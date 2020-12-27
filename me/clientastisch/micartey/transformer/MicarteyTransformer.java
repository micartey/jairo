package me.clientastisch.micartey.transformer;

import io.vavr.control.Try;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.bytecode.Descriptor;
import me.clientastisch.micartey.transformer.annotations.Hook;
import me.clientastisch.micartey.transformer.annotations.Overwrite;

import java.io.InputStream;
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
        for(Class<?> clazz : arguments) {
            if (!clazz.isAnnotationPresent(Hook.class))
                throw new IllegalStateException(clazz.getName() + " is missing annotation present of: " + Hook.class.getName());
        }

        this.observed = Arrays.asList(arguments);
    }

    @Override
    public byte[] transform(java.lang.ClassLoader loader, java.lang.String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        AtomicReference<byte[]> reference = new AtomicReference<>(classfileBuffer);

        for(Class<?> target : observed) {
            if (!target.getAnnotation(Hook.class).value().equals(className.replace("/", ".")))
                continue;

            Try.ofCallable(() -> {
                ClassPool pool = ClassPool.getDefault();
                CtClass ctClass = pool.get(target.getAnnotation(Hook.class).value());

                Arrays.stream(target.getMethods()).filter(method -> method.isAnnotationPresent(Overwrite.class)).forEach(method -> {
                    Overwrite overwrite = method.getAnnotation(Overwrite.class);
                    Try.run(() -> {
                        CtMethod ctMethod = ctClass.getDeclaredMethod(method.getName());

                        StringBuilder builder = new StringBuilder();
                        Arrays.stream(overwrite.body()).forEach(builder::append);

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

                return ctClass.toBytecode();
            }).onFailure(Throwable::printStackTrace)
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
