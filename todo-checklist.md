# Working Checklist

Simple checklist for finishing the BE-A take-home without losing track of what is already done versus what is only scaffolded.

Main goal: complete the required APIs, make enrollment concurrency safe, and leave a repo that is easy to review.

## 0. Current review snapshot (2026-04-27)

- [x] Spring Boot app, Flyway migration, entities, enums, and baseline repositories exist
- [x] Lightweight auth is decided and implemented with `X-User-Id` and `X-User-Role`
- [x] Shared error model exists and business, validation, DB-integrity, and catch-all handlers are active
- [x] All required APIs implemented: course create/list/detail/status change and enrollment create/confirm/cancel/list
- [x] `./mvnw test` passes with Testcontainers PostgreSQL and Flyway
- [x] Docker Compose exists at `docker/postgres-compose.yml`
- [x] Unit tests and API-level integration tests exist for course and enrollment flows
- [x] Real multi-threaded concurrency integration coverage exists in `EnrollmentConcurrencyTest`
- [x] Duplicate active-enrollment constraint mapping to `DUPLICATE_ENROLLMENT` exists in `GlobalExceptionHandler`
- [x] README has been updated to match the current implementation
- [ ] Concurrency tests still assert generic `409` outcomes rather than exact losing error codes
- [ ] Lock-timeout-specific exception mapping is still missing
- [ ] Ownership semantics are still role-based only; verify whether stricter creator ownership is expected

## 1. Project setup

- [x] Create Spring Boot project with Java 21
- [x] Add the required runtime dependencies: Web, Validation, JPA, PostgreSQL
- [x] Replace scaffold defaults: `artifactId`, app name, and application naming
- [x] Set up PostgreSQL with Docker Compose
- [x] Configure datasource settings for local run and tests (`application.yml`)
- [x] Confirm the app starts and the smoke test suite passes
- [x] Decide and implement the lightweight auth approach with request headers
- [x] Keep the package structure small: controller / service / repository / entity / dto

## 2. Database and schema

- [x] Create `course` table
- [x] Create `enrollment` table
- [x] Add index on `enrollment(course_id, status)`
- [x] Add index on `enrollment(student_id, requested_at)`
- [x] Add unique partial index for one active enrollment per student per course
- [x] Store enum values as strings, not ordinals

## 3. Course flow

- [x] Implement course create API scaffold
- [x] Validate course create request fields: title, description, price, capacity, start date, end date
- [x] Allow only `CREATOR` to create courses
- [x] Save new courses with initial `DRAFT` status
- [x] Return `201 Created` and `Location` header from course create API
- [x] Remove temporary `/classes/test` endpoint
- [x] Implement course status change API
- [x] Implement course list API with optional `status` filter
- [x] Implement course detail API
- [x] Return current active enrollment count from course detail
- [x] Allow only `CREATOR` to change course status
- [x] Enforce `DRAFT -> OPEN -> CLOSED` only
- [x] Reject all other transitions explicitly
- [x] Keep naming consistent between API, DTO, and domain, or document the mapping clearly

## 4. Enrollment flow

- [x] Implement enrollment create API
- [x] Implement enrollment confirm API
- [x] Implement enrollment cancel API
- [x] Implement my enrollments API
- [x] Add enrollment request/response DTOs
- [x] Keep payment confirmation simplified as an internal status change, without external payment integration
- [x] Start every new enrollment as `PENDING`
- [x] Treat `PENDING` and `CONFIRMED` as active enrollments
- [x] Use the same active-enrollment definition everywhere: `PENDING + CONFIRMED`
- [x] Reject enrollments when course is not `OPEN`
- [x] Reject duplicate active enrollment for the same student and course
- [x] Perform duplicate check before capacity check to fail fast
- [x] Treat duplicate enrollment attempts as effectively idempotent through DB constraint protection
- [x] Allow only `CREATOR` to confirm enrollments
- [x] Allow only the owner student or a creator to cancel enrollments
- [x] Make `CANCELLED` terminal

## 5. Concurrency and transaction safety

- [x] Wrap enrollment creation in one transaction
- [x] Ensure course load, duplicate check, capacity check, and insert all happen inside the same transaction boundary
- [x] Load course with `PESSIMISTIC_WRITE` during enrollment creation
- [x] Ensure all reads used for enrollment creation happen after the course lock is acquired
- [x] Re-check course status after the row lock is acquired
- [x] Assume PostgreSQL default isolation level (`READ COMMITTED`)
- [x] Rely on row-level locking for correctness under that isolation level
- [x] Rely on the course row lock for capacity consistency, and the DB unique constraint for duplicate protection
- [x] Configure lock wait timeout behavior (`hibernate.jakarta.persistence.lock.timeout=5000`)
- [x] Count active enrollments inside the locked transaction
- [x] Ensure the count query explicitly filters only active statuses: `PENDING`, `CONFIRMED`
- [x] Reject enrollment when active count has reached capacity
- [x] Insert `PENDING` enrollment inside the same transaction
- [x] Ensure flush happens before commit when relying on DB constraints
- [x] Ensure JPA queries inside the same transaction see the latest state
- [x] Wrap confirm and cancel in their own transactions
- [x] Load enrollment with `PESSIMISTIC_WRITE` for confirm and cancel
- [x] Ensure only one state transition can succeed per enrollment under concurrent requests
- [x] Convert unique-constraint violations to `DUPLICATE_ENROLLMENT`
- [x] Treat the service-level duplicate check as a fast-fail optimization; the database is the final source of truth
- [ ] Map lock-timeout or lock-acquisition failures to a stable API error instead of relying on generic fallback handling

## 6. Error handling

- [x] Add one `@RestControllerAdvice`
- [x] Define small, clear error codes
- [x] Keep error response structure consistent with `ErrorResponse`
- [x] Reject missing or invalid auth headers with `UNAUTHORIZED`
- [x] Enable `DataIntegrityViolationException` mapping
- [x] Add catch-all unexpected exception mapping
- [x] Use `COURSE_NOT_OPEN` for both `DRAFT` and `CLOSED`
- [x] Return consistent errors for invalid state transitions
- [x] Return `FORBIDDEN` for role or ownership failures
- [x] Return `NOT_FOUND` for missing course or enrollment
- [x] Map database exceptions such as `DataIntegrityViolationException` to business errors where needed
- [x] Avoid returning JPA entities directly from controllers
- [ ] Add explicit handling for lock timeout / lock acquisition exceptions under contention

## 7. Tests

- [x] Keep Testcontainers PostgreSQL integration support in place
- [x] Keep the current smoke test passing
- [x] Keep the minimal `contextLoads` smoke test alongside the BE-A integration tests
- [x] Test course creation success
- [x] Test invalid course status transition
- [x] Test enrollment rejected for `DRAFT` or `CLOSED`
- [x] Test duplicate active enrollment rejection
- [x] Test capacity full rejection
- [x] Test confirm and cancel state rules
- [x] Test last-seat race: exactly one request succeeds and the others fail with `409`
- [x] Test same-student double submit: only one active enrollment is created
- [x] Test confirm vs cancel race on the same enrollment: only one state change succeeds
- [x] Use real multi-threaded execution to simulate concurrency
- [x] Use a latch or barrier to align concurrent start timing in race-condition tests
- [x] Keep test focus on integration tests, not a big controller test suite
- [ ] Strengthen concurrency assertions to verify the exact error code for each losing path (`COURSE_FULL`, `DUPLICATE_ENROLLMENT`, `INVALID_STATE_TRANSITION`)

## 8. README

- [x] Replace the placeholder README with the required assignment sections
- [x] Add project overview
- [x] Add tech stack
- [x] Add local run instructions
- [x] Add Docker-based run instructions for the database dependency
- [x] Add test run instructions
- [x] Add requirement interpretation and assumptions
- [x] Add API list and sample requests/responses
- [x] Add a short concurrency design summary
- [x] Include one concrete race scenario example
- [x] Add data model description plus DB schema or ERD explanation
- [x] Add design decisions and reasons
- [x] Add trade-offs
- [x] Add AI usage scope
- [x] Add unimplemented items and constraints

## 9. Product-level edge cases

- [ ] Check the same user retrying enrollment rapidly because of network retries
- [ ] Check the same payment confirmation request being sent twice
- [ ] Check cancelling after confirmation
- [ ] Check enrollment while the course is being closed
- [ ] Add minimal logging for enrollment success and failure paths

## 10. Important notes to explain in README

- [x] Explain that `PENDING` reserves a seat
- [x] Explain why pessimistic locking was chosen
- [x] Explain why `COUNT` was chosen over a counter field
- [x] Explain that uniqueness is enforced in both service logic and the database
- [x] Explain that the service duplicate check is only a fast-fail path and the database is the final source of truth
- [ ] Explain that closing a course blocks new enrollments only
- [x] Explain that payment is simplified as a status change
- [x] Document the mapping between API naming (`classes`) and domain naming (`course`)

## 11. POST MVP

- [ ] Restrict enrollment cancellation to an allowed window after payment, for example within 7 days
- [ ] Add waitlist support
- [ ] Add per-course enrolled student list for the creator
- [ ] Add pagination for enrollment history

## 12. Scope control

- [x] Do not add Redis, queues, or extra infrastructure
- [x] Do not add Spring Security for this take-home
- [x] Keep the solution boring and reliable
- [ ] Do not spend time on optional items until all required APIs, tests, and docs are complete
- [ ] Keep POST MVP items out of the core delivery scope until all required APIs, tests, and docs are complete

## 13. Final check before submission

- [ ] Start from a clean database and run the app again
- [ ] Run the test suite from a clean state
- [ ] Verify all required APIs work end to end
- [ ] Verify no unexpected lazy loading or N+1 query behavior in course detail
- [ ] Re-read README once as if I were the reviewer
- [ ] Verify the repository has readable commit history and is ready to submit as a public URL
- [ ] Make sure the repository is public and runnable on `main`
