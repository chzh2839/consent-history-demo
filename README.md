# 약관 버전 관리 & 동의 이력 추적 시스템

---

## 목차

1. [프로젝트 소개](#1-프로젝트-소개)
2. [해결한 문제](#2-해결한-문제)
3. [기술 스택](#3-기술-스택)
4. [아키텍처 개요](#4-아키텍처-개요)
5. [기술적 선택 근거](#5-기술적-선택-근거)
6. [ERD](#6-erd)
7. [API 명세](#7-api-명세)
8. [동의 처리 흐름 상세](#8-동의-처리-흐름-상세)
9. [트러블슈팅](#9-트러블슈팅)
10. [한계 및 개선 방향](#10-한계-및-개선-방향)
11. [테스트](#11-테스트)
12. [실행 방법](#12-실행-방법)

---

## 1. 프로젝트 소개

약관 동의 이력을 관리하는 서버다. 회원가입할 때 이용약관, 개인정보처리방침, 마케팅 수신동의 같은 약관에 동의를 받는데, 이 약관이 나중에 개정되면 "그때 동의한 내용"과 "지금 화면에 보이는 내용"이 달라지는 문제가 생긴다.

약관 내용을 그냥 UPDATE로 고치면 사용자가 어떤 버전에 동의했는지 증명할 방법이 없어진다. 분쟁이 생겼을 때 "동의 당시 약관에는 이런 조항이 없었다"는 주장을 반박할 근거가 사라지는 거다. 이 문제를 버전 불변성과 append-only 이력 설계로 어떻게 풀지 구체적으로 구현해보고 싶어서 만들었다.

---

## 2. 해결한 문제

| 과제 | 구현 방식 |
|------|-----------|
| 동의 시점의 약관 내용을 그대로 재현 | `terms_version`을 버전별로 INSERT, `consent_history`에 버전 ID 저장 |
| 약관 개정 후 재동의 유도 | 최신 이력의 버전과 현행 활성 버전을 비교해 다르면 재동의 허용 |
| 동일 버전 중복 동의 차단 | 최신 이력이 현행 버전 + AGREED 상태일 때만 예외 발생 |
| 철회 후 재동의 | 상태값(AGREED/WITHDRAWN) 기반 판단이라 별도 분기 없이 자연스럽게 허용 |
| 필수 약관 철회 방지 | `terms.isMandatory` 체크 |
| 감사·분쟁 대응용 이력 보존 | `consent_history`를 UPDATE/DELETE 없이 append-only로 설계 |
| 스키마 생성 전에 시드 데이터가 먼저 실행되는 문제 | `defer-datasource-initialization`으로 초기화 순서 제어 |
| 동일 유저의 동시 동의/철회 요청으로 인한 중복 이력 | `agree`/`withdraw` 트랜잭션 앞단에서 `User` 행에 비관적 락(`SELECT ... FOR UPDATE`) 적용 |
| 이력이 쌓일수록 무한정 커지는 응답 크기 | `/consent/history`에 Spring Data `Pageable`/`Page` 기반 페이지네이션 적용 |
| 특정 약관·상태·기간으로 이력을 좁혀 조회 | `termsId`/`status`/`from`/`to`를 모두 선택 파라미터로 받아 JPQL에서 null 여부로 조건 적용 |

---

## 3. 기술 스택

```
Java 17  /  Spring Boot 3.2
Spring Data JPA  /  Hibernate 6
H2 (인메모리, 데모용)
Bean Validation (spring-boot-starter-validation)
Lombok
JUnit 5  /  AssertJ  /  Spring Boot Test
Maven
```

---

## 4. 아키텍처 개요

```
클라이언트
    │
    ├── GET  /terms, /terms/{id}/versions
    ├── POST /consent, /consent/withdraw     ──►  ConsentController
    └── GET  /consent/history
                                                        │
                                                 ConsentService
                                                        │
                    ┌───────────────┬───────────────────┼───────────────────┐
              UserRepository  TermsRepository   TermsVersionRepository  ConsentHistoryRepository
                    │               │                    │                     │
                    └───────────────┴────────────────────┴─────────────────────┘
                                                        │
                                                       JPA
                                                        │
                                               H2 (in-memory)
```

컨트롤러 → 서비스 → 리포지토리로 이어지는 단순한 3계층 구조다. 예외는 서비스에서 던지고, `GlobalExceptionHandler`(`@RestControllerAdvice`) 한 곳에서 상태 코드와 응답 형태를 통일해서 처리한다.

---

## 5. 기술적 선택 근거

### 왜 약관 개정을 UPDATE가 아니라 INSERT로 처리했나

약관 내용을 직접 UPDATE하면 예전 내용이 사라진다. 사용자가 v1.0에 동의했는데 지금 조회하면 v2.0 내용이 보이게 되는 거다. `terms_version` 테이블에 버전마다 새 레코드를 추가하고, 기존 버전은 `is_active`만 false로 바꾼다. `consent_history`에는 어떤 버전에 동의했는지 `terms_version_id`로 정확히 남긴다. 그래서 "이 사용자가 동의한 시점의 약관 내용이 뭐였나"를 언제든 정확히 재현할 수 있다.

### 왜 동의/철회 이력도 UPDATE 없이 계속 쌓기만 하나

처음엔 `users` 테이블에 `is_agreed` 같은 boolean 컬럼 하나 두고 값만 바꾸는 방식도 생각해봤다. 근데 이러면 "언제 동의했다가 언제 철회했는지"가 사라진다. 감사나 분쟁 대응 상황에서는 최종 상태보다 변경 이력 자체가 더 중요한 경우가 많다. 그래서 `consent_history`를 append-only 로그로 설계했다. 동의든 철회든 새 행을 추가만 하고, 현재 상태는 가장 최근 행의 `status`로 판단한다.

### 중복 동의 차단 조건에 버전을 같이 비교하는 이유

처음엔 "이미 AGREED 상태면 무조건 막는다"로 짰다. 근데 이렇게 하면 철회했다가 다시 동의하려는 정상적인 흐름도, 약관이 개정돼서 재동의를 유도해야 하는 흐름도 전부 막힌다. 그래서 조건을 `최신 이력의 버전 == 현행 버전 && 상태 == AGREED`로 바꿨다. 버전이 다르면(개정됨) 재동의를 허용하고, 상태가 WITHDRAWN이면 그것도 재동의를 허용한다. 실제로 막아야 하는 경우는 "지금 버전에 이미 동의한 상태에서 또 동의 요청이 오는" 딱 한 가지뿐이다.

### 철회 이력에도 철회 시점 기준 현행 버전을 기록하는 이유

철회할 때 원래 동의했던 버전이 아니라, 철회 시점의 활성 버전(`findActiveVersion(terms)`)을 참조해서 이력을 남긴다. 철회는 "그때 동의했던 특정 버전"에 대한 게 아니라 "지금 이 약관 자체"에 대한 거부 의사이기 때문이다. 사용자가 v1.0에 동의한 뒤 v2.0으로 개정됐고 그 상태에서 철회하면, 철회 이력은 v2.0을 기준으로 남는 게 감사 관점에서 더 정확하다고 판단했다.

### JPQL에 `LIMIT 1`을 직접 쓴 이유

"특정 유저의 특정 약관에 대한 가장 최근 이력 1건"을 가져와야 하는데, `Pageable`로 `findFirst...By...OrderByDesc` 형태를 쓰는 것보다 JPQL에 `ORDER BY ... LIMIT 1`을 직접 쓰는 게 쿼리 의도가 더 명확했다. Hibernate 6부터 HQL이 `LIMIT` 절을 지원해서 가능했다.

### 동시 요청 방어에 DB 유니크 제약 대신 `User` 행 비관적 락을 쓴 이유

`agree()`/`withdraw()`는 "최신 이력 조회 → 판단 → INSERT" 흐름이라, 같은 유저가 같은 약관에 동시에 요청을 보내면 두 트랜잭션이 모두 "중복 아님"으로 읽고 통과해 `AGREED` 이력이 중복 저장될 수 있었다. 처음엔 `consent_history`에 `(user_id, terms_version_id, status)` 유니크 제약을 거는 방법도 생각했는데, 이 스키마는 철회 후 약관 개정 없이 같은 버전으로 재동의하면 동일한 `(user_id, terms_version_id, AGREED)` 조합이 다시 발생할 수 있는 구조라, 유니크 제약을 걸면 정상적인 재동의 흐름까지 막아버린다.

그래서 `UserRepository.findByIdForUpdate()`로 `agree`/`withdraw` 트랜잭션 맨 앞에서 해당 유저의 `User` 행에 `SELECT ... FOR UPDATE`(`@Lock(PESSIMISTIC_WRITE)`)를 건다. 스키마 변경 없이, 같은 유저에 대한 동시 요청을 트랜잭션 단위로 직렬화할 수 있다.

두 번째 요청은 첫 번째 트랜잭션이 커밋될 때까지 대기했다가, 그 후에야 최신 이력을 조회하므로 항상 최신 상태를 보고 정확히 차단한다. 락은 "같은 유저 + 같은 약관"이 아니라 "같은 유저" 단위로 걸리는데, 별도의 락 전용 테이블을 추가하는 것보다 훨씬 단순하고 이 데모 규모의 트래픽에서는 문제가 되지 않는다.

락을 무한 대기하지 않도록 `jakarta.persistence.lock.timeout`을 3초로 설정했고, 타임아웃 시 `PessimisticLockingFailureException`을 `GlobalExceptionHandler`가 409로 매핑한다.

### `/consent/history`에 `Pageable`/`Page`와 별도 countQuery를 쓴 이유

이력이 쌓일수록 `/consent/history`가 매번 전체 행을 반환하는 게 문제였다. Spring Data JPA가
이미 제공하는 `Pageable`/`Page` 추상화를 그대로 썼다 — 컨트롤러가 `page`/`size`/`sort` 쿼리
파라미터를 자동으로 `Pageable`에 바인딩해주고, 별도 설정 없이 스프링 부트가 지원한다.

다만 `JOIN FETCH`가 들어간 쿼리(`ConsentHistoryRepository.search`)에 `Pageable`을 그대로 쓰면,
Spring Data가 페이지 카운트용 쿼리를 자동으로 유도하다가 `JOIN FETCH` 구문 때문에 실패하거나
비효율적인 쿼리를 만들 수 있다. 그래서 `@Query`의 `countQuery` 속성에 `JOIN FETCH` 없는 단순
`COUNT` 쿼리를 직접 지정했다. `JOIN FETCH` 대상이 전부 `ManyToOne`이라 "컬렉션 fetch join은
페이지네이션과 함께 쓸 수 없다"는 Hibernate 제약에는 걸리지 않는다. 전체 목록이 그대로 필요한
내부 용도(동시성 테스트 정리 등)를 위해 페이지네이션 없는 `findAllByUserIdOrderByConsentedAtDesc`도
별도로 남겨뒀다.

정렬 기준은 JPQL에 직접 `ORDER BY`를 넣지 않고 컨트롤러의 `@PageableDefault(sort = "consentedAt", direction = DESC)`에 맡겼다. 그래야 클라이언트가 `sort` 파라미터로 정렬 기준을 바꿀 수 있고, JPQL에 하드코딩된 `ORDER BY`와 `Pageable`의 `Sort`가 충돌할 일도 없다.

### 검색 조건(termsId/status/from/to)

`from`/`to`는 `LocalDate`(날짜)로 받아 서비스에서 `LocalDateTime`(하루의 시작/끝)으로 변환한다.
`consentedAt`은 타임스탬프라 클라이언트가 매번 시각까지 입력하게 하는 건 불편하고, "그날 하루"
단위로 조회하는 게 실제 사용 패턴에 가깝다고 판단했다.

`from`이 `to`보다 늦은 경우는 서비스에서 `ConsentException`으로 막아 400을 반환한다.

`status`처럼 enum 파라미터에 잘못된 문자열이 들어오면 스프링이 컨트롤러 진입 전에 `MethodArgumentTypeMismatchException`을 던지는데, 이걸 그대로 두면
`GlobalExceptionHandler`의 최종 `Exception` 핸들러에 걸려 500으로 응답해버린다. 그래서 이 예외도
400으로 매핑하는 핸들러를 추가했다.

### 응답 DTO는 `@Builder` + 정적 `from()`, 요청 DTO는 필드만 있는 이유

응답 DTO(`ConsentResponse`)는 엔티티 → DTO 변환 로직을 `TermsInfo.from(entity)`처럼 각 DTO 안에 캡슐화했다. 서비스 코드에 매핑 로직이 흩어지는 걸 막기 위해서다. 요청 DTO(`ConsentRequest`)는 반대로 `@NotNull` 필드만 있고 생성자나 setter가 없다. Jackson이 리플렉션으로 필드에 직접 값을 채우기 때문에 실제 API 호출에는 문제가 없는데, 서비스 레이어를 직접 호출하는 단위 테스트에서는 값을 넣을 방법이 없어서 별도 처리가 필요했다. (9번 트러블슈팅 참고)

---

## 6. ERD

```
┌───────────────────┐       ┌────────────────────────┐       ┌──────────────────────┐
│       USERS       │       │      TERMS_VERSION     │       │         TERMS        │
├───────────────────┤       ├────────────────────────┤       ├──────────────────────┤
│ id            PK  │       │ id                 PK  │       │ id               PK  │
│ username      UQ  │       │ terms_id           FK ─┼──────►│ terms_code       UQ  │
│ email         UQ  │       │ version                │       │ terms_name           │
│ created_at        │       │ content      (TEXT)    │       │ is_mandatory         │
└──────────┬────────┘       │ is_active              │       │ created_at           │
           │                │ effective_date         │       └──────────────────────┘
           │                │ created_at             │
           │                └────────────┬───────────┘
           │                             │
           │      ┌──────────────────────┘
           ▼      ▼
┌─────────────────────────────┐
│       CONSENT_HISTORY       │
├─────────────────────────────┤
│ id                      PK  │
│ user_id                 FK  │
│ terms_version_id        FK  │  ← 동의/철회 시점의 버전을 정확히 기록
│ status  (AGREED/WITHDRAWN)  │
│ consented_at                │
└─────────────────────────────┘

UNIQUE (terms_id, version)  on TERMS_VERSION
```

| 테이블 | 역할 |
|---|---|
| `USERS` | 사용자 정보 |
| `TERMS` | 약관 종류 마스터 (서비스 이용약관, 개인정보 처리방침 등) |
| `TERMS_VERSION` | 약관의 버전별 내용 (INSERT-only, 개정 시 새 행 추가) |
| `CONSENT_HISTORY` | 동의·철회 이력 (INSERT-only, 현재 상태는 최신 행 기준) |

---

## 7. API 명세

### 약관 조회

| Method | URL | 설명 |
|---|---|---|
| `GET` | `/terms` | 현행 활성 약관 전체 목록 |
| `GET` | `/terms/{termsId}/versions` | 특정 약관의 버전 이력 |

### 동의 처리

| Method | URL | 설명 |
|---|---|---|
| `POST` | `/consent` | 약관 동의 (최초 동의 / 재동의 공용) |
| `POST` | `/consent/withdraw` | 약관 동의 철회 |

### 이력 조회

| Method | URL | 설명 |
|---|---|---|
| `GET` | `/consent/history?userId={id}&termsId={termsId}&status={AGREED\|WITHDRAWN}&from={yyyy-MM-dd}&to={yyyy-MM-dd}&page={page}&size={size}` | 사용자 동의 이력 검색 (페이지네이션, 기본값 `page=0`, `size=20`, `consentedAt DESC` 정렬) |

`userId` 외 모든 파라미터는 선택이다.

| 파라미터 | 설명 |
|---|---|
| `termsId` | 특정 약관에 대한 이력만 조회 |
| `status` | `AGREED` 또는 `WITHDRAWN` 상태의 이력만 조회 |
| `from` / `to` | `yyyy-MM-dd` 형식의 조회 기간(둘 다 포함, `from`은 00:00:00, `to`는 23:59:59.999999999 기준). `from`이 `to`보다 늦으면 400 에러 |
| `page` / `size` / `sort` | Spring `Pageable` 표준 파라미터 |

### 응답 예시 — 약관 동의

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
    "consentedAt": "2026-07-01T10:00:00"
  }
}
```

### 응답 예시 — 동의 이력 조회

```bash
GET /consent/history?userId=1&status=WITHDRAWN&from=2026-07-01&to=2026-07-01&page=0&size=2
```

```json
{
  "success": true,
  "data": {
    "userId": 1,
    "histories": [
      {
        "historyId": 2,
        "termsCode": "MARKETING_CONSENT",
        "termsName": "마케팅 수신 동의",
        "mandatory": false,
        "version": "v1.0",
        "status": "WITHDRAWN",
        "consentedAt": "2026-07-01T10:05:00"
      }
    ],
    "page": 0,
    "size": 2,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

`termsId`/`status`/`from`/`to`는 자유롭게 조합할 수 있다(예: 특정 약관을 특정 기간 안에서만 조회). `status`에 잘못된 값을 넘기거나 `from`이 `to`보다 늦으면 400으로 응답한다.

`page`는 0부터 시작한다(Spring `Pageable` 기본 관례). `page`/`size` 쿼리 파라미터를 생략하면 기본값(`page=0`, `size=20`)이 적용되고, `sort` 파라미터로 정렬 기준도 바꿀 수 있다(예: `sort=consentedAt,asc`).

모든 응답은 `ApiResponse<T>`(`success`, `message`, `data`, `timestamp`)로 감싸서 반환한다. 에러는 `GlobalExceptionHandler`가 `ResourceNotFoundException` → 404, `ConsentException` → 400, 검증 실패 → 400, 그 외 → 500으로 매핑한다.

---

## 8. 동의 처리 흐름 상세

### 8-1. 최초 동의

```
클라이언트              서버(ConsentService)             DB
    │                        │                         │
    │ POST /consent          │                         │
    │ {userId, termsId}      │                         │
    │ ───────────────────────►                         │
    │                        │── 활성 버전 조회 ───────► │
    │                        │◄── TermsVersion(active)─│
    │                        │                         │
    │                        │── 최신 이력 조회 ─────────►│
    │                        │◄── 없음(Optional.empty) ─│
    │                        │                         │
    │                        │── INSERT consent_history ►│
    │                        │   (status=AGREED)       │
    │◄── 200 OK, Result ──────                         │
```

### 8-2. 동일 버전 중복 동의 차단

```
클라이언트              서버                            DB
    │                        │                        │
    │ POST /consent (동일 termsId, 두 번째 호출)         │
    │ ───────────────────────►                        │
    │                        │── 최신 이력 조회 ────────►│
    │                        │◄── ConsentHistory       │
    │                        │    (version=v2.0,       │
    │                        │     status=AGREED)      │
    │                        │                         │
    │             latest.version == active.version  && │
    │             latest.status  == AGREED             │
    │             → 둘 다 참이면 ConsentException        │
    │◄── 400, "이미 ... 동의하셨습니다" ──                │
```

### 8-3. 철회 → 약관 개정 → 재동의

```
[이력 없음]
     │ POST /consent   (v1.0 활성)
     ▼
[AGREED, v1.0]
     │
     │ 약관 개정 → v2.0 활성화
     │
     │ POST /consent/withdraw
     ▼
[WITHDRAWN, v2.0]   ← 철회 시점 기준 현행 버전으로 기록
     │
     │ POST /consent   (재동의)
     │   latest.status = WITHDRAWN → 통과
     ▼
[AGREED, v2.0]

※ v1.0 AGREED, v2.0 WITHDRAWN 이력은 그대로 남아있다.
  /consent/history 조회 시 3개 행이 전부 보인다.
```

위 세 시나리오는 각각 `duplicateAgree`, `withdrawOptionalTerms`, `reAgreeAfterWithdraw` 테스트로 검증되어 있다.

---

## 9. 트러블슈팅

### data.sql이 테이블 생성보다 먼저 실행돼서 빌드가 깨짐

`mvn test`를 돌리면 `data.sql`의 INSERT 문에서 `Table "USERS" not found` 에러가 났다. 이 프로젝트는 `schema.sql` 없이 Hibernate의 `ddl-auto: create-drop`으로 스키마를 만드는 구조인데, Spring Boot는 기본적으로 데이터소스 초기화 스크립트(`data.sql`)를 JPA `EntityManagerFactory`가 만들어지기 전에 실행한다. 그래서 테이블이 아직 없는 상태에서 INSERT가 실행돼 실패했다.

`spring.jpa.defer-datasource-initialization: true`를 `application.yml`에 추가해서 Hibernate가 스키마를 먼저 만들고 나서 `data.sql`이 실행되도록 순서를 바꿨다. `schema.sql`로 스키마를 따로 관리하지 않고 JPA 엔티티에서 스키마를 뽑아 쓰는 구조라면 반드시 챙겨야 하는 옵션이다.

### 재동의 테스트를 짜다가 중복 체크 조건을 다시 잡음

`reAgreeAfterWithdraw` 테스트를 작성하면서, 처음 짰던 "상태만 보고 막는" 로직으로는 철회 후 재동의가 항상 예외를 던진다는 걸 확인했다. 상태를 UPDATE하는 게 아니라 새 행을 추가하는 구조라 최신 행 하나만 보면 되는데, 그 최신 행의 status만 체크하고 버전은 안 보고 있었던 게 원인이었다. 조건에 버전 비교를 추가하고 나서야 중복 동의 차단, 철회 후 재동의, 신규 버전 재동의 세 가지 케이스가 테스트에서 전부 의도대로 갈렸다.

### 테스트에서 요청 DTO에 값을 채울 방법이 없었음

`ConsentRequest.Agree`는 Jackson 역직렬화만 고려해서 `userId`, `termsId` 필드에 `@NotNull`만 붙이고 생성자나 setter를 만들지 않았다. 실제 API 호출에서는 Jackson이 리플렉션으로 처리해주니까 문제가 없는데, 서비스 레이어를 직접 호출하는 단위 테스트에서는 인스턴스를 만든 다음 값을 채울 방법이 없었다. 결국 테스트 클래스에 `setField()` 리플렉션 헬퍼를 만들어서 우회했다. 요청 DTO에도 `@Builder`를 붙이면 해결되긴 하는데, 실제 API 흐름에서는 쓰이지 않을 코드가 늘어나는 셈이라 일단 이 상태로 남겨뒀다. (10번 개선 방향에도 정리했다.)

---

## 10. 한계 및 개선 방향

**신규 약관 버전 발행 API 없음**
- `TermsVersion`에 `deactivate()` 메서드는 만들어뒀는데, 실제로 이 메서드를 호출해서 새 버전을 발행하는 서비스 로직·API가 없다. 지금은 `data.sql`로 버전 데이터를 미리 심어두는 방식으로만 동작한다. "기존 활성 버전 deactivate + 신규 버전 insert"를 한 트랜잭션으로 묶는 API를 추가해야 실제 운영에서 쓸 수 있다.

**인증·인가 없음**
- `userId`를 클라이언트가 그대로 파라미터로 넘긴다. 데모 목적이라 의도적으로 뺐지만, 실서비스라면 로그인한 사용자의 토큰에서 `userId`를 뽑아 쓰도록 바꿔야 한다.

**H2 인메모리 + create-drop**
- 재시작할 때마다 데이터가 초기화되는 데모용 구성이다. 실제로 쓰려면 PostgreSQL 같은 RDB로 바꾸고, 스키마는 `ddl-auto` 대신 Flyway나 Liquibase로 버전 관리하는 게 맞다.

---

## 11. 테스트

전부 `@SpringBootTest` + `@Transactional`로 구성해서, 각 테스트가 끝나면 롤백돼 `data.sql`로 심어둔 초기 데이터가 깨지지 않는다.

**ConsentServiceTest** (12케이스)
- `getActiveTermsList` — 활성 약관 목록이 비어있지 않고, 전부 `currentVersion`을 갖고 있는지
- `agreeTerms` — 선택 약관(마케팅 수신동의)에 동의하면 결과 상태가 AGREED로 오는지
- `duplicateAgree` — 같은 약관에 두 번 연속 동의하면 두 번째 호출에서 예외가 나는지
- `cannotWithdrawMandatoryTerms` — 필수 약관(서비스 이용약관)은 동의한 뒤에도 철회 시도 시 예외가 나는지
- `withdrawOptionalTerms` — 선택 약관은 동의 후 철회하면 상태가 WITHDRAWN으로 바뀌는지
- `reAgreeAfterWithdraw` — 동의 → 철회 → 재동의 흐름이 전부 정상 처리되는지
- `getConsentHistory` — 여러 약관에 동의한 뒤 이력 조회 시 전체 개수와 사용자 ID가 맞는지
- `getConsentHistory_pagination` — `size`만큼만 잘려서 반환되고 `hasNext`가 올바르게 계산되는지
- `getConsentHistory_filterByTermsId` — `termsId`로 필터링하면 해당 약관 이력만 나오는지
- `getConsentHistory_filterByStatus` — `status`로 필터링하면 해당 상태의 이력만 나오는지
- `getConsentHistory_filterByPeriod` — `from`/`to` 기간 밖의 이력은 제외되고, 기간 안의 이력은 포함되는지
- `getConsentHistory_invalidPeriod` — `from`이 `to`보다 늦으면 예외가 나는지

**ConsentConcurrencyTest** (1케이스)
- `concurrentAgree_onlyOneAgreedHistoryIsSaved` — 같은 유저 + 같은 약관에 `ExecutorService`로 두 스레드를 동시에 출발시켜 `agree()`를 호출했을 때, 하나만 성공하고 `AGREED` 이력이 정확히 1건만 저장되는지 검증. 스레드별로 실제 커넥션/트랜잭션이 필요해 `@Transactional` 롤백 대신 `@AfterEach`에서 직접 데이터를 정리한다.

---

## 12. 실행 방법

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

애플리케이션 시작 시 `data.sql`에 정의된 샘플 데이터(사용자 2명, 약관 3종, 버전 6개)가 자동으로 삽입된다.
