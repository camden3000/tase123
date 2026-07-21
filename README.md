# tase123

Gradle + Java 21 + Bouncy Castle 샘플 프로젝트입니다.

## 요구 사항

- JDK 21 이상 (Gradle Wrapper 포함)

## 실행

```bash
./gradlew run
```

출력 예시:

```text
Hello World!

=== Bouncy Castle sample ===
provider : BC 1.85
plaintext: Hello Bouncy Castle!
sha256   : ...
encrypted: ...
decrypted: Hello Bouncy Castle!
ok       : true
```

crypt 샘플만 실행:

```bash
./gradlew runCrypt
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
│       ├── main/java/
│       │   ├── com/example/hello/App.java
│       │   └── com/example/crypt/
│       │       ├── BouncyCastleSupport.java
│       │       ├── AesGcmCipher.java
│       │       └── CryptoSample.java
│       └── test/java/
│           ├── com/example/hello/AppTest.java
│           └── com/example/crypt/AesGcmCipherTest.java
├── gradle/
├── gradlew
└── settings.gradle
```

## crypt 패키지

- `BouncyCastleSupport` — BC provider 등록
- `AesGcmCipher` — AES-256-GCM 암·복호화
- `CryptoSample` — SHA-256 + AES-GCM 라운드트립 데모

의존성: `org.bouncycastle:bcprov-jdk18on:1.85`
