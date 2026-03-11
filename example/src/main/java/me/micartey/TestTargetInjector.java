package me.micartey;

import me.micartey.jairo.annotation.Field;
import me.micartey.jairo.annotation.Hook;
import me.micartey.jairo.annotation.Overwrite;

@Field("myTestInjector")
@Hook("me.micartey.example.TestTarget")
public class TestTargetInjector {

    @Overwrite(Overwrite.Type.REPLACE)
    public void test(Object instance, int a, String b) {
        System.out.println("Injected");
    }

}
