package me.clientastisch.micartey.transformer;

import io.vavr.control.Try;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import me.clientastisch.micartey.transformer.annotations.FieldName;
import me.clientastisch.micartey.transformer.annotations.Hook;
import me.clientastisch.micartey.transformer.annotations.MethodName;
import me.clientastisch.micartey.transformer.annotations.Overwrite;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class MicarteyTransformer implements ClassFileTransformer {

    private final List<Class<?>> observed;

    public MicarteyTransformer(Class<?>... arguments) {
        if (!Arrays.stream(arguments).allMatch(target -> target.isAnnotationPresent(Hook.class)))
            throw new IllegalStateException("Some class[es] are missing the annotation: " + Hook.class.getName());

        if (!Arrays.stream(arguments).allMatch(target -> target.isAnnotationPresent(FieldName.class)))
            throw new IllegalStateException("Some class[es] are missing the annotation: " + FieldName.class.getName());

        this.observed = Arrays.asList(arguments);
    }

    @Override
    public byte[] transform(java.lang.ClassLoader loader, java.lang.String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        AtomicReference<byte[]> reference = new AtomicReference<>(classfileBuffer);

        for(Class<?> target : observed) {
            if (!target.getAnnotation(Hook.class).value().equals(className.replace("/", ".")))
                continue;

            ClassPool pool = ClassPool.getDefault();
            CtClass ctClass = Try.ofCallable(() -> pool.get(target.getAnnotation(Hook.class).value())).getOrNull();

            FieldName field = target.getAnnotation(FieldName.class);

            Arrays.stream(target.getMethods()).filter(method -> method.isAnnotationPresent(Overwrite.class)).forEach(method -> {
                Overwrite overwrite = method.getAnnotation(Overwrite.class);
                MethodName name = method.getAnnotation(MethodName.class);

                Try.run(() -> {
                    CtField ctField = CtField.make("private final " + target.getName() + " " + field.value() + " = new " + target.getName() + "();", ctClass);
                    ctClass.addField(ctField);
                }).onFailure(Throwable::printStackTrace);

                Try.run(() -> {
                    CtMethod ctMethod = ctClass.getDeclaredMethod(name != null ? name.value() : method.getName());

                    StringBuilder builder = new StringBuilder("this." + field.value() + "." + method.getName() + "(");

                    for (int index = 0; index < method.getParameterCount(); index++) {
                        builder.append("$" + index);

                        if (method.getParameterCount() != index + 1)
                            builder.append(",");
                    }

                    builder.append(");");

                    System.out.println(ctMethod.getReturnType());

                    switch (overwrite.value()) {
                        case BEFORE:
                            ctMethod.insertBefore(builder.toString());
                            break;
                        case AFTER:
                            ctMethod.insertAfter(builder.toString());
                            break;
                        case REPLACE:
                            if (!ctMethod.getReturnType().equals(pool.get("javassist.CtPrimitiveType")))
                                ctMethod.setBody("return " + builder.toString());
                            else
                                ctMethod.setBody(builder.toString());
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
