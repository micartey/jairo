package me.micartey.jairo.parser;

import java.lang.reflect.Executable;
import java.util.Arrays;

public class MethodParser extends Parser {

    public MethodParser(Executable executable, Class<?> target) {
        super(executable, target);

        content.append("&0&1").append(executable.getName()).append("(");

        for (int index = 0; index < executable.getParameterCount(); index++) {
            content.append("$").append(index);

            if (executable.getParameterCount() != index + 1)
                content.append(",");
        }

        content.append(");");
    }

    public MethodParser useField(String fieldName) {
        if (fieldName != null) {
            content = new StringBuilder(content.toString().replace("&1", "this." + fieldName + "."));
            return this;
        }

        StringBuilder builder = new StringBuilder("new " + target.getName() + "(");

        if (Arrays.stream(target.getConstructors()).anyMatch(constructor -> constructor.getParameterCount() == 1))
            builder.append("this");

        this.content = new StringBuilder(content.toString().replace("&1", builder.append(").")));
        return this;
    }

    public MethodParser allowReturn() {
        content = new StringBuilder(content.toString().replace("&0", "return "));
        return this;
    }

    @Override
    public String build() {
        this.useField(null);
        return super.build();
    }
}
