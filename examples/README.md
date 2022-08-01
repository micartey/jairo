Let's assume that we have a class called `InjectMe` looking as follows:

```java
public class InjectMe {

    private final String tag;

    public InjectMe(String tag) {
        this.tag = tag;
    }

    public void myMethod(String value) {
        this.tag += value;
    }
}
```

and another class called `Daddy`:

```java
import me.micartey.jairo.annotation.*;

@Field("myTestInstance")
@Hook("my.path.to.InjectMe")
public class Daddy {

    @Name("myMethod")
    @Parameter({String.class})
    @Overwrite(Overwrite.Type.BEFORE)
    public void method() {
        System.out.println("Hello World!");
    }

}
```

`Daddy` will rewrite the bytecode of the class `InjectMe` in the [`Heap`](https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-2.html). <br> The rewritten class will look as follows:

```java
public class InjectMe {

    private final Daddy myTestInstance = new Daddy();
    private final String tag;

    public InjectMe(String tag) {
        this.tag = tag;
    }

    public void myMethod(String value) {
        this.myTestInstance.method();
        this.tag += value;
    }
}
```

As you can see, a `private` `final` field has been created with a name which can be specified using the `@Field` annotation above the `Daddy` class. This field will be initialized by creating a new instance of `Daddy`. It will be used to invoke the methods to injected.

<br>

Not all annotations must be used. `@Name` is only necessary if the method names are different from each other and `@Parameter` is only necessary if they're multiple methods with the same name but different parameters.

```java
@Overwrite(Overwrite.Type.BEFORE)
public void myMethod(Object instance, String value) {
    System.out.println(instance);
}
```

Another important aspect are the parameters themselves. It's not necessary that the method has any parameters, but if it has, the first will be the object instance of `InjectMe` and every following is a parameter of the method which will be overwritten.