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

package me.clientastisch.micartey.transformer;

import me.clientastisch.micartey.transformer.annotations.Field;
import me.clientastisch.micartey.transformer.annotations.Overwrite;
import me.clientastisch.micartey.transformer.annotations.Return;

import java.lang.reflect.Executable;
import java.util.Arrays;

final class MicarteyParser<T extends Executable> {

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
        StringBuilder builder = new StringBuilder("private " + (field.isFinal() ? "final " : "") + target.getName() + " " + field.value());

        if (Arrays.stream(target.getConstructors()).noneMatch(constructor -> constructor.isAnnotationPresent(Overwrite.class))) {
            builder.append(" = new ").append(target.getName()).append("(");

            if (Arrays.stream(target.getConstructors()).anyMatch(var -> var.getParameterCount() == 1))
                builder.append("this");

            builder.append(")");
        }

        return builder.append(";").toString();
    }
}
