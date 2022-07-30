package me.micartey.jairo.parser;

import java.util.Arrays;

public class FieldParser extends Parser {

    public FieldParser(Class<?> target) {
        super(null, target);

        content.append("&0 &1 ").append(target.getName()).append(" ").append("&2").append(" = new ").append(target.getName()).append("(");

        if (Arrays.stream(target.getConstructors()).anyMatch(constructor -> constructor.getParameterCount() == 1))
            content.append("this");

        content.append(");");
    }

    public FieldParser setPrivate(boolean state) {
        content = new StringBuilder(content.toString().replace("&0", state ? "private" : "public"));
        return this;
    }

    public FieldParser setFinal(boolean state) {
        content = new StringBuilder(content.toString().replace("&1", state ? "final" : ""));
        return this;
    }

    public FieldParser setName(String value) {
        content = new StringBuilder(content.toString().replace("&2", value));
        return this;
    }

    @Override
    public String build() {
        content = new StringBuilder(content.toString().replace("&0", "private"));
        return super.build();
    }
}
