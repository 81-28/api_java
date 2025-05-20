### コンパイル

```shell
# javac -cp .:sqlite-jdbc-3.30.1.jar ApiServer.java
javac -cp .:lib\sqlite-jdbc-3.30.1.jar -d bin src\ApiServer.java
```

[　 ⚠️ 注意 ⚠️ 　]
mac では以下のようにしないといけない(`;`→`:`)

```shell
javac -cp .:lib/sqlite-jdbc-3.30.1.jar -d bin src/ApiServer.java

javac -cp .:lib/sqlite-jdbc-3.30.1.jar --add-modules jdk.httpserver -d bin src/ApiServer.java

# macはこれで安定
javac -cp lib/sqlite-jdbc.jar src/ApiServer.java
```

### 実行

```shell
# java -cp ".;sqlite-jdbc-3.30.1.jar" ApiServer
java -cp "bin;lib\sqlite-jdbc-3.30.1.jar" ApiServer
```

[　 ⚠️ 注意 ⚠️ 　]
mac では以下のようにしないといけない(`;`→`:`)

```shell
java -cp "bin:lib/sqlite-jdbc-3.30.1.jar" ApiServer

java -cp "bin:lib/sqlite-jdbc-3.30.1.jar" --add-modules jdk.httpserver ApiServer

# macで安定するバージョン
java -cp lib/sqlite-jdbc.jar:src ApiServer
```
