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
