# Micartey

- [Micartey](#micartey)
  - [What is `Micartey`](#what-is-micartey)
  - [How to use `Micartey`](#how-to-use-micartey)
    - [Command line](#command-line)
    - [Injection](#injection)


## What is `Micartey`

`Micartey` is a custom transformer which provides an easy way to change the method body of classes. The transformer will be used through the default `Instrumentation` which is provided by plain java using the java agent.

## How to use `Micartey`

```java
@Field("myTestInstance")
@Hook("my.path.to.Class")
public class Test {

    @Name("myMethod")
    @Parameter({String.class})
    @Overwrite(Overwrite.Type.AFTER)
    public void method(Object instance, String parameter1) {
        System.out.println("Hello World");
    }

    @Overwrite(Overwrite.Type.BEFORE)
    public void anotherMethod(Object instance, String parameter1) {
        System.out.println("Hello World!");
    }

}
```

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
    MicarteyTransformer transformer = new MicarteyTransformer(Test.class);
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
    MicarteyTransformer transformer = new MicarteyTransformer(Test.class);
    transformer.retransform(instrumentation);
}
```