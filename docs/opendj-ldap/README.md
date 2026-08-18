# OpenDJ LDAP 실습 가이드

OpenDJ(ForgeRock Directory Services / Open Identity Platform OpenDJ) 기반 LDAP 실습 문서입니다.  
초급 → 중급 → 고급 순으로 진행하며, 각 단계마다 **명령문**과 **결과 예시**를 포함합니다.

## 문서 구성

| 단계 | 파일 | 핵심 주제 |
|------|------|-----------|
| 초급 | [01-beginner.md](./01-beginner.md) | 설치, 기동, 기본 CRUD, 검색 필터 |
| 중급 | [02-intermediate.md](./02-intermediate.md) | 스키마, ACI, 인덱스, 비밀번호 정책, 그룹 |
| 고급 | [03-advanced.md](./03-advanced.md) | 복제, 백업/복구, 성능 튜닝, 감사/장애 대응 |

## 실습 환경 가정

본 문서의 예시는 아래 환경을 기준으로 작성했습니다.

```text
OpenDJ 버전     : 4.x (Open Identity Platform OpenDJ)
설치 경로       : /opt/opendj
인스턴스 경로   : /opt/opendj
LDAP Port       : 1389
LDAPS Port      : 1636
Admin Port      : 4444
Base DN         : dc=example,dc=com
Root DN         : cn=Directory Manager
Root Password   : Secret123
호스트          : localhost
```

> 실제 환경의 경로·포트·비밀번호가 다르면 명령의 해당 값만 바꿔 사용하세요.

## 권장 학습 순서

```text
[초급]
 설치/기동 → ldapsearch → ldapadd → ldapmodify → ldapdelete
        ↓
[중급]
 스키마 확장 → 그룹 → ACI → 인덱스 → 비밀번호 정책
        ↓
[고급]
 백업/복구 → 복제 → 성능 점검 → 로그/감사 → 장애 시나리오
```

## 공통 도구

| 도구 | 용도 |
|------|------|
| `ldapsearch` | 엔트리 검색 |
| `ldapadd` / `ldapmodify` / `ldapdelete` | 엔트리 추가/수정/삭제 |
| `dsconfig` | OpenDJ 서버 설정 |
| `status` | 서버 상태 확인 |
| `backup` / `restore` | 백업·복구 |
| `dsreplication` | 복제 구성 |

## 빠른 상태 확인

```bash
/opt/opendj/bin/status --bindDN "cn=Directory Manager" --bindPassword Secret123
```

결과 예시:

```text
--- Server Status ---
Server Run Status:        Started
Open Connections:         0

--- Server Details ---
Host Name:                localhost
Administrative Users:     cn=Directory Manager
Installation Path:        /opt/opendj
Instance Path:            /opt/opendj
Version:                  OpenDJ 4.x.x
Java Version:             11.x
Connection Handlers:      [LDAP : 1389]
                          [LDAPS : 1636]
                          [Administration : 4444]

--- Connection Handlers ---
Address:Port : Protocol : State
-------------:----------:------
--:1389      : LDAP     : Enabled
--:1636      : LDAPS    : Enabled
--:4444      : Admin    : Enabled
```

## 실습 LDIF 샘플 위치

각 단계 문서에 인라인 LDIF가 포함되어 있습니다.  
필요 시 아래처럼 파일로 저장해 사용하세요.

```bash
mkdir -p /tmp/opendj-lab
# 각 문서의 LDIF 블록을 /tmp/opendj-lab/*.ldif 로 저장
```
