# OpenDJ LDAP 실습 — 중급

목표: 스키마 확장, 그룹, ACI(접근제어), 인덱스, 비밀번호 정책을 구성하고 검증한다.

사전 조건: [초급](./01-beginner.md)의 OU/사용자 데이터가 존재해야 합니다.

---

## 0. 중급용 샘플 데이터 준비

초급 실습을 초기화했다면 아래를 먼저 적용하세요.

`/tmp/opendj-lab/10-mid-base.ldif`:

```ldif
dn: ou=People,dc=example,dc=com
objectClass: top
objectClass: organizationalUnit
ou: People

dn: ou=Groups,dc=example,dc=com
objectClass: top
objectClass: organizationalUnit
ou: Groups

dn: uid=hong.gildong,ou=People,dc=example,dc=com
objectClass: top
objectClass: person
objectClass: organizationalPerson
objectClass: inetOrgPerson
cn: 홍 길동
sn: 홍
uid: hong.gildong
mail: hong.gildong@example.com
userPassword: Passw0rd!

dn: uid=kim.chulsoo,ou=People,dc=example,dc=com
objectClass: top
objectClass: person
objectClass: organizationalPerson
objectClass: inetOrgPerson
cn: 김 철수
sn: 김
uid: kim.chulsoo
mail: kim.chulsoo@example.com
userPassword: Passw0rd!

dn: uid=lee.younghee,ou=People,dc=example,dc=com
objectClass: top
objectClass: person
objectClass: organizationalPerson
objectClass: inetOrgPerson
cn: 이 영희
sn: 이
uid: lee.younghee
mail: lee.younghee@example.com
userPassword: Passw0rd!
```

```bash
ldapadd -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -c -f /tmp/opendj-lab/10-mid-base.ldif
```

결과 예시:

```text
adding new entry "ou=People,dc=example,dc=com"
# 이미 있으면: Already exists (무시: -c 옵션)
adding new entry "uid=lee.younghee,ou=People,dc=example,dc=com"
...
```

---

## 1. 스키마 확장 (커스텀 속성/오브젝트클래스)

### 1.1 커스텀 속성·클래스 정의

OpenDJ는 `cn=schema`에 스키마를 추가합니다.

`/tmp/opendj-lab/11-schema.ldif`:

```ldif
dn: cn=schema
changetype: modify
add: attributeTypes
attributeTypes: ( 1.3.6.1.4.1.99999.1.1
  NAME 'empNo'
  DESC 'Employee Number'
  EQUALITY caseIgnoreMatch
  SUBSTR caseIgnoreSubstringsMatch
  SYNTAX 1.3.6.1.4.1.1466.115.121.1.15
  SINGLE-VALUE )
-
add: objectClasses
objectClasses: ( 1.3.6.1.4.1.99999.2.1
  NAME 'examplePerson'
  DESC 'Custom person for lab'
  SUP inetOrgPerson
  STRUCTURAL
  MUST empNo
  MAY ( empTitle $ deptCode ) )
```

> 실습용 OID(`1.3.6.1.4.1.99999...`)입니다. 운영에서는 조직 IANA Private Enterprise Number를 사용하세요.  
> `empTitle`, `deptCode`도 필요하면 같은 방식으로 attributeTypes를 먼저 추가해야 합니다. 아래는 최소 속성만 쓰는 단순 버전입니다.

단순 버전(필수 속성만):

`/tmp/opendj-lab/11-schema-simple.ldif`:

```ldif
dn: cn=schema
changetype: modify
add: attributeTypes
attributeTypes: ( 1.3.6.1.4.1.99999.1.1 NAME 'empNo' DESC 'Employee Number' EQUALITY caseIgnoreMatch SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE )
-
add: objectClasses
objectClasses: ( 1.3.6.1.4.1.99999.2.1 NAME 'examplePerson' DESC 'Custom person' SUP inetOrgPerson STRUCTURAL MUST empNo )
```

적용:

```bash
ldapmodify -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -f /tmp/opendj-lab/11-schema-simple.ldif
```

결과 예시:

```text
modifying entry "cn=schema"
```

### 1.2 스키마 확인

```bash
ldapsearch -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -b "cn=schema" -s base "(objectClass=*)" attributeTypes objectClasses \
  | grep -E "empNo|examplePerson"
```

결과 예시:

```text
attributeTypes: ( 1.3.6.1.4.1.99999.1.1 NAME 'empNo' ...
objectClasses: ( 1.3.6.1.4.1.99999.2.1 NAME 'examplePerson' ...
```

### 1.3 커스텀 클래스 사용자 추가

`/tmp/opendj-lab/12-custom-user.ldif`:

```ldif
dn: uid=park.minsoo,ou=People,dc=example,dc=com
objectClass: top
objectClass: person
objectClass: organizationalPerson
objectClass: inetOrgPerson
objectClass: examplePerson
cn: 박 민수
sn: 박
uid: park.minsoo
mail: park.minsoo@example.com
empNo: E-2026-001
userPassword: Passw0rd!
```

```bash
ldapadd -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -f /tmp/opendj-lab/12-custom-user.ldif
```

결과 예시:

```text
adding new entry "uid=park.minsoo,ou=People,dc=example,dc=com"
```

필수 속성 누락 시:

```ldif
dn: uid=bad.user,ou=People,dc=example,dc=com
objectClass: top
objectClass: person
objectClass: organizationalPerson
objectClass: inetOrgPerson
objectClass: examplePerson
cn: Bad User
sn: Bad
uid: bad.user
```

결과 예시:

```text
ldap_add: Object class violation (65)
        additional info: Entry ... violates the Directory Server schema configuration because it is missing attribute empNo which is required by objectclass examplePerson
```

검색:

```bash
ldapsearch -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -b "ou=People,dc=example,dc=com" "(empNo=E-2026-001)" uid empNo
```

결과 예시:

```text
dn: uid=park.minsoo,ou=People,dc=example,dc=com
uid: park.minsoo
empNo: E-2026-001
```

---

## 2. 그룹 (groupOfNames / groupOfUniqueNames)

### 2.1 정적 그룹 생성

`/tmp/opendj-lab/13-groups.ldif`:

```ldif
dn: cn=admins,ou=Groups,dc=example,dc=com
objectClass: top
objectClass: groupOfNames
cn: admins
member: uid=hong.gildong,ou=People,dc=example,dc=com
member: uid=park.minsoo,ou=People,dc=example,dc=com
description: Directory administrators

dn: cn=employees,ou=Groups,dc=example,dc=com
objectClass: top
objectClass: groupOfNames
cn: employees
member: uid=hong.gildong,ou=People,dc=example,dc=com
member: uid=kim.chulsoo,ou=People,dc=example,dc=com
member: uid=lee.younghee,ou=People,dc=example,dc=com
member: uid=park.minsoo,ou=People,dc=example,dc=com
```

```bash
ldapadd -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -f /tmp/opendj-lab/13-groups.ldif
```

결과 예시:

```text
adding new entry "cn=admins,ou=Groups,dc=example,dc=com"
adding new entry "cn=employees,ou=Groups,dc=example,dc=com"
```

### 2.2 멤버십 검색

특정 사용자가 속한 그룹:

```bash
ldapsearch -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -b "ou=Groups,dc=example,dc=com" \
  "(member=uid=hong.gildong,ou=People,dc=example,dc=com)" cn
```

결과 예시:

```text
dn: cn=admins,ou=Groups,dc=example,dc=com
cn: admins

dn: cn=employees,ou=Groups,dc=example,dc=com
cn: employees
```

멤버 추가:

`/tmp/opendj-lab/14-add-member.ldif`:

```ldif
dn: cn=admins,ou=Groups,dc=example,dc=com
changetype: modify
add: member
member: uid=lee.younghee,ou=People,dc=example,dc=com
```

```bash
ldapmodify -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -f /tmp/opendj-lab/14-add-member.ldif
```

결과 예시:

```text
modifying entry "cn=admins,ou=Groups,dc=example,dc=com"
```

---

## 3. ACI (Access Control Instruction)

OpenDJ는 엔트리의 `aci` 속성으로 접근을 제어합니다.

### 3.1 현재 ACI 확인

```bash
ldapsearch -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -b "dc=example,dc=com" -s base "(objectClass=*)" aci
```

결과 예시(환경마다 다름):

```text
dn: dc=example,dc=com
aci: (targetattr="*")(version 3.0; acl "Anonymous read access";
 allow (read,search,compare) userdn="ldap:///anyone";)
...
```

### 3.2 자기 속성 self-write 허용 + 타인 정보 읽기

`/tmp/opendj-lab/15-aci-self.ldif`:

```ldif
dn: ou=People,dc=example,dc=com
changetype: modify
add: aci
aci: (targetattr="telephoneNumber || description || mail")
 (version 3.0; acl "Self write contact attrs";
 allow (write) userdn="ldap:///self";)
-
add: aci
aci: (targetattr="cn || sn || mail || uid || telephoneNumber || description")
 (version 3.0; acl "Authenticated read people";
 allow (read,search,compare) userdn="ldap:///all";)
```

```bash
ldapmodify -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -f /tmp/opendj-lab/15-aci-self.ldif
```

결과 예시:

```text
modifying entry "ou=People,dc=example,dc=com"
```

### 3.3 일반 사용자로 타인 검색

```bash
ldapsearch -h localhost -p 1389 \
  -D "uid=kim.chulsoo,ou=People,dc=example,dc=com" -w 'Passw0rd!' \
  -b "ou=People,dc=example,dc=com" "(uid=hong.gildong)" cn mail telephoneNumber
```

결과 예시:

```text
dn: uid=hong.gildong,ou=People,dc=example,dc=com
cn: 홍 길동
mail: hong.gildong@example.com
telephoneNumber: +82 10 9999 8888
```

### 3.4 self-write 검증

본인 telephoneNumber 변경:

`/tmp/opendj-lab/16-self-write.ldif`:

```ldif
dn: uid=kim.chulsoo,ou=People,dc=example,dc=com
changetype: modify
replace: telephoneNumber
telephoneNumber: +82 10 1111 2222
```

```bash
ldapmodify -h localhost -p 1389 \
  -D "uid=kim.chulsoo,ou=People,dc=example,dc=com" -w 'Passw0rd!' \
  -f /tmp/opendj-lab/16-self-write.ldif
```

결과 예시:

```text
modifying entry "uid=kim.chulsoo,ou=People,dc=example,dc=com"
```

타인 속성 변경 시도(실패):

`/tmp/opendj-lab/17-write-other.ldif`:

```ldif
dn: uid=hong.gildong,ou=People,dc=example,dc=com
changetype: modify
replace: telephoneNumber
telephoneNumber: +82 10 0000 0000
```

```bash
ldapmodify -h localhost -p 1389 \
  -D "uid=kim.chulsoo,ou=People,dc=example,dc=com" -w 'Passw0rd!' \
  -f /tmp/opendj-lab/17-write-other.ldif
```

결과 예시:

```text
modifying entry "uid=hong.gildong,ou=People,dc=example,dc=com"
ldap_modify: Insufficient access (50)
        additional info: The entry uid=hong.gildong,ou=People,dc=example,dc=com cannot be modified due to insufficient access rights
```

### 3.5 그룹 기반 ACI

`/tmp/opendj-lab/18-aci-group.ldif`:

```ldif
dn: ou=People,dc=example,dc=com
changetype: modify
add: aci
aci: (targetattr="*")
 (version 3.0; acl "Admins full access to people";
 allow (all)
 groupdn="ldap:///cn=admins,ou=Groups,dc=example,dc=com";)
```

```bash
ldapmodify -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -f /tmp/opendj-lab/18-aci-group.ldif
```

결과 예시:

```text
modifying entry "ou=People,dc=example,dc=com"
```

admins 멤버(hong)로 타인 수정:

```bash
ldapmodify -h localhost -p 1389 \
  -D "uid=hong.gildong,ou=People,dc=example,dc=com" -w 'Passw0rd!' \
  -f /tmp/opendj-lab/17-write-other.ldif
```

결과 예시:

```text
modifying entry "uid=hong.gildong,ou=People,dc=example,dc=com"
```

---

## 4. 인덱스

검색이 느려지거나 unindexed로 경고가 뜨면 인덱스를 추가합니다.

### 4.1 현재 인덱스 목록

```bash
/opt/opendj/bin/dsconfig list-backend-indexes \
  --hostname localhost --port 4444 \
  --bindDN "cn=Directory Manager" --bindPassword Secret123 \
  --backend-name userRoot \
  --trustAll --no-prompt
```

결과 예시:

```text
Backend Index : index-type                  : index-entry-limit
--------------:-----------------------------:------------------
aci           : presence                    : 4000
cn            : equality, substring         : 4000
...
mail          : equality                    : 4000
uid           : equality                    : 4000
...
```

### 4.2 empNo equality 인덱스 추가

```bash
/opt/opendj/bin/dsconfig create-backend-index \
  --hostname localhost --port 4444 \
  --bindDN "cn=Directory Manager" --bindPassword Secret123 \
  --backend-name userRoot \
  --index-name empNo \
  --set index-type:equality \
  --trustAll --no-prompt
```

결과 예시:

```text
The Backend Index was created successfully
```

### 4.3 인덱스 재구축

온라인 재구축:

```bash
/opt/opendj/bin/rebuild-index \
  --hostname localhost --port 4444 \
  --bindDN "cn=Directory Manager" --bindPassword Secret123 \
  --baseDN "dc=example,dc=com" \
  --index empNo \
  --trustAll
```

결과 예시:

```text
...
Rebuild Index task ... has completed successfully
```

### 4.4 unindexed 검색 관찰 (의도적)

인덱스가 없는 속성으로 광범위 검색 시 서버 로그/접근 로그에 unindexed 경고가 남을 수 있습니다.

```bash
ldapsearch -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -b "ou=People,dc=example,dc=com" "(description=*Lab*)" uid description
```

결과 예시(데이터에 따라):

```text
dn: uid=hong.gildong,ou=People,dc=example,dc=com
uid: hong.gildong
description: IAM Lab User
```

접근 로그 확인:

```bash
tail -n 30 /opt/opendj/logs/access | grep -i unindexed || true
```

결과 예시:

```text
... SEARCH ... SCOPE=sub FILTER=(description=*Lab*) ... unindexed=true ...
```

---

## 5. 비밀번호 정책

### 5.1 기본 비밀번호 정책 확인

```bash
/opt/opendj/bin/dsconfig get-password-policy-prop \
  --hostname localhost --port 4444 \
  --bindDN "cn=Directory Manager" --bindPassword Secret123 \
  --policy-name "Default Password Policy" \
  --trustAll --no-prompt
```

결과 예시:

```text
Property                                  : Value(s)
------------------------------------------:--------------------------
account-status-notification-handler       : -
allow-expired-password-changes            : false
default-password-storage-scheme           : Salted SHA-512
...
password-validator                        : -
```

### 5.2 길이 검증기 생성 및 정책에 연결

```bash
/opt/opendj/bin/dsconfig create-password-validator \
  --hostname localhost --port 4444 \
  --bindDN "cn=Directory Manager" --bindPassword Secret123 \
  --validator-name "Min 8 Chars" \
  --type length-based \
  --set enabled:true \
  --set min-password-length:8 \
  --trustAll --no-prompt
```

결과 예시:

```text
The Password Validator was created successfully
```

```bash
/opt/opendj/bin/dsconfig set-password-policy-prop \
  --hostname localhost --port 4444 \
  --bindDN "cn=Directory Manager" --bindPassword Secret123 \
  --policy-name "Default Password Policy" \
  --add password-validator:"Min 8 Chars" \
  --trustAll --no-prompt
```

결과 예시:

```text
The Password Policy was modified successfully
```

### 5.3 짧은 비밀번호 거부 확인

`/tmp/opendj-lab/19-short-pw.ldif`:

```ldif
dn: uid=short.pw,ou=People,dc=example,dc=com
objectClass: top
objectClass: person
objectClass: organizationalPerson
objectClass: inetOrgPerson
cn: Short PW
sn: Short
uid: short.pw
userPassword: 123
```

```bash
ldapadd -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -f /tmp/opendj-lab/19-short-pw.ldif
```

결과 예시:

```text
adding new entry "uid=short.pw,ou=People,dc=example,dc=com"
ldap_add: Constraint violation (19)
        additional info: The provided password value was rejected by a password validator: The provided password is shorter than the minimum required length of 8 characters
```

정상 길이:

```ldif
dn: uid=ok.pw,ou=People,dc=example,dc=com
objectClass: top
objectClass: person
objectClass: organizationalPerson
objectClass: inetOrgPerson
cn: OK PW
sn: OK
uid: ok.pw
userPassword: Passw0rd!
```

결과 예시:

```text
adding new entry "uid=ok.pw,ou=People,dc=example,dc=com"
```

### 5.4 비밀번호 변경 (ldappasswordmodify 또는 ldapmodify)

```bash
ldappasswordmodify -h localhost -p 1389 \
  -D "uid=kim.chulsoo,ou=People,dc=example,dc=com" -w 'Passw0rd!' \
  -a 'Passw0rd!' -n 'NewPassw0rd!'
```

결과 예시:

```text
The LDAP password modify operation was successful
```

또는 LDIF:

```ldif
dn: uid=kim.chulsoo,ou=People,dc=example,dc=com
changetype: modify
replace: userPassword
userPassword: NewPassw0rd!
```

---

## 6. Virtual List View / Size Limit (중급 팁)

대량 검색 시 클라이언트 size limit:

```bash
ldapsearch -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -b "ou=People,dc=example,dc=com" -z 2 "(objectClass=inetOrgPerson)" uid
```

결과 예시:

```text
dn: uid=hong.gildong,ou=People,dc=example,dc=com
uid: hong.gildong

dn: uid=kim.chulsoo,ou=People,dc=example,dc=com
uid: kim.chulsoo

# search result
# result: 4 Size limit exceeded
# numEntries: 2
```

---

## 7. dsconfig로 리스너/연결 확인

```bash
/opt/opendj/bin/dsconfig list-connection-handlers \
  --hostname localhost --port 4444 \
  --bindDN "cn=Directory Manager" --bindPassword Secret123 \
  --trustAll --no-prompt
```

결과 예시:

```text
Connection Handler       : Type : enabled
-------------------------:------:--------
LDAP Connection Handler  : ldap : true
LDAPS Connection Handler : ldaps: true
JMX Connection Handler   : jmx  : false
```

---

## 8. 중급 체크리스트

- [ ] 커스텀 attribute/objectClass 추가 및 검증
- [ ] `groupOfNames` 생성·멤버 추가·역검색
- [ ] self-write / groupdn ACI 동작 확인
- [ ] `empNo` 인덱스 생성·rebuild
- [ ] 비밀번호 길이 검증기 적용 및 거부 확인
- [ ] size limit / unindexed 개념 이해

## 다음 단계

→ [고급 실습](./03-advanced.md): 백업/복구, 복제, 성능, 감사, 장애 대응
