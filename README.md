# Micartey

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

```java
@Hook("my.path.to.Class")
public interface Test {

    @Overwrite(
            value = Overwrite.Type.AFTER,
            body = "System.out.println(\"test\");"
    )
    void $method();

}
```