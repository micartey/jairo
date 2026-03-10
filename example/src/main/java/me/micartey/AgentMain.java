package me.micartey;

import me.micartey.jairo.JairoTransformer;

import java.lang.instrument.Instrumentation;

public class AgentMain {

    public static void premain(String arguments, Instrumentation instrumentation) {
        JairoTransformer transformer = new JairoTransformer(TestTargetInjector.class);
        instrumentation.addTransformer(transformer);
    }

}
