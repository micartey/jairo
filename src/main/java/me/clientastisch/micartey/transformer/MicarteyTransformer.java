package me.clientastisch.micartey.transformer;

import io.vavr.control.Try;
import javassist.*;
import lombok.SneakyThrows;
import me.clientastisch.micartey.transformer.annotations.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
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
            if (!match(className.replace("/", "."), target.getAnnotation(Hook.class).value()))
                continue;

            CtClass ctClass = Try.ofCallable(() -> this.classPool.get(className.replace("/", "."))).getOrNull();

            Try.run(() -> {
                MicarteyParser<?> parser = new MicarteyParser<>(null, target);

                CtField ctField = CtField.make(parser.buildField(), ctClass);
                ctClass.addField(ctField);
            }).onFailure(Throwable::printStackTrace);

            Arrays.stream(target.getConstructors()).filter(constructor -> constructor.isAnnotationPresent(Overwrite.class)).forEach(constructor -> {
                Overwrite overwrite = constructor.getAnnotation(Overwrite.class);

                Try.run(() -> {
                    MicarteyParser<Constructor<?>> parser = new MicarteyParser<>(constructor, target);

                    CtConstructor ctConstructor = this.getConstructor(ctClass, constructor);
                    String instance = parser.buildInstance();

                    switch (overwrite.value()) {
                        case BEFORE:
                            ctConstructor.insertBefore(instance);
                            break;
                        case AFTER:
                            ctConstructor.insertAfter(instance);
                            break;
                        case REPLACE:
                            ctConstructor.setBody(instance);
                            break;
                    }

                }).onFailure(Throwable::printStackTrace);
            });

            Arrays.stream(target.getMethods()).filter(method -> method.isAnnotationPresent(Overwrite.class)).forEach(method -> {
                Overwrite overwrite = method.getAnnotation(Overwrite.class);

                Try.run(() -> {
                    MicarteyParser<Method> parser = new MicarteyParser<>(method, target);

                    CtMethod ctMethod = this.getMethod(ctClass, method);
                    String invoke = parser.buildInvoke();

                    switch (overwrite.value()) {
                        case BEFORE:
                            ctMethod.insertBefore(invoke);
                            break;
                        case AFTER:
                            ctMethod.insertAfter(invoke);
                            break;
                        case REPLACE:
                            ctMethod.setBody(invoke);
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

    private CtConstructor getConstructor(CtClass ctClass, Constructor<?> constructor) throws NotFoundException {
        Parameter parameter = constructor.getAnnotation(Parameter.class);

        return ctClass.getDeclaredConstructor(Arrays.stream(parameter.value()).map(Class::getName).map(var -> {
            return Try.ofCallable(() -> this.classPool.get(var)).getOrNull();
        }).toArray(CtClass[]::new));
    }

    private boolean match(String pattern, String parameter) {
        if (parameter.split("~").length != 2)
            return pattern.compareTo(parameter) == 0;

        String part = parameter.split("~")[0];
        String part2 = parameter.split("~")[1];

        return pattern.substring(0, part.length()).compareTo(part) + pattern.substring(pattern.length() - part2.length()).compareTo(part2) == 0;
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
