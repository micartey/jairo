/*
 * MIT License
 *
 * Copyright (c) 2021 Clientastisch
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.micartey.jairo;

import io.vavr.control.Try;
import javassist.*;
import me.micartey.jairo.annotation.*;
import me.micartey.jairo.parser.FieldParser;
import me.micartey.jairo.parser.MethodParser;

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
    private final ClassPool classPool;

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
        this.classPool = ClassPool.getDefault();
    }

    @Override
    public byte[] transform(java.lang.ClassLoader loader, java.lang.String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classFileBuffer) throws IllegalClassFormatException {
        CtClass ctClass = Try.ofCallable(() -> this.classPool.get(className.replace("/", "."))).get();

        for(Class<?> target : this.observed) {
            if (!match(className.replace("/", "."), target.getAnnotation(Hook.class).value()))
                continue;

            Try.ofCallable(() -> {
                if (!target.isAnnotationPresent(Field.class))
                    return null;

                Field field = target.getAnnotation(Field.class);

                String fieldHeader = new FieldParser(target)
                        .setName(field.value())
                        .setPrivate(field.isPrivate())
                        .setFinal(field.isFinal())
                        .build();

                CtField ctField = CtField.make(fieldHeader, ctClass);
                ctClass.addField(ctField);

                return null;
            }).onFailure(Throwable::printStackTrace);

            Arrays.stream(target.getMethods()).filter(method -> method.isAnnotationPresent(Overwrite.class)).forEach(method -> {
                Optional<Return> returns = Optional.ofNullable(method.getAnnotation(Return.class));
                Optional<Field> field = Optional.ofNullable(target.getAnnotation(Field.class));
                Overwrite overwrite = method.getAnnotation(Overwrite.class);

                Try.run(() -> {
                    MethodParser parser = new MethodParser(method, target)
                            .useField(field.map(Field::value).orElse(null));

                    if (returns.isPresent())
                        parser.allowReturn();

                    CtMethod ctMethod = this.getMethod(ctClass, method);
                    String invoke = parser.build();

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
        }

        return Try.ofCallable(ctClass::toBytecode)
                .onFailure(Throwable::printStackTrace)
                .getOrElse(classFileBuffer);
    }

    /**
     * Finds a {@linkplain CtMethod CtMethod} according to the pattern presented by
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
     * @param ctClass Class to rewrite
     * @param method Method of the observing class
     * @return {@linkplain CtMethod CtMethod} which is suitable for the {@linkplain Method Method}
     * @throws NotFoundException Exception is thrown if no {@linkplain CtMethod CtMethod} is found
     */
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

    /**
     * Finds a {@linkplain CtConstructor CtConstructor} according to the pattern presented by
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
     * @param ctClass Class to rewrite
     * @param constructor Constructor of the observing class
     * @return {@linkplain CtConstructor CtConstructor} which is suitable for the {@linkplain Constructor Constructor}
     * @throws NotFoundException Exception is thrown if no {@linkplain CtConstructor CtConstructor} is found
     */
    private CtConstructor getConstructor(CtClass ctClass, Constructor<?> constructor) throws NotFoundException {
        Parameter parameter = constructor.getAnnotation(Parameter.class);

        if (!constructor.isAnnotationPresent(Parameter.class)) {
            return ctClass.getDeclaredConstructor(Arrays.stream(constructor.getParameterTypes()).map(Class::getName).map(it -> {
                return Try.ofCallable(() -> this.classPool.get(it)).getOrNull();
            }).toArray(CtClass[]::new));
        }

        return ctClass.getDeclaredConstructor(Arrays.stream(parameter.value()).map(Class::getName).map(var -> {
            return Try.ofCallable(() -> this.classPool.get(var)).getOrNull();
        }).toArray(CtClass[]::new));
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
