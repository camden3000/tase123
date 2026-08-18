# OpenDJ LDAP 실습 — 고급

목표: 백업·복구, 복제, 성능 점검, 감사/로그, 장애 시나리오를 다루고 운영 관점에서 검증한다.

사전 조건: [초급](./01-beginner.md), [중급](./02-intermediate.md) 개념을 이해하고, 최소 1개 OpenDJ 인스턴스가 동작 중이어야 합니다.

---

## 1. 운영 상태·메트릭 확인

### 1.1 상세 status

```bash
/opt/opendj/bin/status \
  --hostname localhost --port 4444 \
  --bindDN "cn=Directory Manager" --bindPassword Secret123 \
  --trustAll
```

결과 예시:

```text
--- Server Status ---
Server Run Status:        Started
Open Connections:         2

--- Base DN ---
Base DN:                  dc=example,dc=com
Backend ID:               userRoot
Entries:                  12
Replication:              Not enabled

--- JE Backend ---
DB Cache Size:            ...
DB Cache Percent Full:    ...
```

### 1.2 monitor 엔트리 조회

```bash
ldapsearch -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -b "cn=monitor" -s base "(objectClass=*)"
```

결과 예시(일부):

```text
dn: cn=monitor
objectClass: top
objectClass: extensibleObject
objectClass: ds-monitor-entry
currentTime: 20260818234000Z
startTime: 20260818230000Z
version: OpenDJ 4.x.x
```

백엔드 엔트리 수:

```bash
ldapsearch -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -b "cn=userRoot Backend,cn=monitor" -s base "(objectClass=*)" ds-mon-*
```

결과 예시:

```text
dn: cn=userRoot Backend,cn=monitor
ds-mon-backend-entry-count: 12
ds-mon-db-cache-total-bytes-used: 5242880
...
```

> 속성명은 OpenDJ 버전에 따라 `ds-backend-entry-count` 등일 수 있습니다. `ldapsearch ... cn=monitor -s sub`로 실제 이름을 확인하세요.

---

## 2. 백업 / 복구

### 2.1 온라인 백업

```bash
mkdir -p /opt/opendj/bak
/opt/opendj/bin/backup \
  --hostname localhost --port 4444 \
  --bindDN "cn=Directory Manager" --bindPassword Secret123 \
  --backendID userRoot \
  --backupDirectory /opt/opendj/bak \
  --start 0 \
  --trustAll
```

결과 예시:

```text
Backup task ... scheduled to start immediately
...
The backup process completed successfully
```

백업 목록:

```bash
/opt/opendj/bin/backup \
  --hostname localhost --port 4444 \
  --bindDN "cn=Directory Manager" --bindPassword Secret123 \
  --backupDirectory /opt/opendj/bak \
  --listBackups \
  --trustAll
```

결과 예시:

```text
Backup ID:          20260818-234015
Backup Date:        18/Aug/2026:23:40:15 +0000
Is Incremental:     false
Is Compressed:      false
Is Encrypted:       false
...
```

### 2.2 복구 (오프라인)

```bash
/opt/opendj/bin/stop-ds

/opt/opendj/bin/restore \
  --backupDirectory /opt/opendj/bak \
  --backendID userRoot \
  --backupID 20260818-234015

/opt/opendj/bin/start-ds
```

결과 예시:

```text
Restoring backup 20260818-234015 ...
...
The restore process completed successfully
The Directory Server has started successfully
```

### 2.3 LDIF 기반 재해 복구 연습

```bash
/opt/opendj/bin/export-ldif \
  --hostname localhost --port 4444 \
  --bindDN "cn=Directory Manager" --bindPassword Secret123 \
  --backendID userRoot \
  --ldifFile /tmp/opendj-lab/dr-export.ldif \
  --trustAll
```

의도적 데이터 삭제 후:

```bash
ldapdelete -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 -r \
  "ou=People,dc=example,dc=com"
```

결과 예시:

```text
# 하위 포함 삭제 완료(도구에 따라 출력 없음)
```

오프라인 import로 복원:

```bash
/opt/opendj/bin/stop-ds
/opt/opendj/bin/import-ldif \
  --backendID userRoot \
  --ldifFile /tmp/opendj-lab/dr-export.ldif \
  --overwrite
/opt/opendj/bin/start-ds
```

결과 예시:

```text
Processed N entries, imported N, skipped 0, rejected 0 ...
LDIF import completed successfully
```

검증:

```bash
ldapsearch -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -b "ou=People,dc=example,dc=com" "(objectClass=inetOrgPerson)" uid \
  | grep '^dn:' | wc -l
```

결과 예시:

```text
4
```

---

## 3. 복제 (Replication) — 2노드 실습

실습 토폴로지:

```text
          +------------------+
          |   Replication    |
          |   (changelog)    |
          +---------+--------+
                    |
        +-----------+-----------+
        |                       |
+-------v------+         +------v-------+
| opendj-1     | <-----> | opendj-2     |
| :1389 / :4444|         | :2389 / :5444|
| dc=example   |         | dc=example   |
+--------------+         +--------------+
```

> 한 호스트에서 포트만 다르게 두 인스턴스를 띄우는 구성을 가정합니다.  
> 실제 경로 예: `/opt/opendj1`, `/opt/opendj2`

### 3.1 두 번째 인스턴스 준비(요약)

```bash
# opendj2 설치 시 LDAP 2389, Admin 5444, Base DN 동일
/opt/opendj2/setup --cli
```

입력 요약:

```text
LDAP Port: 2389
Admin Port: 5444
Base DN: dc=example,dc=com
Root DN / Password: cn=Directory Manager / Secret123
```

### 3.2 복제 활성화

opendj1에서:

```bash
/opt/opendj1/bin/dsreplication enable \
  --host1 localhost --port1 4444 \
  --bindDN1 "cn=Directory Manager" --bindPassword1 Secret123 \
  --replicationPort1 8989 \
  --host2 localhost --port2 5444 \
  --bindDN2 "cn=Directory Manager" --bindPassword2 Secret123 \
  --replicationPort2 8990 \
  --adminUID admin --adminPassword Admin123 \
  --baseDN "dc=example,dc=com" \
  --trustAll --no-prompt
```

결과 예시:

```text
Initializing Registration Information ... Done.
Configuring Replication port on server localhost:4444 ... Done.
Configuring Replication port on server localhost:5444 ... Done.
Updating replication configuration on base DN dc=example,dc=com ... Done.
...
Replication has been successfully enabled
```

### 3.3 초기 데이터 동기화

```bash
/opt/opendj1/bin/dsreplication initialize \
  --hostSource localhost --portSource 4444 \
  --hostDestination localhost --portDestination 5444 \
  --baseDN "dc=example,dc=com" \
  --adminUID admin --adminPassword Admin123 \
  --trustAll --no-prompt
```

결과 예시:

```text
Initializing base DN dc=example,dc=com with the contents from localhost:4444 ...
...
Base DN initialized successfully.
```

### 3.4 복제 상태

```bash
/opt/opendj1/bin/dsreplication status \
  --hostname localhost --port 4444 \
  --adminUID admin --adminPassword Admin123 \
  --trustAll --no-prompt
```

결과 예시:

```text
Base DN:             dc=example,dc=com
Status:              Enabled
Server:              localhost:4444
Entries:             12
Replication Delay:   0
...
Server:              localhost:5444
Entries:             12
Replication Delay:   0
```

### 3.5 쓰기 후 전파 확인

opendj1에 사용자 추가:

```bash
ldapadd -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 <<'EOF'
dn: uid=repl.test,ou=People,dc=example,dc=com
objectClass: top
objectClass: person
objectClass: organizationalPerson
objectClass: inetOrgPerson
cn: Repl Test
sn: Repl
uid: repl.test
userPassword: Passw0rd!
EOF
```

결과 예시:

```text
adding new entry "uid=repl.test,ou=People,dc=example,dc=com"
```

opendj2에서 확인:

```bash
ldapsearch -h localhost -p 2389 -D "cn=Directory Manager" -w Secret123 \
  -b "ou=People,dc=example,dc=com" "(uid=repl.test)" dn cn
```

결과 예시:

```text
dn: uid=repl.test,ou=People,dc=example,dc=com
cn: Repl Test
```

### 3.6 복제 지연 인위 확인(선택)

한 노드를 잠시 중지한 뒤 다른 노드에 쓰기를 넣고, 재기동 후 따라잡는 과정을 관찰합니다.

```bash
/opt/opendj2/bin/stop-ds
# opendj1에 여러 엔트리 추가
/opt/opendj2/bin/start-ds
/opt/opendj1/bin/dsreplication status \
  --hostname localhost --port 4444 \
  --adminUID admin --adminPassword Admin123 \
  --trustAll --no-prompt
```

결과 예시(일시적):

```text
Replication Delay:   2 seconds
...
# 곧 0으로 수렴
```

---

## 4. 성능·부하 점검

### 4.1 searchrate (OpenDJ 동봉 도구가 있는 경우)

```bash
/opt/opendj/bin/searchrate \
  -h localhost -p 1389 \
  -D "cn=Directory Manager" -w Secret123 \
  -b "ou=People,dc=example,dc=com" \
  -f "(uid=%s)" \
  -a uid=hong.gildong,uid=kim.chulsoo,uid=lee.younghee,uid=park.minsoo \
  -c 4 -t 10
```

결과 예시:

```text
-------------------------------------------------------------------------------
     Throughput                            Response Time
   Ops/Second  Recent  Average  Recent  Average  Recent  Average
              (ops/sec)(ops/sec) (ms)    (ms)    (ms)    (ms)
-------------------------------------------------------------------------------
   1850.2      1850.2   1850.2   2.1     2.1     1.8     1.8
...
```

### 4.2 modrate

```bash
/opt/opendj/bin/modrate \
  -h localhost -p 1389 \
  -D "cn=Directory Manager" -w Secret123 \
  -b "uid=hong.gildong,ou=People,dc=example,dc=com" \
  -F "description" -v "perf-%d" \
  -c 2 -t 5
```

결과 예시:

```text
-------------------------------------------------------------------------------
     Throughput                            Response Time
   Ops/Second  Recent  Average  Recent  Average
-------------------------------------------------------------------------------
   420.5       420.5    420.5    4.5     4.5
```

### 4.3 DB 캐시 튜닝 확인

```bash
/opt/opendj/bin/dsconfig get-backend-prop \
  --hostname localhost --port 4444 \
  --bindDN "cn=Directory Manager" --bindPassword Secret123 \
  --backend-name userRoot \
  --property db-cache-percent \
  --property db-cache-size \
  --trustAll --no-prompt
```

결과 예시:

```text
Property         : Value(s)
-----------------:---------
db-cache-percent : 50
db-cache-size    : -
```

캐시 비율 조정 예시(실습용, 재시작 필요할 수 있음):

```bash
/opt/opendj/bin/dsconfig set-backend-prop \
  --hostname localhost --port 4444 \
  --bindDN "cn=Directory Manager" --bindPassword Secret123 \
  --backend-name userRoot \
  --set db-cache-percent:60 \
  --trustAll --no-prompt
```

결과 예시:

```text
The Backend was modified successfully
```

### 4.4 워커 스레드 / 연결 제한

```bash
/opt/opendj/bin/dsconfig get-connection-handler-prop \
  --hostname localhost --port 4444 \
  --bindDN "cn=Directory Manager" --bindPassword Secret123 \
  --handler-name "LDAP Connection Handler" \
  --property max-request-size \
  --property accept-backlog \
  --trustAll --no-prompt
```

결과 예시:

```text
Property         : Value(s)
-----------------:---------
accept-backlog   : 128
max-request-size : 5 mb
```

---

## 5. 감사·접근 로그 / Debug

### 5.1 access 로그 실시간 관찰

```bash
tail -f /opt/opendj/logs/access
```

결과 예시:

```text
[18/Aug/2026:23:45:01 +0000] CONNECT conn=12 from=127.0.0.1:50321 to=127.0.0.1:1389 protocol=LDAP
[18/Aug/2026:23:45:01 +0000] BIND REQ conn=12 op=0 msgID=1 version=3 type=SIMPLE dn="uid=hong.gildong,ou=People,dc=example,dc=com"
[18/Aug/2026:23:45:01 +0000] BIND RES conn=12 op=0 msgID=1 result=0 authDN="uid=hong.gildong,ou=People,dc=example,dc=com" etime=1
[18/Aug/2026:23:45:01 +0000] SEARCH REQ conn=12 op=1 msgID=2 base="ou=People,dc=example,dc=com" scope=sub filter="(uid=kim.chulsoo)" attrs="cn,mail"
[18/Aug/2026:23:45:01 +0000] SEARCH RES conn=12 op=1 msgID=2 result=0 nentries=1 etime=2
```

### 5.2 실패 인증 탐지

```bash
grep 'result=49' /opt/opendj/logs/access | tail -n 5
```

결과 예시:

```text
[18/Aug/2026:23:46:10 +0000] BIND RES conn=15 op=0 msgID=1 result=49 authFailureID=49 ... etime=1
```

### 5.3 errors / debug 로그

```bash
tail -n 50 /opt/opendj/logs/errors
```

결과 예시:

```text
[18/Aug/2026:23:40:00 +0000] category=CORE severity=NOTICE ... The Directory Server has started successfully
```

디버그 타깃 활성화(과다 로그 주의):

```bash
/opt/opendj/bin/dsconfig set-log-publisher-prop \
  --hostname localhost --port 4444 \
  --bindDN "cn=Directory Manager" --bindPassword Secret123 \
  --publisher-name "File-Based Access Logger" \
  --set log-format:combined \
  --trustAll --no-prompt
```

결과 예시:

```text
The Log Publisher was modified successfully
```

---

## 6. 가상 속성 / Collective Attribute (고급 활용)

### 6.1 isMemberOf 확인 (그룹 멤버십 가상속성)

OpenDJ는 보통 `isMemberOf`/`memberOf` 계열 가상 속성을 제공합니다(버전·설정에 따라 이름 상이).

```bash
ldapsearch -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -b "uid=hong.gildong,ou=People,dc=example,dc=com" -s base "(objectClass=*)" isMemberOf
```

결과 예시:

```text
dn: uid=hong.gildong,ou=People,dc=example,dc=com
isMemberOf: cn=admins,ou=Groups,dc=example,dc=com
isMemberOf: cn=employees,ou=Groups,dc=example,dc=com
```

### 6.2 Collective Attribute로 부서 기본값 부여(개념 실습)

`/tmp/opendj-lab/30-collective.ldif`:

```ldif
dn: cn=Employee Collective Attributes,dc=example,dc=com
objectClass: top
objectClass: subentry
objectClass: collectiveAttributeSubentry
objectClass: extensibleObject
cn: Employee Collective Attributes
o;collective: Example Corp
subtreeSpecification: { base "ou=People", minimum 1 }
```

```bash
ldapadd -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -f /tmp/opendj-lab/30-collective.ldif
```

결과 예시:

```text
adding new entry "cn=Employee Collective Attributes,dc=example,dc=com"
```

사용자에서 확인:

```bash
ldapsearch -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -b "uid=hong.gildong,ou=People,dc=example,dc=com" -s base "(objectClass=*)" o
```

결과 예시:

```text
dn: uid=hong.gildong,ou=People,dc=example,dc=com
o: Example Corp
```

---

## 7. 장애·보안 시나리오

### 7.1 디스크 부족/백엔드 잠금 시뮬레이션(개념)

백엔드 디렉터리 권한 오류를 내면 기동 실패 로그를 볼 수 있습니다.  
실습에서는 **권한을 잠시 바꾼 뒤 반드시 원복**하세요.

```bash
/opt/opendj/bin/stop-ds
# 주의: 실습 후 즉시 복구
chmod 000 /opt/opendj/db/userRoot || true
/opt/opendj/bin/start-ds || true
tail -n 20 /opt/opendj/logs/errors
chmod 700 /opt/opendj/db/userRoot
/opt/opendj/bin/start-ds
```

오류 로그 예시:

```text
category=BACKEND severity=ERROR ... Unable to open the database environment ... Permission denied
```

### 7.2 스키마 위반 대량 import 거부

잘못된 LDIF:

```ldif
dn: uid=nosn,ou=People,dc=example,dc=com
objectClass: inetOrgPerson
objectClass: organizationalPerson
objectClass: person
objectClass: top
cn: No SN
uid: nosn
```

```bash
ldapadd -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -f /tmp/opendj-lab/31-bad-schema.ldif
```

결과 예시:

```text
ldap_add: Object class violation (65)
        additional info: Entry ... is missing attribute sn which is required by objectclass person
```

### 7.3 계정 잠금 정책(고급 비밀번호 정책)

```bash
/opt/opendj/bin/dsconfig create-password-policy \
  --hostname localhost --port 4444 \
  --bindDN "cn=Directory Manager" --bindPassword Secret123 \
  --policy-name "Lockout Policy" \
  --type password-policy \
  --set password-attribute:userPassword \
  --set default-password-storage-scheme:"Salted SHA-512" \
  --set lockout-failure-count:3 \
  --set lockout-duration:2m \
  --trustAll --no-prompt
```

결과 예시:

```text
The Password Policy was created successfully
```

사용자에 정책 할당:

```ldif
dn: uid=lee.younghee,ou=People,dc=example,dc=com
changetype: modify
replace: ds-pwp-password-policy-dn
ds-pwp-password-policy-dn: cn=Lockout Policy,cn=Password Policies,cn=config
```

```bash
ldapmodify -h localhost -p 1389 -D "cn=Directory Manager" -w Secret123 \
  -f /tmp/opendj-lab/32-assign-lockout.ldif
```

의도적 실패 3회:

```bash
for i in 1 2 3; do
  ldapsearch -h localhost -p 1389 \
    -D "uid=lee.younghee,ou=People,dc=example,dc=com" -w 'Wrong!' \
    -b "" -s base "(objectClass=*)" 2>&1 | tail -n 1
done
```

결과 예시:

```text
ldap_bind: Invalid credentials (49)
ldap_bind: Invalid credentials (49)
ldap_bind: Invalid credentials (49)
```

이후 정상 비밀번호도 일시 거부될 수 있음:

```bash
ldapsearch -h localhost -p 1389 \
  -D "uid=lee.younghee,ou=People,dc=example,dc=com" -w 'Passw0rd!' \
  -b "uid=lee.younghee,ou=People,dc=example,dc=com" -s base "(objectClass=*)" cn
```

결과 예시:

```text
ldap_bind: Invalid credentials (49)
# 또는 Constraint violation / Account locked 계열 메시지(버전별)
```

### 7.4 LDAPS 연결 확인

```bash
ldapsearch -H ldaps://localhost:1636 -D "cn=Directory Manager" -w Secret123 \
  -b "dc=example,dc=com" -s base "(objectClass=*)" dn \
  -o tls_reqcert=never
```

결과 예시:

```text
dn: dc=example,dc=com
```

OpenSSL로 인증서 확인:

```bash
echo | openssl s_client -connect localhost:1636 -servername localhost 2>/dev/null \
  | openssl x509 -noout -subject -dates
```

결과 예시:

```text
subject=CN = localhost
notBefore=Aug  1 00:00:00 2025 GMT
notAfter=Aug  1 00:00:00 2027 GMT
```

---

## 8. 설정 백업 (config.ldif)

```bash
cp /opt/opendj/config/config.ldif /tmp/opendj-lab/config.ldif.bak
ls -l /tmp/opendj-lab/config.ldif.bak
```

결과 예시:

```text
-rw-r----- 1 opendj opendj 245760 Aug 18 23:50 /tmp/opendj-lab/config.ldif.bak
```

변경 이력(서버가 남기는 경우):

```bash
ls /opt/opendj/config/archived-configs | tail
```

결과 예시:

```text
config-20260818230000.gz
config-20260818234500.gz
```

---

## 9. 종합 트러블슈팅 플로우

```text
증상 발생
   │
   ├─ 기동 실패? → errors 로그 → DB 권한/디스크/포트 충돌
   │
   ├─ 바인드 실패(49)? → 비밀번호/잠금/정책/계정 상태
   │
   ├─ 검색 느림? → access의 etime/unindexed → 인덱스/캐시
   │
   ├─ 권한 오류(50)? → ACI / root DN / group 멤버십
   │
   └─ 복제 불일치? → dsreplication status → initialize / 지연 확인
```

유용한 원라이너:

```bash
# 최근 실패 결과 코드 집계
awk '/RES /{for(i=1;i<=NF;i++) if($i ~ /^result=/) print $i}' /opt/opendj/logs/access \
  | sort | uniq -c | sort -nr | head
```

결과 예시:

```text
    120 result=0
      8 result=49
      2 result=50
      1 result=32
```

---

## 10. 고급 체크리스트

- [ ] online backup / listBackups / offline restore
- [ ] export-ldif ↔ import-ldif 재해 복구
- [ ] 2노드 복제 enable → initialize → status → 전파 확인
- [ ] searchrate/modrate로 대략적 성능 측정
- [ ] access 로그로 BIND/SEARCH 추적
- [ ] 계정 잠금 정책 적용 및 동작 확인
- [ ] LDAPS 핸드셰이크 확인
- [ ] monitor/config 아카이브 위치 파악

## 참고

- 초급: [01-beginner.md](./01-beginner.md)
- 중급: [02-intermediate.md](./02-intermediate.md)
- 목차: [README.md](./README.md)

## 운영 주의사항

1. 문서의 비밀번호(`Secret123`, `Passw0rd!`)는 **실습 전용**입니다.
2. 복제/권한/스키마 변경은 운영 반영 전 스테이징에서 검증하세요.
3. `chmod`로 장애를 재현한 경우 **반드시 원복** 후 기동하세요.
4. OID·인증서·포트는 조직 표준에 맞게 교체하세요.
