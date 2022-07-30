# jairo

<div align="center">
  <a href="https://www.oracle.com/java/">
    <img
      src="https://img.shields.io/badge/Written%20in-java-%23EF4041?style=for-the-badge"
      height="30"
    />
  </a>
  <a href="https://jitpack.io/#Clientastisch/jairo/main-SNAPSHOT">
    <img
      src="https://img.shields.io/badge/jitpack-main-%2321f21?style=for-the-badge"
      height="30"
    />
  </a>
  <a href="https://clientastisch.github.io/jairo/docs" target="_blank">
    <img
      src="https://img.shields.io/badge/javadoc-reference-5272B4.svg?style=for-the-badge"
      height="30"
    />
  </a>
</div>

<br>

<p align="center">
  <a href="#-introduction">Introduction</a> |
  <a href="#-build-tools">Maven/Gradle</a> |
  <a href="#-getting-started">Getting started</a>
</p>

## 📚 Introduction

jairo is a custom transformer which provides an easy way to change the method body of classes. The transformer will be used through the `Instrumentation` which is provided by java using the java agent. It heavily depends on the [javaassist](https://github.com/jboss-javassist/javassist) library to change the bytecode at runtime.

## 🔗 Build Tools

To use jairo as a dependency you might want to use a build tool like maven or gradle. An easy way for each and every project, is to use [jitpack](https://jitpack.io/#Clientastisch/jairo/main-SNAPSHOT) as it makes it easy to implement and use.

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

...

<dependency>
    <groupId>com.github.Clientastisch</groupId>
    <artifactId>jairo</artifactId>
    <version>Tag</version>
</dependency>
```

## 🎈 Getting started

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

## 💉 Injecting into a JVM

There are different ways to inject into a JVM. The best option is to specify a javaagent with the start-up command. This will ensure that the transformations will be applied, since classes don't have to be retransformed.

### Command line

```text
java -javaagent:Agent.jar -jar MyApplication.jar
```

```text
Manifest-Version: 1.0
Premain-Class: my.path.to.Agent
```

```java
public static void premain(String arguments, Instrumentation instrumentation) {
    JairoTransformer transformer = new JairoTransformer(Daddy.class);
    instrumentation.addTransformer(transformer);
}
```

### Injection

```text
Manifest-Version: 1.0
Agent-Class: my.path.to.Agent
Can-Redefine-Classes: true
Can-Retransform-Classes: true
```

```java
public static void agentmain(String args, Instrumentation instrumentation) {
    JairoTransformer transformer = new JairoTransformer(Daddy.class);
    transformer.retransform(instrumentation);
}
```