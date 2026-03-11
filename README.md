# jairo

<div align="center">
  <a href="https://www.oracle.com/java/">
    <img
      src="https://img.shields.io/badge/Written%20in-java-%23EF4041?style=for-the-badge"
      height="30"
    />
  </a>
  <a href="https://jitpack.io/#micartey/jairo/main-SNAPSHOT">
    <img
      src="https://img.shields.io/badge/jitpack-main-%2321f21?style=for-the-badge"
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

jairo is a custom transformer which provides an easy way to change the method body of classes. The transformer will be used through the `Instrumentation` which is provided by Java using the java agent. It heavily depends on the ASM library to change the bytecode at runtime.

### Motivation

Changing code at runtime makes a lot of things easier. E.g. if you are reverse engineering something, want to trigger an event after a class is created or a method is invoked whose implementation you have no control over. Maybe you want to benchmark a method etc... In such cases, it could be pleasant to just rewrite implementations at runtime without having to download, decompile, edit and recompile your dependencies.


## 🔗 Build Tools

To use jairo as a dependency you might want to use a build tool like Maven or Gradle. An easy way for each and every project is to use [jitpack](https://jitpack.io/#micartey/jairo/main-SNAPSHOT) as it makes it easy to implement and use. The following example is Maven specific, as I personally don't use Gradle that much.

### Maven

First of all add a new repository to your `pom.xml` file to be able to download the dependencies provided by jitpack.

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Lastly, after adding the repository to all your other repositories, you have to add the following segment to your dependencies.

```xml
<dependency>
    <groupId>com.github.micartey</groupId>
    <artifactId>jairo</artifactId>
    <version>main-SNAPSHOT</version>
</dependency>
```

## 🎈 Getting started

There are a total of 6 annotations you have to know and use.

<details open>
<summary> Expand for an overview of available annotations </summary>
<br>

| Annotation | Description | Scope | |
|------------|-------------|-------|-|
| @Field     | The `@Field` annotation is used to specify the parameter name at runtime. Make sure to avoid name collisions | Class |
| @Hook | The `@Hook` annotation specifies on which class the injection should be applied | Class |
| @Name | The `@Name` annotation is used in case your method name differs from the method you are trying to inject | Method | optional |
| @Overwrite | The `@Overwrite` annotation specifies which type of injection should be used. You can choose between: <ul><li>Before</li><li>After</li><li>Replace</li></ul> | Method |
| @Parameter | The `@Parameter` annotation is only used in case many methods in a class share the same name. While they can share the same name, they cannot share the same signature. By specifying the method parameters of the target method, the right method can be injected | Method | optional |
| @Return | The `@Return` annotation can only be used with `Overwrite.Type.Replace` and will replace the target method's content with a return statement | Method | optional |

</details>

### Hook to a Class

In order to overwrite methods of a class, jairo needs to know on which classes it should apply (re-)transformations. This is being achieved by the `@Hook` annotation as previously described.

```java
package my.test.path;

public class Target {
    ...
}
```

Let's assume Target is the target class on which we want to perform some transformations. Now create another class which has the `@Hook` annotation:

```java
package some.random.path;

@Field("targetInjectorInstance")
@Hook("my.test.path.Target")
public class TargetInjector {
    ...
}
```

As you can see, the `@Field` annotation was also added. This annotation is very important as it specifies the instance name of the global variable which will be added in order to hold an instance of `TargetInjector` inside `Target`.

The following section illustrates what is happening to the `Target` class at runtime:

```java
package my.test.path;

public class Target {
    private TargetInjector targetInjectorInstance = new TargetInjector()
    ...
}
```

### Overwrite a method

Next up is to overwrite a method. Let's assume that you have a method called `addNumbers` as follows:

```java
public int addNumbers(int a, int b) {
    return a + b;
}
```

And we want to print both numbers out before they are being calculated for some reason.

```java
@Name("addNumber")
@Overwrite(Overwrite.Type.BEFORE)
public void printNumbersBeforeAdding(Object instance, int a, int b) {
    System.out.println(String.format("a: %s b: %s", a, b));
}
```


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
