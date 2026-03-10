package me.micartey.jairo;

import io.vavr.control.Try;
import me.micartey.jairo.annotation.*;
import me.micartey.jairo.asm.JairoClassVisitor;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.List;

public class JairoTransformer implements ClassFileTransformer {

    private final List<Class<?>> observed;

    public JairoTransformer(Class<?>... arguments) {
        if (!Arrays.stream(arguments).allMatch(target -> target.isAnnotationPresent(Hook.class)))
            throw new IllegalStateException("Some classes are missing the annotation: " + Hook.class.getName());

        this.observed = Arrays.asList(arguments);
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classFileBuffer) throws IllegalClassFormatException {
        String dotClassName = className.replace("/", ".");

        for (Class<?> target : this.observed) {
            if (!match(dotClassName, target.getAnnotation(Hook.class).value()))
                continue;

            try {
                ClassReader reader = new ClassReader(classFileBuffer);
                ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                JairoClassVisitor visitor = new JairoClassVisitor(writer, target);
                reader.accept(visitor, ClassReader.EXPAND_FRAMES);
                classFileBuffer = writer.toByteArray();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return classFileBuffer;
    }

    private boolean match(String match, String pattern) {
        String backup = match;

        for (String string : pattern.split("~")) {
            if (string.length() > 1)
                backup = backup.replace(string, "");
        }

        return String.format(pattern.replace("~", "%s"), (Object[]) backup.split("\\.")).compareTo(match) == 0;
    }

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
