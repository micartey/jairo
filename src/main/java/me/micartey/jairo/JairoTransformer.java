package me.micartey.jairo;

import io.vavr.control.Try;
import me.micartey.jairo.annotation.*;
import me.micartey.jairo.parser.FieldParser;
import me.micartey.jairo.parser.MethodParser;
import org.objectweb.asm.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class JairoTransformer implements ClassFileTransformer {

    private final List<Class<?>> observed;

    /**
     * Add a list of {@linkplain Class observers} to define rules for
     * transforming classes.
     *
     * Observers need following annotations:
     * <ul>
     *     <li>
     *          {@linkplain Field Field} annotation
     *     </li>
     *     <li>
     *          {@linkplain Hook Hook} annotation
     *     </li>
     * </ul>
     *
     * @param arguments List of observers
     */
    public JairoTransformer(Class<?>... arguments) {
        if (!Arrays.stream(arguments).allMatch(target -> target.isAnnotationPresent(Hook.class)))
            throw new IllegalStateException("Some classes are missing the annotation: " + Hook.class.getName());

        this.observed = Arrays.asList(arguments);
    }

    @Override
    public byte[] transform(java.lang.ClassLoader loader, java.lang.String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classFileBuffer) throws IllegalClassFormatException {
        ClassReader classReader = new ClassReader(classFileBuffer);
        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, classWriter) {
            @Override
            public void visitEnd() {
                for (Class<?> target : observed) {
                    if (!match(className.replace("/", "."), target.getAnnotation(Hook.class).value()))
                        continue;

                    if (target.isAnnotationPresent(Field.class)) {
                        Field field = target.getAnnotation(Field.class);
                        String fieldHeader = new FieldParser(target)
                                .setName(field.value())
                                .setPrivate(field.isPrivate())
                                .setFinal(field.isFinal())
                                .build();

                        FieldVisitor fieldVisitor = cv.visitField(
                                field.isPrivate() ? Opcodes.ACC_PRIVATE : Opcodes.ACC_PUBLIC,
                                field.value(),
                                Type.getDescriptor(target),
                                null,
                                null
                        );
                        fieldVisitor.visitEnd();
                    }

                    Arrays.stream(target.getMethods()).filter(method -> method.isAnnotationPresent(Overwrite.class)).forEach(method -> {
                        Optional<Return> returns = Optional.ofNullable(method.getAnnotation(Return.class));
                        Optional<Field> field = Optional.ofNullable(target.getAnnotation(Field.class));
                        Overwrite overwrite = method.getAnnotation(Overwrite.class);

                        MethodVisitor methodVisitor = cv.visitMethod(
                                Opcodes.ACC_PUBLIC,
                                method.getName(),
                                Type.getMethodDescriptor(method),
                                null,
                                null
                        );

                        methodVisitor.visitCode();
                        MethodParser parser = new MethodParser(method, target)
                                .useField(field.map(Field::value).orElse(null));

                        if (returns.isPresent())
                            parser.allowReturn();

                        String invoke = parser.build();

                        switch (overwrite.value()) {
                            case BEFORE:
                                methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, className, method.getName(), invoke, false);
                                break;
                            case AFTER:
                                methodVisitor.visitInsn(Opcodes.RETURN);
                                methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, className, method.getName(), invoke, false);
                                break;
                            case REPLACE:
                                methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, className, method.getName(), invoke, false);
                                break;
                        }

                        methodVisitor.visitMaxs(0, 0);
                        methodVisitor.visitEnd();
                    });
                }
                super.visitEnd();
            }
        };

        classReader.accept(classVisitor, 0);
        return classWriter.toByteArray();
    }

    /**
     * Finds a {@linkplain Method Method} according to the pattern presented by
     * one of the following:
     *
     * <ul>
     *     <li>
     *         Annotation {@linkplain Parameter @Parameter}
     *     </li>
     *     <li>
     *          Method name
     *     </li>
     * </ul>
     *
     * @param className Class to rewrite
     * @param method Method of the observing class
     * @return {@linkplain Method Method} which is suitable for the {@linkplain Method Method}
     * @throws NoSuchMethodException Exception is thrown if no {@linkplain Method Method} is found
     */
    private Method getMethod(String className, Method method) throws NoSuchMethodException, ClassNotFoundException {
        Parameter parameter = method.getAnnotation(Parameter.class);
        Name name = method.getAnnotation(Name.class);

        String methodName = name != null ? name.value() : method.getName();

        if (!method.isAnnotationPresent(Parameter.class))
            return Class.forName(className).getDeclaredMethod(methodName);

        return Class.forName(className).getDeclaredMethod(methodName, parameter.value());
    }

    /**
     * Finds a {@linkplain Constructor Constructor} according to the pattern presented by
     * one of the following:
     *
     * <ul>
     *     <li>
     *         Annotations like {@linkplain Parameter @Parameter}
     *     </li>
     *     <li>
     *          Method {@link java.lang.reflect.Constructor#getTypeParameters signature}
     *     </li>
     * </ul>
     *
     * @param className Class to rewrite
     * @param constructor Constructor of the observing class
     * @return {@linkplain Constructor Constructor} which is suitable for the {@linkplain Constructor Constructor}
     * @throws NoSuchMethodException Exception is thrown if no {@linkplain Constructor Constructor} is found
     */
    private Constructor<?> getConstructor(String className, Constructor<?> constructor) throws NoSuchMethodException, ClassNotFoundException {
        Parameter parameter = constructor.getAnnotation(Parameter.class);

        if (!constructor.isAnnotationPresent(Parameter.class)) {
            return Class.forName(className).getDeclaredConstructor(constructor.getParameterTypes());
        }

        return Class.forName(className).getDeclaredConstructor(parameter.value());
    }

    /**
     * Makes sure that both {@linkplain String parameters} matches according to
     * their pattern
     *
     * @param match classname of the class which will be transformed
     * @param pattern pattern to match the classname
     * @return Whether pattern matches classname
     */
    private boolean match(String match, String pattern) {
        String backup = match;

        for(String string : pattern.split("~")) {
            if (string.length() > 1)
                backup = backup.replace(string, "");
        }

        return String.format(pattern.replace("~", "%s"), backup.split("\\.")).compareTo(match) == 0;
    }

    /**
     * Add {@linkplain JairoTransformer MicarteyTransformer} as a new
     * {@linkplain ClassFileTransformer ClassTransformer} and retransforms already loaded
     * classes.
     *
     * @param instrumentation Instrumentation of Java-agent
     */
    public void retransform(Instrumentation instrumentation) {
        instrumentation.addTransformer(this, true);

        for (Class<?> target : instrumentation.getAllLoadedClasses()) {
            observed.stream().filter(observe -> match(target.getName(), observe.getAnnotation(Hook.class).value())).forEach(value -> {
                Try.run(() -> {
                    instrumentation.retransformClasses(target);
                }).onFailure(Throwable::printStackTrace);
            });
        }
    }
}
