package me.clientastisch.micartey.agent;

import me.clientastisch.micartey.inject.Test;
import me.clientastisch.micartey.transformer.MicarteyTransformer;

import java.lang.instrument.Instrumentation;

public class Agent {

    public static void premain(String arguments, Instrumentation instrumentation) {
        MicarteyTransformer transformer = new MicarteyTransformer(Test.class);
        instrumentation.addTransformer(transformer);
    }

}
