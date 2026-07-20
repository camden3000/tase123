# tase123

Gradle + Java 21 Hello World 프로젝트입니다.

## 요구 사항

- JDK 21 이상 (Gradle Wrapper 포함)

## 실행

```bash
./gradlew run
```

출력:

```text
Hello World!
```

## 테스트

```bash
./gradlew test
```

## 빌드

```bash
./gradlew build
```

## 구조

```text
.
├── app/
│   ├── build.gradle
│   └── src/
│       ├── main/java/com/example/hello/App.java
│       └── test/java/com/example/hello/AppTest.java
├── gradle/
├── gradlew
└── settings.gradle
```
