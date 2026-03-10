# Example

1. Build the project
```bash
just build
cd example
gradle clean jar
```

2. Run the following command

```bash
java -javaagent:build/libs/example-1.0-SNAPSHOT.jar -jar HelloWorld.jar
```

Depending on your changes in `TestTargetInjector` you will see _"Injected"_ before, after or only instead of the _"Hello World! 10"_ message