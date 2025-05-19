### コンパイル
```shell
# javac -cp .:sqlite-jdbc-3.30.1.jar ApiServer.java
javac -cp .:lib\sqlite-jdbc-3.30.1.jar -d bin src\ApiServer.java
```
### 実行
```shell
# java -cp ".;sqlite-jdbc-3.30.1.jar" ApiServer
java -cp "bin;lib\sqlite-jdbc-3.30.1.jar" ApiServer
```