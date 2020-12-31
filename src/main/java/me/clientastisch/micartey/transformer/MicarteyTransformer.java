package me.clientastisch.micartey.transformer;

import io.vavr.control.Try;
import javassist.*;
import me.clientastisch.micartey.transformer.annotations.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class MicarteyTransformer implements ClassFileTransformer {

    private final List<Class<?>> observed;
    private final ClassPool classPool;

    public MicarteyTransformer(Class<?>... arguments) {
        if (!Arrays.stream(arguments).allMatch(target -> target.isAnnotationPresent(Hook.class)))
            throw new IllegalStateException("Some class[es] are missing the annotation: " + Hook.class.getName());

        if (!Arrays.stream(arguments).allMatch(target -> target.isAnnotationPresent(Field.class)))
            throw new IllegalStateException("Some class[es] are missing the annotation: " + Field.class.getName());

        this.observed = Arrays.asList(arguments);
        this.classPool = ClassPool.getDefault();
    }

    @Override
    public byte[] transform(java.lang.ClassLoader loader, java.lang.String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        AtomicReference<byte[]> reference = new AtomicReference<>(classfileBuffer);

        for(Class<?> target : this.observed) {
            if (!target.getAnnotation(Hook.class).value().equals(className.replace("/", ".")))
                continue;

            CtClass ctClass = Try.ofCallable(() -> this.classPool.get(target.getAnnotation(Hook.class).value())).getOrNull();

            Arrays.stream(target.getMethods()).filter(method -> method.isAnnotationPresent(Overwrite.class)).forEach(method -> {
                Overwrite overwrite = method.getAnnotation(Overwrite.class);
                Field field = target.getAnnotation(Field.class);

                Try.run(() -> {
                    CtField ctField = CtField.make(this.buildFiled(field, target), ctClass);
                    ctClass.addField(ctField);

                    CtMethod ctMethod = this.getMethod(ctClass, method);
                    String methodCall = this.buildInvoke(field, method);

                    switch (overwrite.value()) {
                        case BEFORE:
                            ctMethod.insertBefore(methodCall);
                            break;
                        case AFTER:
                            ctMethod.insertAfter(methodCall);
                            break;
                        case REPLACE:
                            ctMethod.setBody(methodCall);
                            break;
                    }

                }).onFailure(Throwable::printStackTrace);
            });

            Try.ofCallable(ctClass::toBytecode)
                    .onFailure(Throwable::printStackTrace)
                    .onSuccess(reference::set);
        }

        return reference.get();
    }

    private CtMethod getMethod(CtClass ctClass, Method method) throws NotFoundException {
        Parameter parameter = method.getAnnotation(Parameter.class);
        Name name = method.getAnnotation(Name.class);

        String methodName = name != null ? name.value() : method.getName();

        if (!method.isAnnotationPresent(Parameter.class))
            return ctClass.getDeclaredMethod(methodName);

        return ctClass.getDeclaredMethod(methodName, Arrays.stream(parameter.value()).map(Class::getName).map(var -> {
            return Try.ofCallable(() -> this.classPool.get(var)).getOrNull();
        }).toArray(CtClass[]::new));
    }

    private String buildInvoke(Field field, Method method) {
        StringBuilder builder = new StringBuilder("this." + field.value() + "." + method.getName() + "(");

        for (int index = 0; index < method.getParameterCount(); index++) {
            builder.append("$").append(index);

            if (method.getParameterCount() != index + 1)
                builder.append(",");
        }

        if (method.isAnnotationPresent(Return.class))
            builder.insert(0, "return ");

        return builder.append(");").toString();
    }

    private String buildFiled(Field field, Class<?> target) {
        return String.format(
                "private final %s %s = new %s(%s))",
                target.getName(), field.value(), target.getName(),
                Arrays.stream(target.getConstructors()).anyMatch(var -> var.getParameterCount() == 1) ? "$1" : ""
        );
    }

    public void retransform(Instrumentation instrumentation) {
        instrumentation.addTransformer(this, true);

        for (Class<?> target : instrumentation.getAllLoadedClasses()) {
            observed.stream().filter(observe -> observe.getAnnotation(Hook.class).value().equals(target.getName())).forEach(value -> {
                Try.run(() -> {
                    instrumentation.retransformClasses(target);
                }).onFailure(Throwable::printStackTrace);
            });
        }
    }
}
