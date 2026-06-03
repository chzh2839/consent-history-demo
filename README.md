# consent-history-demo

> **약관 버전 관리 및 동의 이력 추적 데모 프로젝트**  
> Spring Boot + JPA + H2 기반의 최소 구현체

---

## 왜 만들었는가

실무에서 약관 시스템을 설계·구현한 경험을 코드로 명확하게 보여주기 위해 만들었습니다.

약관 관리는 단순 CRUD처럼 보이지만, 실제로는 다음과 같은 까다로운 요구사항이 숨어 있습니다.

- **"사용자가 동의한 시점의 약관 내용"** 을 언제든 재현할 수 있어야 한다
- **개정된 약관에 재동의** 유도가 필요하고, 그 이전 동의 이력도 보존해야 한다
- **규제/감사 대응** 을 위해 동의·철회 모든 행위가 이력으로 남아야 한다

이 프로젝트는 그 핵심 설계 원칙을 가장 단순한 형태로 구현합니다.

---

## 약관 버전 관리의 필요성

### ❌ 버전 관리 없이 약관을 수정하면

```
[기존 방식]
terms 테이블: content = "약관 내용 v1.0"
→ 개정 시: content = "약관 내용 v2.0" 으로 UPDATE

문제: 사용자가 v1.0에 동의했는데, 지금 조회하면 v2.0 내용이 보인다.
     → "동의한 내용"과 "보이는 내용"이 달라진다.
```

### ✅ 버전 관리를 도입하면

```
[이 프로젝트의 방식]
terms_version 테이블에 버전별로 INSERT (절대 UPDATE 안 함)

v1.0 레코드: "약관 내용 v1.0", is_active=false
v2.0 레코드: "약관 내용 v2.0", is_active=true  ← 현행

동의 이력에는 terms_version_id를 저장
→ 사용자가 동의한 시점의 내용을 언제든 정확히 재현 가능
```

---

## 핵심 설계 원칙

### 1. 약관 버전의 불변성 (Immutability)

`terms_version` 레코드는 **INSERT Only**, 절대 UPDATE/DELETE하지 않습니다.

약관이 개정되면:
1. 기존 활성 버전: `is_active = false`
2. 새 버전 레코드: INSERT (`is_active = true`)

### 2. 동의 이력의 불변성 (Append-Only Log)

`consent_history` 레코드도 **INSERT Only**입니다.

```
동의  → status=AGREED   레코드 INSERT
철회  → status=WITHDRAWN 레코드 INSERT
재동의 → status=AGREED   레코드 INSERT
```

현재 동의 상태는 **가장 최신 레코드의 status** 로 판단합니다.  
이 방식으로 모든 변경 시점이 타임스탬프와 함께 추적됩니다.

---

## ERD

```
┌──────────────────┐       ┌──────────────────────┐       ┌─────────────────────┐
│      USERS       │       │    TERMS_VERSION     │       │       TERMS         │
├──────────────────┤       ├──────────────────────┤       ├─────────────────────┤
│ id (PK)          │       │ id (PK)              │       │ id (PK)             │
│ username         │       │ terms_id (FK) ───────┼──────▶│ terms_code          │
│ email            │       │ version              │       │ terms_name          │
│ created_at       │       │ content              │       │ is_mandatory        │
└────────┬─────────┘       │ is_active            │       │ created_at          │
         │                 │ effective_date       │       └─────────────────────┘
         │                 │ created_at           │
         │                 └──────────┬───────────┘
         │                            │
         │    ┌───────────────────────┘
         │    │
         ▼    ▼
┌───────────────────────────┐
│      CONSENT_HISTORY      │
├───────────────────────────┤
│ id (PK)                   │
│ user_id (FK)              │
│ terms_version_id (FK)     │  ← 동의 시점의 버전을 정확히 기록
│ status (AGREED/WITHDRAWN) │
│ consented_at              │
└───────────────────────────┘
```

### 테이블 설명

| 테이블 | 역할 |
|---|---|
| `USERS` | 사용자 정보 |
| `TERMS` | 약관 종류 마스터 (서비스 이용약관, 개인정보 처리방침 등) |
| `TERMS_VERSION` | 약관의 버전별 내용 (불변 레코드) |
| `CONSENT_HISTORY` | 동의/철회 이력 (Append-Only) |

---

## API 명세

### 약관 조회

| Method | URL | 설명 |
|---|---|---|
| `GET` | `/terms` | 현행 활성 약관 전체 목록 |
| `GET` | `/terms/{termsId}/versions` | 특정 약관의 버전 이력 |

### 동의 처리

| Method | URL | 설명 |
|---|---|---|
| `POST` | `/consent` | 약관 동의 (최초 동의 / 재동의) |
| `POST` | `/consent/withdraw` | 약관 동의 철회 |

### 이력 조회

| Method | URL | 설명 |
|---|---|---|
| `GET` | `/consent/history?userId={id}` | 사용자 동의 이력 전체 |

---

## API 사용 예시

### 1. 현행 약관 목록 조회

```bash
GET /terms
```

```json
{
  "success": true,
  "data": [
    {
      "termsId": 1,
      "termsCode": "TERMS_OF_SERVICE",
      "termsName": "서비스 이용약관",
      "mandatory": true,
      "currentVersion": "v2.0",
      "content": "서비스 이용약관 내용 (v2.0)",
      "effectiveDate": "2025-01-01T00:00:00"
    }
  ]
}
```

### 2. 약관 동의

```bash
POST /consent
Content-Type: application/json

{
  "userId": 1,
  "termsId": 1
}
```

```json
{
  "success": true,
  "message": "약관에 동의하였습니다.",
  "data": {
    "consentHistoryId": 1,
    "userId": 1,
    "termsId": 1,
    "termsName": "서비스 이용약관",
    "version": "v2.0",
    "status": "AGREED",
    "consentedAt": "2025-06-01T10:00:00"
  }
}
```

### 3. 동의 이력 조회

```bash
GET /consent/history?userId=1
```

```json
{
  "success": true,
  "data": {
    "userId": 1,
    "totalCount": 3,
    "histories": [
      {
        "historyId": 3,
        "termsCode": "MARKETING_CONSENT",
        "termsName": "마케팅 수신 동의",
        "mandatory": false,
        "version": "v1.0",
        "status": "WITHDRAWN",
        "consentedAt": "2025-06-01T10:05:00"
      },
      {
        "historyId": 2,
        "termsCode": "MARKETING_CONSENT",
        "termsName": "마케팅 수신 동의",
        "mandatory": false,
        "version": "v1.0",
        "status": "AGREED",
        "consentedAt": "2025-06-01T10:02:00"
      }
    ]
  }
}
```

---

## 비즈니스 규칙 요약

| 상황 | 처리 |
|---|---|
| 최초 동의 | `/consent` POST → AGREED 이력 INSERT |
| 이미 동의한 현행 버전에 재요청 | 400 오류 (중복 방지) |
| 철회 후 재동의 | `/consent` POST 재호출 가능 (허용) |
| 새 버전 출시 후 재동의 | `/consent` POST 재호출 가능 (다른 버전 ID) |
| 필수 약관 철회 시도 | 400 오류 |
| 동의 이력 없이 철회 시도 | 400 오류 |

---

## 기술 스택

| 항목 | 내용 |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| ORM | Spring Data JPA / Hibernate |
| DB | H2 In-Memory (데모용) |
| Build | Maven |
| Test | JUnit 5, Spring Boot Test |

---

## 실행 방법

```bash
# 빌드 및 실행
./mvnw spring-boot:run

# 테스트 실행
./mvnw test

# H2 콘솔 (DB 직접 확인)
http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:consentdb
# Username: sa / Password: (공백)
```

> 애플리케이션 시작 시 `data.sql`에 정의된 샘플 데이터(사용자 2명, 약관 3종, 버전 6개)가 자동으로 삽입됩니다.

