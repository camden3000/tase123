# OpenDJ LDAP 실습 — 초급

목표: OpenDJ를 설치·기동하고, 기본 LDAP CRUD와 검색 필터를 익힌다.

---

## 1. 사전 준비

### 1.1 Java 확인

```bash
java -version
```

결과 예시:

```text
openjdk version "11.0.22" 2024-01-16
OpenJDK Runtime Environment (build 11.0.22+7-post-Ubuntu-0ubuntu222.04.1)
OpenJDK 64-Bit Server VM (build 11.0.22+7-post-Ubuntu-0ubuntu222.04.1, mixed mode, sharing)
```

### 1.2 OpenDJ 바이너리 확인

```bash
ls /opt/opendj/bin | head
```

결과 예시:

```text
backup
base64
create-rc-script
dsconfig
dsreplication
encode-password
export-ldif
import-ldif
ldapcompare
ldapdelete
```

---

## 2. OpenDJ 설치(대화형 요약)

이미 설치된 환경이면 이 절은 건너뛰어도 됩니다.

```bash
cd /opt/opendj
./setup --cli
```

주요 입력 예시:

```text
Root User DN [cn=Directory Manager]: cn=Directory Manager
Password: Secret123
Confirm Password: Secret123
Fully Qualified Host Name [localhost]: localhost
LDAP Port [1389]: 1389
Administration Connector Port [4444]: 4444
Create Base DN: yes
Base DN: dc=example,dc=com
Directory Data: Only create base entry
Start Server: yes
```

설치 완료 메시지 예시:

```text
Configuring Directory Server ..... Done.
Creating Base Entry dc=example,dc=com ..... Done.
Starting Directory Server ...... Done.
...
To see basic server configuration status and configuration, you can launch
/opt/opendj/bin/status
```

---

## 3. 서버 기동 / 중지 / 상태

### 3.1 기동

```bash
/opt/opendj/bin/start-ds
```

결과 예시:

```text
[18/Aug/2026:23:40:01 +0000] category=BACKEND severity=NOTICE msgID=9896306 msg=The backend dc=example,dc=com containing 1 entries has started
...
The Directory Server has started successfully
```

### 3.2 상태

```bash
/opt/opendj/bin/status --bindDN "cn=Directory Manager" --bindPassword Secret123
```

결과 예시(요약):

```text
Server Run Status:        Started
Connection Handlers:      [LDAP : 1389]
Base DN:                  dc=example,dc=com
Entries:                  1
```

### 3.3 중지

```bash
/opt/opendj/bin/stop-ds
```

결과 예시:

```text
Stopping Directory Server ..... Done.
```

> 이후 실습은 서버가 기동된 상태를 가정합니다. 중지했다면 `start-ds`로 다시 기동하세요.

---

## 4. 기본 검색 (ldapsearch)

### 4.1 Base DN 확인

```bash
ldapsearch -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -b "dc=example,dc=com" -s base "(objectClass=*)"
```

결과 예시:

```text
dn: dc=example,dc=com
objectClass: top
objectClass: domain
dc: example
```

### 4.2 전체 하위 트리 검색

```bash
ldapsearch -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -b "dc=example,dc=com" -s sub "(objectClass=*)" dn
```

결과 예시(초기):

```text
dn: dc=example,dc=com
```

### 4.3 익명 바인드 시도(기본은 제한됨)

```bash
ldapsearch -h localhost -p 1389 -b "dc=example,dc=com" -s base "(objectClass=*)"
```

결과 예시(권한/정책에 따라 다를 수 있음):

```text
# 성공하는 경우
dn: dc=example,dc=com
objectClass: top
objectClass: domain
dc: example

# 또는 권한 부족
ldap_search: Insufficient access
```

---

## 5. OU / 사용자 엔트리 추가 (ldapadd)

### 5.1 조직 단위(OU) 생성

`/tmp/opendj-lab/01-ou.ldif` 작성:

```ldif
dn: ou=People,dc=example,dc=com
objectClass: top
objectClass: organizationalUnit
ou: People
description: Employee accounts

dn: ou=Groups,dc=example,dc=com
objectClass: top
objectClass: organizationalUnit
ou: Groups
description: Group containers
```

추가:

```bash
ldapadd -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -f /tmp/opendj-lab/01-ou.ldif
```

결과 예시:

```text
adding new entry "ou=People,dc=example,dc=com"
adding new entry "ou=Groups,dc=example,dc=com"
```

### 5.2 사용자 추가

`/tmp/opendj-lab/02-users.ldif` 작성:

```ldif
dn: uid=hong.gildong,ou=People,dc=example,dc=com
objectClass: top
objectClass: person
objectClass: organizationalPerson
objectClass: inetOrgPerson
cn: 홍 길동
sn: 홍
givenName: 길동
uid: hong.gildong
mail: hong.gildong@example.com
userPassword: Passw0rd!
telephoneNumber: +82 10 1234 5678

dn: uid=kim.chulsoo,ou=People,dc=example,dc=com
objectClass: top
objectClass: person
objectClass: organizationalPerson
objectClass: inetOrgPerson
cn: 김 철수
sn: 김
givenName: 철수
uid: kim.chulsoo
mail: kim.chulsoo@example.com
userPassword: Passw0rd!
```

추가:

```bash
ldapadd -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -f /tmp/opendj-lab/02-users.ldif
```

결과 예시:

```text
adding new entry "uid=hong.gildong,ou=People,dc=example,dc=com"
adding new entry "uid=kim.chulsoo,ou=People,dc=example,dc=com"
```

### 5.3 중복 추가 시 오류 확인

같은 파일을 다시 실행:

```bash
ldapadd -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -f /tmp/opendj-lab/02-users.ldif
```

결과 예시:

```text
adding new entry "uid=hong.gildong,ou=People,dc=example,dc=com"
ldap_add: Already exists (68)
        additional info: Entry uid=hong.gildong,ou=People,dc=example,dc=com cannot be added because an entry with that name already exists
```

---

## 6. 검색 필터 연습

### 6.1 UID로 검색

```bash
ldapsearch -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -b "ou=People,dc=example,dc=com" "(uid=hong.gildong)"
```

결과 예시:

```text
dn: uid=hong.gildong,ou=People,dc=example,dc=com
objectClass: top
objectClass: person
objectClass: organizationalPerson
objectClass: inetOrgPerson
cn: 홍 길동
sn: 홍
givenName: 길동
uid: hong.gildong
mail: hong.gildong@example.com
telephoneNumber: +82 10 1234 5678
```

> `userPassword`는 보통 관리자라도 해시 형태로 보이거나, 표시 정책에 따라 숨겨질 수 있습니다.

### 6.2 속성 일부만 출력

```bash
ldapsearch -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -b "ou=People,dc=example,dc=com" "(objectClass=inetOrgPerson)" uid mail cn
```

결과 예시:

```text
dn: uid=hong.gildong,ou=People,dc=example,dc=com
uid: hong.gildong
mail: hong.gildong@example.com
cn: 홍 길동

dn: uid=kim.chulsoo,ou=People,dc=example,dc=com
uid: kim.chulsoo
mail: kim.chulsoo@example.com
cn: 김 철수
```

### 6.3 AND / OR / NOT / 와일드카드

```bash
# AND: 성이 홍 이고 mail이 있는 사용자
ldapsearch -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -b "ou=People,dc=example,dc=com" "(&(sn=홍)(mail=*))" uid sn mail

# OR: uid가 hong 또는 kim으로 시작
ldapsearch -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -b "ou=People,dc=example,dc=com" "(|(uid=hong*)(uid=kim*))" uid

# NOT: mail에 example.com이 없는 사용자(현재는 0건일 수 있음)
ldapsearch -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -b "ou=People,dc=example,dc=com" "(!(mail=*@example.com))" uid mail
```

결과 예시(AND):

```text
dn: uid=hong.gildong,ou=People,dc=example,dc=com
uid: hong.gildong
sn: 홍
mail: hong.gildong@example.com
```

결과 예시(OR):

```text
dn: uid=hong.gildong,ou=People,dc=example,dc=com
uid: hong.gildong

dn: uid=kim.chulsoo,ou=People,dc=example,dc=com
uid: kim.chulsoo
```

### 6.4 검색 스코프 비교

| 옵션 | 의미 |
|------|------|
| `-s base` | 지정 DN만 |
| `-s one` | 직계 하위만 |
| `-s sub` | 하위 트리 전체(기본) |

```bash
ldapsearch -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -b "dc=example,dc=com" -s one "(objectClass=*)" dn
```

결과 예시:

```text
dn: ou=People,dc=example,dc=com
dn: ou=Groups,dc=example,dc=com
```

---

## 7. 엔트리 수정 (ldapmodify)

### 7.1 속성 변경 / 추가 / 삭제

`/tmp/opendj-lab/03-modify-user.ldif`:

```ldif
dn: uid=hong.gildong,ou=People,dc=example,dc=com
changetype: modify
replace: telephoneNumber
telephoneNumber: +82 10 9999 8888
-
add: description
description: IAM Lab User
-
delete: givenName
givenName: 길동
```

적용:

```bash
ldapmodify -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -f /tmp/opendj-lab/03-modify-user.ldif
```

결과 예시:

```text
modifying entry "uid=hong.gildong,ou=People,dc=example,dc=com"
```

확인:

```bash
ldapsearch -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -b "uid=hong.gildong,ou=People,dc=example,dc=com" -s base "(objectClass=*)" \
  telephoneNumber description givenName cn
```

결과 예시:

```text
dn: uid=hong.gildong,ou=People,dc=example,dc=com
telephoneNumber: +82 10 9999 8888
description: IAM Lab User
cn: 홍 길동
```

> `givenName`은 삭제되어 출력되지 않습니다.

### 7.2 RDN 변경 (modrdn)

`/tmp/opendj-lab/04-modrdn.ldif`:

```ldif
dn: uid=kim.chulsoo,ou=People,dc=example,dc=com
changetype: modrdn
newrdn: uid=kim.cs
deleteoldrdn: 1
```

```bash
ldapmodify -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -f /tmp/opendj-lab/04-modrdn.ldif
```

결과 예시:

```text
modifying RDN of entry "uid=kim.chulsoo,ou=People,dc=example,dc=com"
```

확인:

```bash
ldapsearch -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -b "ou=People,dc=example,dc=com" "(uid=kim.cs)" dn uid
```

결과 예시:

```text
dn: uid=kim.cs,ou=People,dc=example,dc=com
uid: kim.cs
```

---

## 8. 사용자 바인드(인증) 확인

```bash
ldapsearch -h localhost -p 1389 \
  -D "uid=hong.gildong,ou=People,dc=example,dc=com" -w 'Passw0rd!' \
  -b "uid=hong.gildong,ou=People,dc=example,dc=com" -s base "(objectClass=*)" cn mail
```

결과 예시(성공):

```text
dn: uid=hong.gildong,ou=People,dc=example,dc=com
cn: 홍 길동
mail: hong.gildong@example.com
```

잘못된 비밀번호:

```bash
ldapsearch -h localhost -p 1389 \
  -D "uid=hong.gildong,ou=People,dc=example,dc=com" -w 'WrongPass!' \
  -b "uid=hong.gildong,ou=People,dc=example,dc=com" -s base "(objectClass=*)"
```

결과 예시:

```text
ldap_bind: Invalid credentials (49)
```

---

## 9. 엔트리 삭제 (ldapdelete)

```bash
ldapdelete -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  "uid=kim.cs,ou=People,dc=example,dc=com"
```

결과 예시:

```text
# 성공 시 보통 별도 메시지 없이 exit 0
# OpenLDAP ldapdelete 기준: 출력 없음
```

확인:

```bash
ldapsearch -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -b "ou=People,dc=example,dc=com" "(uid=kim.cs)" dn
```

결과 예시:

```text
# search result
# numEntries: 0
```

자식이 있는 DN 삭제 시도(실패 예시):

```bash
ldapdelete -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  "ou=People,dc=example,dc=com"
```

결과 예시:

```text
ldap_delete: Operation not allowed on non-leaf (66)
        additional info: The entry ou=People,dc=example,dc=com cannot be removed because it has subordinate entries
```

재귀 삭제(주의: 실습용):

```bash
ldapdelete -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 -r \
  "ou=People,dc=example,dc=com"
```

> 초급 실습에서는 재귀 삭제 후 다시 OU/사용자를 만들어 다음 단계로 진행하세요.

---

## 10. LDIF Export / Import (초급)

### 10.1 Export

```bash
/opt/opendj/bin/export-ldif \
  --hostname localhost --port 4444 \
  --bindDN "cn=Directory Manager" --bindPassword Secret123 \
  --backendID userRoot \
  --ldifFile /tmp/opendj-lab/export-beginner.ldif \
  --trustAll
```

결과 예시:

```text
...
Exported 4 entries
LDIF export completed successfully
```

### 10.2 Import (오프라인 권장)

```bash
/opt/opendj/bin/stop-ds
/opt/opendj/bin/import-ldif \
  --backendID userRoot \
  --ldifFile /tmp/opendj-lab/export-beginner.ldif \
  --overwrite
/opt/opendj/bin/start-ds
```

결과 예시:

```text
...
Processed 4 entries ...
LDIF import completed successfully
The Directory Server has started successfully
```

---

## 11. 초급 체크리스트

- [ ] `start-ds` / `stop-ds` / `status` 사용 가능
- [ ] OU·사용자 LDIF 추가 성공
- [ ] `uid`, `mail`, AND/OR 필터로 검색 가능
- [ ] `ldapmodify`로 속성 replace/add/delete 가능
- [ ] 사용자 DN으로 바인드 성공/실패 구분
- [ ] 엔트리 삭제 및 non-leaf 오류 이해
- [ ] export-ldif로 데이터 덤프 가능

## 다음 단계

→ [중급 실습](./02-intermediate.md): 스키마, 그룹, ACI, 인덱스, 비밀번호 정책
