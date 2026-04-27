# Liveklass 과제

Spring Boot와 PostgreSQL로 구현한 Liveklass BE-A 과제용 수강 신청 시스템입니다.

강의 생성/조회/상태 변경, 수강 신청 생성/확정/취소/내역 조회가 구현되어 있습니다. 정원이 찬 강의에 신청하면 자동으로 대기열(waitlist)에 등록되고, 선행 신청이 취소될 때 가장 오래된 대기자가 자동으로 `PENDING`으로 승격됩니다. 스키마는 Flyway로 관리하고, 수강 신청 관련 동시성은 비관적 락과 데이터베이스 제약으로 보호합니다. 테스트는 단위 테스트, MVC 테스트, 통합 테스트, 동시성 테스트까지 포함합니다.

## 빠른 시작

```bash
# 1. PostgreSQL 실행
docker compose -f docker/postgres-compose.yml up -d

# 2. 애플리케이션 실행 (Linux/macOS/Git Bash)
./mvnw spring-boot:run
# Windows: .\mvnw.cmd spring-boot:run

# 3. 동작 확인
curl http://localhost:8080/classes

# 4. 테스트 실행 (로컬 DB 불필요 — Testcontainers 사용)
./mvnw test
```

기본 URL: `http://localhost:8080`

## 주요 기능

### 강의 관리

- 강의 생성 — 제목, 설명, 가격, 정원, 기간을 지정해 강의를 만듭니다.
- 강의 목록/상세 조회 — 인증 없이 접근할 수 있습니다.
- 강의 상태 변경 — `DRAFT → OPEN → CLOSED` 단방향 전이, 강의 생성자(소유자)만 수행 가능합니다.

### 수강 신청 관리

- 수강 신청 생성 — `OPEN` 상태의 강의에 한해 `STUDENT`가 신청할 수 있습니다. 정원이 찬 경우 `WAITLISTED` 상태로 대기열에 등록됩니다. 중복 신청(활성 신청이 이미 있는 경우)은 즉시 거부됩니다.
- 대기열 자동 승격 — `CONFIRMED` 또는 `PENDING` 신청이 취소되면, 같은 강의의 가장 오래된 `WAITLISTED` 신청이 자동으로 `PENDING`으로 승격됩니다(선착순).
- 수강 신청 확정 — 해당 강의의 소유 `CREATOR`만 `PENDING` → `CONFIRMED`로 바꿀 수 있습니다.
- 수강 신청 취소 — 신청자 본인 또는 해당 강의의 소유 `CREATOR`가 취소할 수 있습니다.
- 내 신청 목록 조회 — 로그인한 사용자의 전체 신청 이력을 최신순으로 반환합니다. `page`/`size` 쿼리 파라미터로 페이지네이션을 지원합니다(기본값: `page=0`, `size=20`).

### 동시성 안전성

- 수강 신청 생성 시 강의 row에 `PESSIMISTIC_WRITE` 락을 걸어 정원 초과와 waitlist 등록을 직렬화합니다.
- 확정/취소 시 enrollment row에 `PESSIMISTIC_WRITE` 락을 걸어 상태 충돌을 방지합니다.
- DB 부분 유니크 인덱스로 중복 활성 신청(`PENDING`/`CONFIRMED`/`WAITLISTED`)을 추가 차단합니다.
- 락 대기 시간 초과 시 `LOCK_TIMEOUT(409)` 에러를 반환합니다.

### 보안 및 권한

- 헤더 기반 인증 — `X-User-Id` / `X-User-Role` 헤더로 사용자를 식별합니다.
- 역할 기반 인가 — `CREATOR` / `STUDENT` 역할에 따라 API 접근을 제한합니다.
- 소유권 검사 — 강의 상태 변경, 수강 신청 확정, 강의별 수강생 목록 조회는 해당 강의의 생성자만 수행할 수 있고, 수강 신청 취소는 신청자 본인 또는 해당 강의의 생성자만 수행할 수 있습니다.

### 안정성 및 품질

- Bean Validation + 전역 예외 처리(`GlobalExceptionHandler`)로 일관된 에러 응답을 반환합니다.
- Flyway 마이그레이션으로 스키마를 코드로 관리합니다.
- 단위 테스트, MockMvc 테스트, Testcontainers 통합 테스트, 동시성 테스트 154건이 모두 통과합니다.

## 기술 스택

| 영역    | 기술                                               | 사용 목적                                     |
| ------- | -------------------------------------------------- | --------------------------------------------- |
| Runtime | Java 21, Spring Boot 4.0.5                         | 애플리케이션 실행과 DI, 웹 계층 구성          |
| Web     | Spring Web MVC, Bean Validation                    | REST API 구현, 요청 검증                      |
| Data    | Spring Data JPA, PostgreSQL 16, Flyway             | 영속성, 트랜잭션, 스키마 마이그레이션         |
| Test    | JUnit 5, Spring Boot Test, MockMvc, Testcontainers | 단위 테스트, API 테스트, PostgreSQL 통합 검증 |
| Build   | Maven Wrapper                                      | 빌드와 테스트 실행 표준화                     |

## 프로젝트 구조

```
src/main/java/com/example/liveklass/
├── LiveklassApplication.java
├── common/
│   ├── config/
│   │   ├── CurrentUser.java          # @CurrentUser 파라미터 애노테이션
│   │   ├── RequestUser.java          # 인증된 사용자 정보 record
│   │   ├── RequestUserResolver.java  # X-User-Id/Role 헤더 파싱
│   │   ├── UserRole.java             # CREATOR / STUDENT enum
│   │   └── WebMvcConfig.java         # ArgumentResolver 등록
│   └── error/
│       ├── BusinessException.java    # 도메인 예외 기본 클래스
│       ├── ErrorCode.java            # 에러 코드 + HTTP 상태 enum
│       ├── ErrorResponse.java        # 에러 응답 DTO
│       └── GlobalExceptionHandler.java
├── course/
│   ├── controller/CourseController.java
│   ├── dto/                          # CreateCourseRequest, CourseDetailResponse 등
│   ├── entity/Course.java
│   ├── enums/CourseStatus.java       # DRAFT / OPEN / CLOSED
│   ├── repository/CourseRepository.java
│   └── service/CourseService.java + CourseServiceImpl.java
└── enrollment/
    ├── controller/EnrollmentController.java
    ├── dto/                          # CreateEnrollmentRequest, EnrollmentResponse, PagedEnrollmentResponse
    ├── entity/Enrollment.java
    ├── enums/EnrollmentStatus.java   # PENDING / CONFIRMED / CANCELLED / WAITLISTED
    ├── repository/EnrollmentRepository.java
    └── service/EnrollmentService.java + EnrollmentServiceImpl.java

src/main/resources/
├── application.yml
├── db/migration/V1__initial.sql     # Flyway 초기 스키마
└── db/migration/V2__waitlist.sql    # WAITLISTED 상태 + 부분 유니크 인덱스 확장
```

## 인증/인가 방식

과제 범위에 맞춰 별도의 로그인 시스템은 붙이지 않았습니다. 대신 보호된 API는 아래 두 헤더를 사용합니다.

- `X-User-Id`: 숫자형 사용자 ID
- `X-User-Role`: `CREATOR` 또는 `STUDENT`

예시:

```http
X-User-Id: 10
X-User-Role: STUDENT
```

헤더가 없거나 형식이 잘못되면 `401 UNAUTHORIZED`를 반환합니다.

## 실행 방법

### 1. 준비물

- Java 21
- Docker Desktop 또는 Docker Engine

### 2. PostgreSQL 실행

```bash
docker compose -f docker/postgres-compose.yml up -d
```

기본 로컬 DB 정보:

- URL: `jdbc:postgresql://localhost:5432/liveklass`
- Username: `liveklass`
- Password: `liveklass`

### 3. 애플리케이션 실행

Linux/macOS/Git Bash:

```bash
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Unix 계열 환경에서는 보조 스크립트도 사용할 수 있습니다.

```bash
./run-dev.sh
```

애플리케이션 시작 시 `src/main/resources/db/migration/V1__initial.sql`이 자동 적용됩니다.

## 테스트 실행 방법

Linux/macOS/Git Bash:

```bash
./mvnw test
```

Windows PowerShell:

```powershell
.\mvnw.cmd test
```

참고:

- 통합 테스트는 Testcontainers PostgreSQL(`postgres:16`)을 사용합니다.
- 테스트 실행을 위해 로컬 PostgreSQL을 별도로 띄울 필요는 없습니다.
- 2026-04-27 기준 결과: 테스트 154건, 실패 0건, 에러 0건

## 요구사항 해석과 가정

- 외부 API 경로는 `/classes`를 사용하고, 내부 패키지명은 `course`를 사용합니다.
- 강의 생성과 강의 상태 변경은 `CREATOR`만 수행할 수 있습니다.
- 수강 신청 생성은 `STUDENT`만 수행할 수 있습니다.
- 수강 확정은 외부 결제 연동 없이 내부 상태 변경으로 단순화했습니다.
- `PENDING`과 `CONFIRMED`는 모두 좌석을 점유하는 활성 상태로 봅니다.
- `WAITLISTED`는 좌석은 점유하지 않지만, 활성 신청으로 취급하여 중복 신청을 방지합니다.
- 같은 학생은 같은 강의에 대해 `PENDING`, `CONFIRMED`, `WAITLISTED` 상태의 신청을 하나만 가질 수 있습니다.
- 강의 상태 전이는 `DRAFT -> OPEN -> CLOSED`만 허용합니다.
- 강의 상태 변경과 수강 신청 확정은 해당 강의를 직접 생성한 `CREATOR`만 수행할 수 있습니다.

## API 요약

현재 프로젝트가 제공하는 구현 API는 아래 9개가 전부입니다.

### 공통 규칙

- 시간 필드는 ISO-8601 datetime 문자열로 반환됩니다.
- `POST`, `PATCH` 기반 상태 변경 요청은 멱등성을 보장하지 않습니다.
- 동일 요청을 재시도할 경우, 상태 변경 요청은 현재 처리 결과에 따라 `DUPLICATE_ENROLLMENT(409)` 또는 `INVALID_STATE_TRANSITION(409)`으로 응답할 수 있습니다.

### 강의 API

| 메서드  | 경로                              | 권한      | 설명                                  |
| ------- | --------------------------------- | --------- | ------------------------------------- |
| `POST`  | `/classes`                        | `CREATOR` | 강의 생성                             |
| `GET`   | `/classes`                        | 공개      | 강의 목록 조회                        |
| `GET`   | `/classes/{courseId}`             | 공개      | 강의 상세 조회                        |
| `PATCH` | `/classes/{courseId}/status`      | `CREATOR` | 강의 상태 변경 (소유자 전용)          |
| `GET`   | `/classes/{courseId}/enrollments` | `CREATOR` | 강의별 수강생 목록 조회 (소유자 전용) |

### 수강 신청 API

| 메서드 | 경로                                  | 권한                                      | 설명                                       |
| ------ | ------------------------------------- | ----------------------------------------- | ------------------------------------------ |
| `POST` | `/enrollments`                        | `STUDENT`                                 | 수강 신청 생성                             |
| `POST` | `/enrollments/{enrollmentId}/confirm` | `CREATOR`                                 | 수강 신청 확정                             |
| `POST` | `/enrollments/{enrollmentId}/cancel`  | 신청자 본인 또는 해당 강의 소유 `CREATOR` | 수강 신청 취소                             |
| `GET`  | `/enrollments/me`                     | 인증 필요                                 | 내 수강 신청 목록 조회 (`?page=0&size=20`) |

## 요청/응답 예시

아래 예시는 구현된 9개 API 전체를 각각 한 번씩 보여줍니다.

### 1. 강의 생성

요청:

```http
POST /classes
Content-Type: application/json
X-User-Id: 1
X-User-Role: CREATOR

{
  "title": "Math 101",
  "description": "Basic math course",
  "price": 50.00,
  "capacity": 10,
  "startDate": "2026-06-01",
  "endDate": "2026-06-30"
}
```

응답:

```json
{
  "courseId": 1,
  "creatorId": 1,
  "title": "Math 101",
  "description": "Basic math course",
  "price": 50.0,
  "capacity": 10,
  "startDate": "2026-06-01",
  "endDate": "2026-06-30",
  "status": "DRAFT",
  "activeEnrollmentCount": 0,
  "createdAt": "2026-04-27T01:00:00Z",
  "updatedAt": "2026-04-27T01:00:00Z"
}
```

### 2. 강의 오픈

요청:

```http
PATCH /classes/1/status
Content-Type: application/json
X-User-Id: 1
X-User-Role: CREATOR

{
  "status": "OPEN"
}
```

응답:

```json
{
  "courseId": 1,
  "creatorId": 1,
  "title": "Math 101",
  "description": "Basic math course",
  "price": 50.0,
  "capacity": 10,
  "startDate": "2026-06-01",
  "endDate": "2026-06-30",
  "status": "OPEN",
  "activeEnrollmentCount": 0,
  "createdAt": "2026-04-27T01:00:00Z",
  "updatedAt": "2026-04-27T01:01:00Z"
}
```

### 3. 강의 목록 조회

요청:

```http
GET /classes?status=OPEN
```

응답:

```json
[
  {
    "courseId": 1,
    "title": "Math 101",
    "description": "Basic math course",
    "price": 50.0,
    "capacity": 10,
    "startDate": "2026-06-01",
    "endDate": "2026-06-30",
    "status": "OPEN"
  }
]
```

### 4. 강의 상세 조회

요청:

```http
GET /classes/1
```

응답:

```json
{
  "courseId": 1,
  "creatorId": 1,
  "title": "Math 101",
  "description": "Basic math course",
  "price": 50.0,
  "capacity": 10,
  "startDate": "2026-06-01",
  "endDate": "2026-06-30",
  "status": "OPEN",
  "activeEnrollmentCount": 1,
  "createdAt": "2026-04-27T01:00:00Z",
  "updatedAt": "2026-04-27T01:01:00Z"
}
```

### 5. 수강 신청 생성

요청:

```http
POST /enrollments
Content-Type: application/json
X-User-Id: 10
X-User-Role: STUDENT

{
  "courseId": 1
}
```

응답:

```json
{
  "enrollmentId": 100,
  "courseId": 1,
  "studentId": 10,
  "status": "PENDING",
  "requestedAt": "2026-04-27T01:05:00Z",
  "updatedAt": "2026-04-27T01:05:00Z"
}
```

`WAITLISTED`도 정상적인 신청 결과로 간주하여 `200 OK`를 반환합니다. 클라이언트는 에러 분기 없이 동일한 응답 모델을 처리하고, 상태 값만 기준으로 후속 UX를 분기하면 됩니다.

### 6. 수강 신청 확정

요청:

```http
POST /enrollments/100/confirm
X-User-Id: 1
X-User-Role: CREATOR
```

응답:

```json
{
  "enrollmentId": 100,
  "courseId": 1,
  "studentId": 10,
  "status": "CONFIRMED",
  "requestedAt": "2026-04-27T01:05:00Z",
  "updatedAt": "2026-04-27T01:06:00Z"
}
```

### 7. 수강 신청 취소

요청:

```http
POST /enrollments/100/cancel
X-User-Id: 10
X-User-Role: STUDENT
```

응답:

```json
{
  "enrollmentId": 100,
  "courseId": 1,
  "studentId": 10,
  "status": "CANCELLED",
  "requestedAt": "2026-04-27T01:05:00Z",
  "updatedAt": "2026-04-27T01:07:00Z"
}
```

> 409 — `CANCELLATION_WINDOW_EXPIRED`: 학생 본인이 CONFIRMED 상태의 신청을 확정 후 7일이 지나 취소 요청한 경우.

### 8. 내 수강 신청 목록 조회

요청:

```http
GET /enrollments/me?page=0&size=20
X-User-Id: 10
X-User-Role: STUDENT
```

응답:

```json
{
  "content": [
    {
      "enrollmentId": 100,
      "courseId": 1,
      "studentId": 10,
      "status": "CONFIRMED",
      "requestedAt": "2026-04-27T01:05:00Z",
      "updatedAt": "2026-04-27T01:06:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "last": true
}
```

페이지네이션은 offset 기반으로 구현했습니다. 현재 과제 범위와 예상 데이터 규모에서는 충분하다고 판단했고, 대규모 데이터 환경으로 확장될 경우 cursor 기반 페이지네이션으로 전환할 수 있습니다.

### 9. 강의별 수강생 목록 조회 (크리에이터 전용)

요청:

```http
GET /classes/1/enrollments?page=0&size=20
X-User-Id: 1
X-User-Role: CREATOR
```

응답:

```json
{
  "content": [
    {
      "enrollmentId": 100,
      "studentId": 10,
      "status": "CONFIRMED",
      "requestedAt": "2026-04-27T01:05:00Z",
      "updatedAt": "2026-04-27T01:06:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "last": true
}
```

## 에러 응답 형식

모든 에러 응답은 같은 구조를 사용합니다.

```json
{
  "timestamp": "2026-04-27T01:10:00Z",
  "code": "DUPLICATE_ENROLLMENT",
  "message": "Active enrollment already exists.",
  "path": "/enrollments"
}
```

주요 에러 코드:

| 코드                          | HTTP 상태 | 설명                                                      |
| ----------------------------- | --------- | --------------------------------------------------------- |
| `BAD_REQUEST`                 | 400       | 입력값 오류                                               |
| `UNAUTHORIZED`                | 401       | 인증 헤더 누락 또는 형식 오류                             |
| `FORBIDDEN`                   | 403       | 권한 없음 (역할 불일치 또는 소유자 아님)                  |
| `NOT_FOUND`                   | 404       | 리소스 없음                                               |
| `INVALID_STATE_TRANSITION`    | 409       | 허용되지 않은 상태 전이                                   |
| `DUPLICATE_ENROLLMENT`        | 409       | 이미 활성 수강 신청이 존재함                              |
| `COURSE_NOT_OPEN`             | 409       | 모집 중이 아닌 강의에 신청 시도                           |
| `COURSE_FULL`                 | 409       | 정원 초과 (현재는 대기열 등록으로 대체되어 발생하지 않음) |
| `CANCELLATION_WINDOW_EXPIRED` | 409       | 취소 가능 기간 만료 (학생 본인, CONFIRMED 상태 한정)      |
| `LOCK_TIMEOUT`                | 409       | DB 락 대기 시간 초과, 재시도 필요                         |
| `INTERNAL_SERVER_ERROR`       | 500       | 서버 내부 오류                                            |

## 동시성 처리 방식

수강 신청 관련 처리에는 트랜잭션, 행 단위 락, 데이터베이스 제약을 함께 사용합니다.

### 수강 신청 생성

- 대상 강의를 `PESSIMISTIC_WRITE`로 잠급니다.
- 락 획득 후 강의 상태, 중복 활성 신청 여부(`PENDING`/`CONFIRMED`/`WAITLISTED`)를 다시 확인합니다.
- 좌석이 남아 있으면 `PENDING`으로 저장하고, 정원이 차 있으면 `WAITLISTED`로 저장합니다.
- DB에는 `(course_id, student_id)`에 대한 부분 유니크 인덱스를 두고, `PENDING`/`CONFIRMED`/`WAITLISTED` 중복을 막습니다.

### 대기열 자동 승격

- `cancelEnrollment` 트랜잭션 안에서, 취소 대상의 원래 상태가 `PENDING` 또는 `CONFIRMED`인 경우에 한해 해당 강의의 `WAITLISTED` 신청 중 `requested_at`이 가장 이른 건을 `PENDING`으로 승격합니다.
- `WAITLISTED` 신청 취소 시에는 승격을 수행하지 않습니다.

### 수강 신청 확정/취소

- 확정과 취소는 모두 대상 enrollment row를 `PESSIMISTIC_WRITE`로 조회합니다.
- 같은 신청에 대한 경쟁 상태 변경 요청을 직렬화해서 최종 상태가 뒤엉키지 않게 합니다.

### 락 대기 시간

- `application.yml`에서 JPA lock timeout을 `5000ms`로 설정했습니다.

### 테스트로 검증한 경쟁 상황

| 시나리오               | 설정                   | 성공                                      | 실패 응답                        |
| ---------------------- | ---------------------- | ----------------------------------------- | -------------------------------- |
| 마지막 좌석 경쟁       | 정원 1, 동시 10명 신청 | 전원 200 OK (1명 PENDING, 9명 WAITLISTED) | —                                |
| 같은 학생 중복 요청    | 동시 5건 신청          | 1건 성공                                  | `DUPLICATE_ENROLLMENT (409)`     |
| 확정 vs 취소 동시 요청 | 동시 2건               | 1건 성공                                  | `INVALID_STATE_TRANSITION (409)` |

### 실패 시나리오 및 처리 전략

- DB 락 획득 실패 시 `LOCK_TIMEOUT(409)`를 반환하고, 클라이언트 재시도를 유도합니다.
- 트랜잭션 중 예외가 발생하면 전체 롤백으로 부분 상태 불일치를 방지합니다.
- 중복 요청은 서비스 레벨 선검사와 DB 유니크 제약으로 이중 방어합니다.
- `confirm`과 `cancel`의 경합은 `PESSIMISTIC_WRITE`로 직렬화해 최종 상태를 하나로 수렴시킵니다.

## 데이터 모델

### 핵심 엔터티

| 엔터티       | 필드                         | 설명                                              |
| ------------ | ---------------------------- | ------------------------------------------------- |
| `course`     | `id`                         | 강의 PK                                           |
| `course`     | `creator_id`                 | 강의 생성자 ID                                    |
| `course`     | `title`, `description`       | 강의 기본 정보                                    |
| `course`     | `price`, `capacity`          | 가격과 정원                                       |
| `course`     | `start_date`, `end_date`     | 운영 기간                                         |
| `course`     | `status`                     | `DRAFT`, `OPEN`, `CLOSED`                         |
| `course`     | `created_at`, `updated_at`   | 감사용 시각 정보                                  |
| `enrollment` | `id`                         | 수강 신청 PK                                      |
| `enrollment` | `course_id`                  | 대상 강의 FK                                      |
| `enrollment` | `student_id`                 | 신청 학생 ID                                      |
| `enrollment` | `status`                     | `PENDING`, `CONFIRMED`, `CANCELLED`, `WAITLISTED` |
| `enrollment` | `requested_at`, `updated_at` | 신청/변경 시각                                    |

### 관계와 상태 규칙

| 항목      | 규칙                                                                                                                                               |
| --------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| 관계      | 하나의 `course`는 여러 `enrollment`를 가질 수 있습니다.                                                                                            |
| 활성 신청 | `PENDING`, `CONFIRMED`, `WAITLISTED`는 중복 신청 방지 대상입니다.                                                                                  |
| 좌석 점유 | `PENDING`, `CONFIRMED`만 실제 좌석을 점유합니다.                                                                                                   |
| 대기열    | `WAITLISTED`는 좌석은 점유하지 않지만 취소 시 자동 승격 대상입니다.                                                                                |
| 상태 전이 | 강의는 `DRAFT -> OPEN -> CLOSED`, 수강 신청은 비즈니스 규칙에 따라 `PENDING -> CONFIRMED`, `* -> CANCELLED`, `WAITLISTED -> PENDING`가 허용됩니다. |

### 상태 전이 규칙 (명시적)

| 현재 상태    | 이벤트  | 다음 상태   | 조건                                                                 |
| ------------ | ------- | ----------- | -------------------------------------------------------------------- |
| `DRAFT`      | open    | `OPEN`      | 강의 소유 `CREATOR`                                                  |
| `OPEN`       | close   | `CLOSED`    | 강의 소유 `CREATOR`                                                  |
| `PENDING`    | confirm | `CONFIRMED` | 강의 소유 `CREATOR`                                                  |
| `PENDING`    | cancel  | `CANCELLED` | 신청자 본인 또는 강의 소유 `CREATOR`                                 |
| `CONFIRMED`  | cancel  | `CANCELLED` | 신청자 본인은 확정 후 7일 이내, 강의 소유 `CREATOR`는 기간 제한 없음 |
| `WAITLISTED` | promote | `PENDING`   | 선행 좌석이 해제되어 승격 가능할 때                                  |
| `WAITLISTED` | cancel  | `CANCELLED` | 신청자 본인 또는 강의 소유 `CREATOR`                                 |

- 상태 변경 API에서 정의되지 않은 전이는 모두 `INVALID_STATE_TRANSITION(409)`으로 처리합니다.

### 인덱스와 제약

| 종류               | 대상                                        | 목적                                          |
| ------------------ | ------------------------------------------- | --------------------------------------------- |
| 인덱스             | `enrollment(course_id, status)`             | 강의별 활성 신청 수 집계와 대기열 조회 최적화 |
| 인덱스             | `enrollment(student_id, requested_at desc)` | 내 신청 목록 최신순 조회 최적화               |
| 부분 유니크 인덱스 | `course_id + student_id` on active statuses | 동일 강의에 대한 활성 신청 중복 방지          |
| 체크 제약          | price, capacity, date range, enum values    | 잘못된 데이터의 DB 유입 방지                  |

인덱스는 실제 조회 패턴인 강의별 조회, 사용자별 조회, 대기열 조회에 맞춰 설계했고, 쓰기 성능과 운영 단순성을 해치지 않도록 최소한으로 유지했습니다.

## 설계 결정

이 프로젝트의 설계는 "빠른 구현"보다 "설명 가능한 정합성"을 우선했습니다. 리뷰어가 코드를 읽을 때 각 선택이 어떤 문제를 줄이기 위한 것인지 바로 이해할 수 있도록, 구조와 상태 모델을 보수적으로 두고 비즈니스 규칙을 서비스 계층에 집중시켰습니다.

### 1. 계층 구조를 단순하게 유지한 이유

애플리케이션은 Controller - Service - Repository의 전형적인 계층 구조를 사용했습니다. Controller는 HTTP 계약과 입력 검증을 담당하고, Service는 수강 신청과 상태 전이 규칙, 트랜잭션 경계를 담당하며, Repository는 조회 조건과 락 전략을 캡슐화합니다.

이 구조를 선택한 이유는 이번 과제의 핵심 복잡도가 UI나 분산 인프라가 아니라 도메인 규칙과 동시성 제어에 있기 때문입니다. 비즈니스 규칙을 Service에 모으면 테스트 작성과 리뷰가 쉬워지고, Controller는 얇게 유지되며, 저장소 계층은 일관된 DB 접근 정책을 유지할 수 있습니다.

더 풍부한 도메인 모델이나 CQRS 스타일 분리도 가능하지만, 현재 범위에서는 구조적 이점보다 설명 비용과 구현 복잡도가 더 커진다고 판단했습니다. 제출 과제에서는 가장 보수적이지만 가장 검증 가능한 구조가 더 적절하다고 봤습니다.

### 2. 외부 API와 내부 도메인 언어를 분리한 이유

외부 API는 과제 문서와 리뷰어의 기대에 맞춰 `/classes`를 유지했고, 내부 패키지와 구현은 `course`라는 도메인 언어로 정리했습니다. 외부 계약은 문제 정의에 맞추고, 내부 코드는 개발자가 유지보수하기 쉬운 언어로 가져가려는 의도입니다.

이렇게 분리하면 API 이름을 과제 문맥에 맞게 유지하면서도, 서비스와 저장소 코드에서는 더 짧고 명확한 도메인 용어를 사용할 수 있습니다. 이후 내부 구조를 변경하더라도 외부 계약을 흔들지 않아도 된다는 점에서 변경 비용도 낮아집니다.

### 3. `PENDING`을 좌석 점유 상태로 본 이유

이 시스템에서 `PENDING`은 단순한 임시 요청이 아니라 좌석 확보가 완료된 상태입니다. 신청 생성 시점에 좌석을 잡지 않으면, 확정 이전 구간에서 동일 좌석이 중복 배정될 수 있고, 정원 제어는 결제나 후속 상태 변경에 과도하게 의존하게 됩니다.

제품 관점에서도 사용자는 신청 직후 "자리가 확보되었다"고 기대합니다. 따라서 `PENDING`을 비점유 상태로 두는 것보다, 신청 순간 좌석을 확보하고 이후 확정 또는 취소로 이어지게 하는 편이 더 일관된 사용자 경험을 제공합니다.

대신 이 선택은 향후 결제 실패, 시간 만료, 자동 취소 정책이 추가될 경우 `PENDING` 좌석 회수 정책을 함께 설계해야 한다는 비용을 가집니다. 현재 과제 범위에서는 이 비용보다 정원 정합성을 안정적으로 보장하는 편이 더 중요하다고 판단했습니다.

### 4. 동시성 제어를 비관적 락 + DB 제약으로 구성한 이유

이 과제에서 가장 큰 실패 비용은 처리량 저하보다 초과 예약, 중복 신청, 상태 충돌 같은 정합성 오류입니다. 그래서 신청 생성 시에는 강의 row를, 확정과 취소 시에는 enrollment row를 비관적 락으로 보호하고, 중복 활성 신청은 DB 유니크 제약으로 한 번 더 막았습니다.

이 방식의 장점은 동시성 규칙이 코드와 데이터베이스 양쪽에 명시적으로 남는다는 점입니다. 테스트, 운영 장애 분석, 코드 리뷰 모두에서 "어디서 무엇을 보장하는가"를 설명하기 쉽고, PostgreSQL `READ COMMITTED` 환경에서도 동작을 예측하기 쉽습니다.

낙관적 락이나 애플리케이션 레벨 재시도 전략도 대안이 될 수 있지만, 이 경우 충돌 감지 이후의 재시도 정책과 실패 복구 규칙을 추가로 설계해야 합니다. 이번 과제 범위에서는 단순성과 예측 가능성을 우선해 비관적 락을 선택했습니다. 현재 제출 범위에서는 가장 화려한 구조보다 가장 검증 가능한 구조가 적합하다고 판단했습니다.

### 5. 좌석 수를 카운터 컬럼이 아니라 실시간 계산으로 유지한 이유

좌석 수를 별도 카운터 컬럼으로 유지하면 읽기 성능은 좋아질 수 있지만, 신청, 취소, 대기열 승격, 중복 요청이 모두 카운터 정합성 문제로 연결됩니다. 현재 구조에서는 락을 획득한 동일 트랜잭션 안에서 활성 신청 수를 직접 계산해 데이터의 진실 원천을 하나로 유지했습니다.

즉, 조회 성능보다 데이터 정확성과 운영 단순성을 우선한 결정입니다. 실제 대규모 트래픽과 엄격한 성능 요구가 생기는 단계라면 카운터 캐시나 별도 예약 모델을 검토할 수 있지만, 현재 규모와 요구사항에서는 직접 계산이 더 안전하고 유지보수 친화적입니다.

### 6. 대기열을 별도 테이블로 분리하지 않은 이유

대기열을 별도 모델로 분리하면 읽기 구조는 더 선명해질 수 있지만, 신청 생성, 취소, 승격 시 서로 다른 저장 구조 사이의 정합성을 계속 맞춰야 합니다. 이번 구현에서는 `WAITLISTED`를 enrollment의 한 상태로 포함시켜 신청의 전체 생애주기를 하나의 흐름으로 관리했습니다.

이 선택은 구현 복잡도를 낮추고, 감사 추적과 테스트 작성도 단순하게 만듭니다. 특히 "누가 언제 신청했고 언제 대기열에 들어갔으며 언제 승격되었는가"를 하나의 엔터티 상태 변화로 설명할 수 있어 리뷰어와 운영자 모두에게 이해 비용이 낮습니다.

## 트레이드오프

- 높은 처리량보다 정합성과 단순함을 우선했습니다.
- 인증은 헤더 기반으로 단순화했기 때문에 운영용 보안 구조는 아닙니다.
- 권한 처리는 역할 + 소유권 기반이며, 조직/팀 단위의 세밀한 권한 모델은 아닙니다.
- 결제는 별도 도메인 없이 상태 변경으로만 표현했습니다.

## 기대 효과

| 관점        | 기대 효과                                                                                |
| ----------- | ---------------------------------------------------------------------------------------- |
| 사용자 경험 | 정원 초과 시 실패 대신 대기열 등록으로 전환해 신청 실패율을 낮춥니다.                    |
| 운영 안정성 | DB 제약과 락을 함께 사용해 중복 신청, 초과 예약, 경쟁 상태를 예측 가능하게 통제합니다.   |
| 리뷰 용이성 | API, 예외 코드, 테스트 시나리오가 대응되어 구현 의도를 빠르게 검증할 수 있습니다.        |
| 확장성      | 현재 구조를 유지한 채 환불 정책, 결제 연동, 알림, 관리자 기능을 후속 확장할 수 있습니다. |

## 운영 확장 고려 사항

- 운영 환경에서는 `requestId` 기반 로깅, 메트릭 수집, 에러 모니터링을 추가해 요청 추적성과 장애 대응 속도를 높일 수 있습니다.

## AI 활용 범위

본 과제에서는 아래 AI 도구를 활용했습니다.

**ChatGPT (GPT-4o mini, 웹 브라우저)**

- 문서 작성: README 및 작업 목록(TODO) 초안 작성과 표현 정리에 활용했습니다.
- 문법 참조: Spring Boot 및 Java 문법 확인 등 레퍼런스 조회 용도로 활용했습니다.

**GitHub Copilot (VS Code)**

- 코드 작성 보조: 반복적인 보일러플레이트 코드 자동 완성에 활용했습니다.
- 리팩터링 검토: 코드 리뷰 및 개선 제안을 참고했습니다.

아키텍처 설계, 도메인 모델링, 동시성 처리 전략, 비즈니스 규칙 해석, 테스트 시나리오 설계는 모두 직접 판단하고 구현했습니다.

## 미구현 또는 범위 밖 항목

- 환불 정책
- 실제 인증/인가 인프라
- 외부 결제 연동

## 구현 상태 요약

- 대기열(waitlist) 지원 — 정원 초과 시 `WAITLISTED`로 등록하고, `PENDING`/`CONFIRMED` 취소 시 가장 오래된 `WAITLISTED`를 자동으로 `PENDING`으로 승격합니다.
- 동시성 테스트는 성공/실패 HTTP 상태, 정확한 에러 코드, 최종 DB 상태를 모두 검증합니다.
- 락 타임아웃 발생 시 `LOCK_TIMEOUT(409)` 에러 코드를 반환합니다.
- 강의 상태 변경과 수강 신청 확정은 해당 강의를 생성한 크리에이터 본인만 가능합니다.
- 강의별 수강생 목록 조회(`GET /classes/{courseId}/enrollments`)는 강의 소유 CREATOR만 호출할 수 있습니다.
- 취소 가능 기간 정책 — 학생 본인이 `CONFIRMED` 상태의 신청을 취소하려면 확정 후 7일 이내여야 합니다. `PENDING` 취소 및 크리에이터 취소는 기간 제한이 없습니다. 기간이 만료된 경우 `CANCELLATION_WINDOW_EXPIRED(409)`를 반환합니다.
