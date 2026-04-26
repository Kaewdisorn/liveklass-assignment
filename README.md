# 프로젝트 개요

이 프로젝트는 Liveklass 백엔드 과제 BE-A인 수강 신청 시스템을 Spring Boot로 구현한 저장소입니다.

현재 기준으로 다음 흐름을 중심으로 구현을 진행하고 있습니다.

- 강의 생성
- 강의 목록 조회 및 상세 조회
- 강의 상태 변경
- 수강 신청 생성
- 수강 신청 확정

인증/인가 요구사항은 과제 가이드에 맞춰 단순화했고, 요청 사용자 정보는 헤더 기반으로 주입하는 구조를 사용합니다.

## 기술 스택

- Java 21
- Spring Boot 4.0.5
- Spring Web MVC
- Spring Data JPA
- Bean Validation
- PostgreSQL
- Flyway
- JUnit 5
- Spring Boot Test
- Testcontainers
- Maven Wrapper

## 실행 방법

### 1. 사전 준비

- Java 21
- Docker Desktop 또는 Docker Engine

### 2. 데이터베이스 실행

프로젝트 루트에서 아래 명령으로 PostgreSQL 컨테이너를 올릴 수 있습니다.

```bash
docker compose -f docker/postgres-compose.yml up -d
```

기본 DB 연결 정보는 아래와 같습니다.

- DB URL: `jdbc:postgresql://localhost:5432/liveklass`
- Username: `liveklass`
- Password: `liveklass`

### 3. 애플리케이션 실행

macOS/Linux 또는 Git Bash/WSL 환경에서는 아래 스크립트로 DB 기동과 애플리케이션 실행을 한 번에 처리할 수 있습니다.

```bash
./run-dev.sh
```

직접 실행할 경우에는 아래 명령을 사용합니다.

```bash
./mvnw spring-boot:run
```

Windows PowerShell에서는 다음 명령을 사용할 수 있습니다.

```powershell
.\mvnw.cmd spring-boot:run
```

애플리케이션 시작 시 Flyway가 `src/main/resources/db/migration`의 마이그레이션을 자동 적용합니다.

## 요구사항 해석 및 가정

- 강의 생성과 상태 변경은 크리에이터 역할의 사용자만 수행한다고 가정했습니다.
- 수강 신청과 수강 확정은 수강생 역할의 사용자 컨텍스트를 기준으로 처리한다고 가정했습니다.
- 과제 설명에 따라 별도 인증 시스템은 붙이지 않고, 요청 헤더 기반 사용자 식별로 단순화했습니다.
- 강의 상태는 `DRAFT -> OPEN -> CLOSED` 흐름을 갖는다고 보고, 신청 가능 여부는 강의 상태에 의존하도록 해석했습니다.
- 수강 신청은 `PENDING -> CONFIRMED -> CANCELLED` 상태 모델을 갖는다고 가정했습니다.
- 동일 사용자가 같은 강의에 대해 활성 상태(`PENDING`, `CONFIRMED`)의 신청을 중복 생성하지 못하도록 데이터베이스 제약을 두었습니다.
- 동시성 제어는 과제 핵심 요구사항이지만, 현재는 완성 단계가 아니므로 README의 미구현 항목에 명시했습니다.

## 설계 결정과 이유

### 도메인 분리

`course`, `enrollment`, `common` 패키지로 책임을 분리했습니다. 과제 범위가 크지 않더라도 강의 관리와 수강 신청은 규칙이 다르기 때문에, 컨트롤러/서비스/DTO/엔티티를 도메인별로 분리하는 편이 이후 확장에 유리하다고 판단했습니다.

### DB 제약 우선

가격, 정원, 날짜 범위, 상태값, 중복 활성 신청은 애플리케이션 코드뿐 아니라 DB 스키마에서도 함께 제약했습니다. 비즈니스 규칙이 중요한 과제이기 때문에, 검증을 애플리케이션 레이어에만 두는 것보다 데이터 무결성을 이중으로 보호하는 쪽이 안전하다고 봤습니다.

### Flyway 기반 스키마 관리

DDL 자동 생성을 쓰지 않고 Flyway 마이그레이션으로 스키마를 관리했습니다. 제출 과제에서도 테이블 구조와 제약을 명시적으로 보여주는 편이 설계 의도를 전달하기 쉽고, 로컬/테스트 환경 재현성도 더 높습니다.

### 단순한 인증 추상화

실제 인증 시스템 대신 요청 사용자 정보를 Resolver로 주입하는 방식을 택했습니다. 과제 요구사항이 인증 자체보다 비즈니스 규칙 검증에 있으므로, 사용자 컨텍스트는 단순하게 유지하고 핵심 로직 구현에 집중하는 쪽이 적절하다고 판단했습니다.

## 미구현 / 제약사항

- 수강 취소 API는 아직 비활성 상태입니다.
- 내 수강 신청 목록 조회 API는 아직 비활성 상태입니다.
- 정원 초과 경쟁 상황을 위한 명시적 동시성 제어는 아직 마무리되지 않았습니다.
- 대기열(waitlist) 기능은 구현하지 않았습니다.
- 취소 가능 기간 제한은 구현하지 않았습니다.
- 강의별 수강생 목록 조회는 구현하지 않았습니다.
- 현재 컨트롤러에 확인용 테스트 엔드포인트 `/classes/test`가 남아 있습니다.
- README의 `AI 활용 범위`, `API 목록 및 예시`, `테스트 실행 방법` 섹션은 아직 작성 중입니다.

## AI 활용 범위

작성 중

## API 목록 및 예시

작성 중

## 데이터 모델 설명

### Course

강의 정보를 저장하는 엔티티입니다.

- `id`: 강의 ID
- `creator_id`: 강의를 등록한 사용자 ID
- `title`: 강의 제목
- `description`: 강의 설명
- `price`: 강의 가격
- `capacity`: 최대 수강 가능 인원
- `start_date`, `end_date`: 수강 기간
- `status`: `DRAFT`, `OPEN`, `CLOSED`
- `created_at`, `updated_at`: 생성/수정 시각

주요 제약은 다음과 같습니다.

- 가격은 0 이상
- 정원은 1 이상
- 시작일은 종료일보다 늦을 수 없음

### Enrollment

사용자의 수강 신청 정보를 저장하는 엔티티입니다.

- `id`: 수강 신청 ID
- `course_id`: 대상 강의 ID
- `student_id`: 신청한 사용자 ID
- `status`: `PENDING`, `CONFIRMED`, `CANCELLED`
- `requested_at`, `updated_at`: 신청/수정 시각

주요 제약은 다음과 같습니다.

- `course_id`, `status` 복합 인덱스로 강의별 신청 상태 조회를 최적화
- `student_id`, `requested_at` 인덱스로 내 신청 목록 조회를 대비
- 동일 강의에 대해 동일 사용자의 활성 신청(`PENDING`, `CONFIRMED`)은 유니크 인덱스로 중복 방지

## 테스트 실행 방법

작성 중
