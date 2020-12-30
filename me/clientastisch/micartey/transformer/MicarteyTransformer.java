package me.clientastisch.micartey.transformer;

import io.vavr.control.Try;
import javassist.*;
import me.clientastisch.micartey.transformer.annotations.Hook;
import me.clientastisch.micartey.transformer.annotations.Overwrite;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.WrongMethodTypeException;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class MicarteyTransformer implements ClassFileTransformer {

    private final List<Class<? extends Micartey>> observed;

    public MicarteyTransformer(Class<? extends Micartey>... arguments) {
        if (!Arrays.stream(arguments).allMatch(target -> target.isAnnotationPresent(Hook.class)))
            throw new IllegalStateException("Some class[es] are missing the annotation: " + Hook.class.getName());

        this.observed = Arrays.asList(arguments);
    }

    @Override
    public byte[] transform(java.lang.ClassLoader loader, java.lang.String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        AtomicReference<byte[]> reference = new AtomicReference<>(classfileBuffer);

        for(Class<? extends Micartey> target : observed) {
            if (!target.getAnnotation(Hook.class).value().equals(className.replace("/", ".")))
                continue;

            ClassPool pool = ClassPool.getDefault();
            CtClass ctClass = Try.ofCallable(() -> pool.get(target.getAnnotation(Hook.class).value())).getOrNull();

            Micartey instance = Try.ofCallable(() -> target.getConstructor().newInstance()).getOrNull();

            Arrays.stream(target.getMethods()).filter(method -> method.isAnnotationPresent(Overwrite.class)).forEach(method -> {
                Overwrite overwrite = method.getAnnotation(Overwrite.class);

                Try.run(() -> {
                    CtField field = CtField.make("private final " + target.getName() + " " + instance.getFieldName() + " = new " + target.getName() + "();", ctClass);
                    ctClass.addField(field);
                }).onFailure(Throwable::printStackTrace);

                Try.run(() -> {
                    CtMethod ctMethod = ctClass.getDeclaredMethod(method.getName());

                    StringBuilder builder = new StringBuilder("this." + instance.getFieldName() + "." + method.getName() + "(");

                    for (int index = 0; index < method.getParameterCount(); index++) {
                        builder.append("$" + index);

                        if (method.getParameterCount() != index + 1)
                            builder.append(",");
                    }

                    builder.append(");");

                    switch (overwrite.value()) {
                        case BEFORE:
                            ctMethod.insertBefore(builder.toString());
                            break;
                        case AFTER:
                            ctMethod.insertAfter(builder.toString());
                            break;
                        case REPLACE:
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
