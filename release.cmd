set JAVA_HOME="C:\tools\java\jdk-21.0.1"

set new_version=4.5.0

set jsh_content=^
    Files.writeString(Path.of("README.md"), ^
        Files.readString(Path.of("README.md")).replaceAll(^
            "<brinvex-util-revolut.version>(.*)</brinvex-revolut-ibkr.version>", ^
            "<brinvex-util-revolut.version>%%s</brinvex-revolut-ibkr.version>".formatted(System.getenv("new_version"))), ^
    StandardOpenOption.TRUNCATE_EXISTING);

echo %jsh_content% | %JAVA_HOME%\bin\jshell -

call mvnw clean -Darguments=-DskipTests license:format
call mvnw clean package
call mvnw versions:set -DnewVersion=%new_version%
call mvnw versions:commit
call mvnw clean deploy -DskipTests

REM Commit and push