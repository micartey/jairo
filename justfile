default: build

mvn := "nix run nixpkgs#maven --"

build:
    {{mvn}} package -DskipTests

compile:
    {{mvn}} compile

clean:
    {{mvn}} clean

test:
    {{mvn}} test

install:
    {{mvn}} install -DskipTests
