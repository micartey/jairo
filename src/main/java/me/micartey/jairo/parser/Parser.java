package me.micartey.jairo.parser;

import java.lang.reflect.Executable;

public abstract class Parser {

    protected StringBuilder content = new StringBuilder();

    protected final Executable executable;
    protected final Class<?> target;

    public Parser(Executable executable, Class<?> target) {
        this.executable = executable;
        this.target = target;
    }

    public String build() {
        return content.toString()
                .replaceAll("&.", "")
                .replaceAll(" +", " ");
    };
}
