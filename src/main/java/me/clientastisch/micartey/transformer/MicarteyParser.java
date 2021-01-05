package me.clientastisch.micartey.transformer;

import me.clientastisch.micartey.transformer.annotations.Field;
import me.clientastisch.micartey.transformer.annotations.Overwrite;
import me.clientastisch.micartey.transformer.annotations.Return;

import java.lang.reflect.Executable;
import java.util.Arrays;

public class MicarteyParser<T extends Executable> {

    private final Class<?> target;
    private final T type;

    private final Field field;

    public MicarteyParser(T type, Class<?> target) {
        this.target = target;
        this.type = type;

        this.field = target.getAnnotation(Field.class);
    }

    public String buildInvoke() {
        StringBuilder builder = new StringBuilder("this." + field.value() + "." + type.getName() + "(");

        for (int index = 0; index < type.getParameterCount(); index++) {
            builder.append("$").append(index);

            if (type.getParameterCount() != index + 1)
                builder.append(",");
        }

        if (type.isAnnotationPresent(Return.class))
            builder.insert(0, "return ");

        return builder.append(");").toString();
    }

    public String buildInstance() {
        StringBuilder builder = new StringBuilder("this." + field.value() + " = new " + target.getName() + "(");

        for (int index = 0; index < type.getParameterCount(); index++) {
            builder.append("$").append(index);

            if (type.getParameterCount() != index + 1)
                builder.append(",");
        }

        return builder.append(");").toString();
    }

    public String buildField() {
        StringBuilder builder = new StringBuilder("private final " + target.getName() + " " + field.value());

        if (Arrays.stream(target.getConstructors()).noneMatch(constructor -> constructor.isAnnotationPresent(Overwrite.class))) {
            builder.append(" = new ").append(target.getName()).append("(");

            if (Arrays.stream(target.getConstructors()).anyMatch(var -> var.getParameterCount() == 1))
                builder.append("this");

            builder.append(")");
        }

        return builder.append(";").toString();
    }
}
