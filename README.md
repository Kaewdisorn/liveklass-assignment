# Liveklass 과제

Spring Boot와 PostgreSQL로 구현한 Liveklass BE-A 과제용 수강 신청 시스템입니다.

강의 생성/조회/상태 변경, 수강 신청 생성/확정/취소/내역 조회가 구현되어 있습니다. 스키마는 Flyway로 관리하고, 수강 신청 관련 동시성은 비관적 락과 데이터베이스 제약으로 보호합니다. 테스트는 단위 테스트, MVC 테스트, 통합 테스트, 동시성 테스트까지 포함합니다.

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

- 수강 신청 생성 — `OPEN` 상태의 강의에 한해 `STUDENT`가 신청할 수 있습니다. 정원 초과·중복 신청은 즉시 거부됩니다.
- 수강 신청 확정 — 해당 강의의 소유 `CREATOR`만 `PENDING` → `CONFIRMED`로 바꿀 수 있습니다.
- 수강 신청 취소 — 신청자 본인 또는 해당 강의의 `CREATOR`가 취소할 수 있습니다.
- 내 신청 목록 조회 — 로그인한 사용자의 전체 신청 이력을 최신순으로 반환합니다.

### 동시성 안전성

- 수강 신청 생성 시 강의 row에 `PESSIMISTIC_WRITE` 락을 걸어 정원 초과를 방지합니다.
- 확정/취소 시 enrollment row에 `PESSIMISTIC_WRITE` 락을 걸어 상태 충돌을 방지합니다.
- DB 부분 유니크 인덱스로 중복 활성 신청을 추가 차단합니다.
- 락 대기 시간 초과 시 `LOCK_TIMEOUT(409)` 에러를 반환합니다.

### 보안 및 권한

- 헤더 기반 인증 — `X-User-Id` / `X-User-Role` 헤더로 사용자를 식별합니다.
- 역할 기반 인가 — `CREATOR` / `STUDENT` 역할에 따라 API 접근을 제한합니다.
- 소유권 검사 — 강의 상태 변경과 수강 확정은 해당 강의의 생성자만 수행할 수 있습니다.

### 안정성 및 품질

- Bean Validation + 전역 예외 처리(`GlobalExceptionHandler`)로 일관된 에러 응답을 반환합니다.
- Flyway 마이그레이션으로 스키마를 코드로 관리합니다.
- 단위 테스트, MockMvc 테스트, Testcontainers 통합 테스트, 동시성 테스트 109건이 모두 통과합니다.

## 기술 스택

**런타임**

- Java 21
- Spring Boot 4.0.5
- Spring Web MVC
- Bean Validation

**데이터**

- Spring Data JPA
- PostgreSQL 16
- Flyway

**테스트**

- JUnit 5
- Spring Boot Test (MockMvc)
- Testcontainers

**빌드**

- Maven Wrapper

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
    ├── dto/                          # CreateEnrollmentRequest, EnrollmentResponse
    ├── entity/Enrollment.java
    ├── enums/EnrollmentStatus.java   # PENDING / CONFIRMED / CANCELLED
    ├── repository/EnrollmentRepository.java
    └── service/EnrollmentService.java + EnrollmentServiceImpl.java

src/main/resources/
├── application.yml
└── db/migration/V1__initial.sql     # Flyway 초기 스키마
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
- 2026-04-27 기준 결과: 테스트 109건, 실패 0건, 에러 0건

## 요구사항 해석과 가정

- 외부 API 경로는 `/classes`를 사용하고, 내부 패키지명은 `course`를 사용합니다.
- 강의 생성과 강의 상태 변경은 `CREATOR`만 수행할 수 있습니다.
- 수강 신청 생성은 `STUDENT`만 수행할 수 있습니다.
- 수강 확정은 외부 결제 연동 없이 내부 상태 변경으로 단순화했습니다.
- `PENDING`과 `CONFIRMED`는 모두 좌석을 점유하는 활성 상태로 봅니다.
- 같은 학생은 같은 강의에 대해 `PENDING` 또는 `CONFIRMED` 상태의 신청을 하나만 가질 수 있습니다.
- 강의 상태 전이는 `DRAFT -> OPEN -> CLOSED`만 허용합니다.
- 강의 상태 변경과 수강 신청 확정은 해당 강의를 직접 생성한 `CREATOR`만 수행할 수 있습니다.

## API 요약

### 강의 API

| 메서드  | 경로                         | 권한      | 설명           |
| ------- | ---------------------------- | --------- | -------------- |
| `POST`  | `/classes`                   | `CREATOR` | 강의 생성      |
| `GET`   | `/classes`                   | 공개      | 강의 목록 조회 |
| `GET`   | `/classes/{courseId}`        | 공개      | 강의 상세 조회 |
| `PATCH` | `/classes/{courseId}/status` | `CREATOR` | 강의 상태 변경 |

### 수강 신청 API

| 메서드 | 경로                                  | 권한                       | 설명                   |
| ------ | ------------------------------------- | -------------------------- | ---------------------- |
| `POST` | `/enrollments`                        | `STUDENT`                  | 수강 신청 생성         |
| `POST` | `/enrollments/{enrollmentId}/confirm` | `CREATOR`                  | 수강 신청 확정         |
| `POST` | `/enrollments/{enrollmentId}/cancel`  | 신청자 본인 또는 `CREATOR` | 수강 신청 취소         |
| `GET`  | `/enrollments/me`                     | 인증 필요                  | 내 수강 신청 목록 조회 |

## 요청/응답 예시

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

### 3. 수강 신청 생성

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

### 4. 수강 신청 확정

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

### 5. 수강 신청 취소

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

### 6. 내 수강 신청 목록 조회

요청:

```http
GET /enrollments/me
X-User-Id: 10
X-User-Role: STUDENT
```

응답:

```json
[
  {
    "enrollmentId": 100,
    "courseId": 1,
    "studentId": 10,
    "status": "CONFIRMED",
    "requestedAt": "2026-04-27T01:05:00Z",
    "updatedAt": "2026-04-27T01:06:00Z"
  }
]
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

| 코드                       | HTTP 상태 | 설명                                     |
| -------------------------- | --------- | ---------------------------------------- |
| `BAD_REQUEST`              | 400       | 입력값 오류                              |
| `UNAUTHORIZED`             | 401       | 인증 헤더 누락 또는 형식 오류            |
| `FORBIDDEN`                | 403       | 권한 없음 (역할 불일치 또는 소유자 아님) |
| `NOT_FOUND`                | 404       | 리소스 없음                              |
| `INVALID_STATE_TRANSITION` | 409       | 허용되지 않은 상태 전이                  |
| `DUPLICATE_ENROLLMENT`     | 409       | 이미 활성 수강 신청이 존재함             |
| `COURSE_NOT_OPEN`          | 409       | 모집 중이 아닌 강의에 신청 시도          |
| `COURSE_FULL`              | 409       | 정원 초과                                |
| `LOCK_TIMEOUT`             | 409       | DB 락 대기 시간 초과, 재시도 필요        |
| `INTERNAL_SERVER_ERROR`    | 500       | 서버 내부 오류                           |

## 동시성 처리 방식

수강 신청 관련 처리에는 트랜잭션, 행 단위 락, 데이터베이스 제약을 함께 사용합니다.

### 수강 신청 생성

- 대상 강의를 `PESSIMISTIC_WRITE`로 잠급니다.
- 락 획득 후 강의 상태, 중복 활성 신청 여부, 현재 좌석 수를 다시 확인합니다.
- 같은 트랜잭션 안에서 `PENDING` 상태의 신청을 저장합니다.
- DB에는 `(course_id, student_id)`에 대한 부분 유니크 인덱스를 두고, `PENDING`/`CONFIRMED` 중복을 막습니다.

### 수강 신청 확정/취소

- 확정과 취소는 모두 대상 enrollment row를 `PESSIMISTIC_WRITE`로 조회합니다.
- 같은 신청에 대한 경쟁 상태 변경 요청을 직렬화해서 최종 상태가 뒤엉키지 않게 합니다.

### 락 대기 시간

- `application.yml`에서 JPA lock timeout을 `5000ms`로 설정했습니다.

### 테스트로 검증한 경쟁 상황

| 시나리오               | 설정                   | 성공     | 실패 응답                        |
| ---------------------- | ---------------------- | -------- | -------------------------------- |
| 마지막 좌석 경쟁       | 정원 1, 동시 10명 신청 | 1명 성공 | `COURSE_FULL (409)`              |
| 같은 학생 중복 요청    | 동시 5건 신청          | 1건 성공 | `DUPLICATE_ENROLLMENT (409)`     |
| 확정 vs 취소 동시 요청 | 동시 2건               | 1건 성공 | `INVALID_STATE_TRANSITION (409)` |

## 데이터 모델

### `course`

- `id`: PK
- `creator_id`: 강의 생성자 ID
- `title`: 필수, 최대 100자
- `description`: 선택 입력
- `price`: 0 이상
- `capacity`: 1 이상
- `start_date`, `end_date`: 필수, `start_date <= end_date`
- `status`: `DRAFT`, `OPEN`, `CLOSED`
- `created_at`, `updated_at`: 생성/수정 시각

### `enrollment`

- `id`: PK
- `course_id`: 강의 FK
- `student_id`: 학생 ID
- `status`: `PENDING`, `CONFIRMED`, `CANCELLED`
- `requested_at`, `updated_at`: 생성/수정 시각

### 인덱스와 제약

- `enrollment(course_id, status)` 인덱스
- `enrollment(student_id, requested_at desc)` 인덱스
- 활성 신청 1건만 허용하는 부분 유니크 인덱스
- 가격, 정원, 날짜 범위, enum 값에 대한 DB 체크 제약

## 설계 결정

### 외부 API는 `/classes`, 내부 패키지는 `course`로 둔 이유

과제 문서에서 보이는 용어와 내부 코드의 도메인 표현을 분리했습니다. 리뷰어는 API를 이해하기 쉽고, 코드는 도메인 기준으로 정리하기 좋습니다.

### `PENDING`도 좌석을 점유하도록 한 이유

신청 생성 직후부터 자리를 확보해야 과예약을 막을 수 있기 때문입니다. 확정 전까지 좌석을 비워 두면 경쟁 상황에서 정원 초과가 더 쉽게 발생합니다.

### 비관적 락을 선택한 이유

이 과제에서는 처리량보다 정합성이 더 중요합니다. PostgreSQL `READ COMMITTED` 환경에서 강의 row를 잠그는 방식이 가장 단순하고 설명하기 쉬운 방법입니다.

### 카운터 컬럼 대신 `COUNT`를 사용한 이유

좌석 수를 위한 별도 카운터 컬럼을 두면 관리해야 할 상태가 하나 더 생깁니다. 현재 구조에서는 활성 신청 수를 직접 세는 편이 단순하고 실수 여지가 적습니다.

## 트레이드오프

- 높은 처리량보다 정합성과 단순함을 우선했습니다.
- 인증은 헤더 기반으로 단순화했기 때문에 운영용 보안 구조는 아닙니다.
- 권한 처리는 역할 + 소유권 기반이며, 조직/팀 단위의 세밀한 권한 모델은 아닙니다.
- 결제는 별도 도메인 없이 상태 변경으로만 표현했습니다.

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

- 대기열(waitlist)
- 수강 신청 목록 페이지네이션
- 강의별 수강생 목록 조회
- 실제 인증/인가 인프라
- 외부 결제 연동
- 취소 가능 기간/환불 정책

## 구현 상태 요약

- 동시성 테스트는 성공/실패 HTTP 상태, 정확한 에러 코드, 최종 DB 상태를 모두 검증합니다.
- 락 타임아웃 발생 시 `LOCK_TIMEOUT(409)` 에러 코드를 반환합니다.
- 강의 상태 변경과 수강 신청 확정은 해당 강의를 생성한 크리에이터 본인만 가능합니다.
