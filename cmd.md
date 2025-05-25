# コンパイル

```shell
# javac -cp .:sqlite-jdbc-3.30.1.jar ApiServer.java
```

### windows

```shell
javac -cp .:lib\sqlite-jdbc-3.30.1.jar -d bin src\ApiServer.java
```

### linux

```shell
javac -cp .:lib/sqlite-jdbc-3.30.1.jar -d bin src/ApiServer.java
```

### mac(arm, m2)

```shell
javac -cp lib/sqlite-jdbc.jar src/ApiServer.java
```

# 実行

```shell
# java -cp ".;sqlite-jdbc-3.30.1.jar" ApiServer
```

### windows

```shell
java -cp "bin;lib\sqlite-jdbc-3.30.1.jar" ApiServer
```

### linux

```shell
java -cp bin:lib/sqlite-jdbc-3.30.1.jar ApiServer
```

### mac(arm, m2)

```shell
java -cp lib/sqlite-jdbc.jar:src ApiServer
```
