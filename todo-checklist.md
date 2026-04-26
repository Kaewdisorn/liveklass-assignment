# Working Checklist

Simple checklist for finishing the BE-A take-home without losing track of what is already done versus what is only scaffolded.

Main goal: complete the required APIs, make enrollment concurrency safe, and leave a repo that is easy to review.

## 0. Current review snapshot (2026-04-23)

- [x] Spring Boot app, Flyway migration, entities, enums, and baseline repositories exist
- [x] Lightweight auth is decided and implemented with `X-User-Id` and `X-User-Role`
- [x] Shared error model exists and business plus validation handlers are active
- [x] Course creation scaffolding exists across DTO, controller, service, and persistence
- [x] `./mvnw test` passes with Testcontainers PostgreSQL and Flyway
- [x] Docker Compose exists at `docker/postgres-compose.yml`
- [x] `GlobalExceptionHandler` still has commented-out DB-integrity and catch-all handlers
- [ ] Course API is still incomplete: create returns `200 OK`, `/classes/test` is still present, and detail/list/status-change endpoints do not exist
- [ ] Enrollment service and controller layers do not exist yet
- [ ] README is still a placeholder
- [ ] Test coverage is still smoke-only: `LiveklassApplicationTests` still contains only `contextLoads`

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
- [ ] Remove temporary `/classes/test` endpoint
- [x] Implement course status change API
- [x] Implement course list API with optional `status` filter
- [x] Implement course detail API
- [ ] Return current active enrollment count from course detail
- [x] Allow only `CREATOR` to change course status
- [x] Enforce `DRAFT -> OPEN -> CLOSED` only
- [x] Reject all other transitions explicitly
- [x] Keep naming consistent between API, DTO, and domain, or document the mapping clearly

## 4. Enrollment flow

- [ ] Implement enrollment create API
- [ ] Implement enrollment confirm API
- [ ] Implement enrollment cancel API
- [ ] Implement my enrollments API
- [ ] Add enrollment request/response DTOs
- [ ] Keep payment confirmation simplified as an internal status change, without external payment integration
- [ ] Start every new enrollment as `PENDING`
- [ ] Treat `PENDING` and `CONFIRMED` as active enrollments
- [ ] Use the same active-enrollment definition everywhere: `PENDING + CONFIRMED`
- [ ] Reject enrollments when course is not `OPEN`
- [ ] Reject duplicate active enrollment for the same student and course
- [ ] Perform duplicate check before capacity check to fail fast
- [ ] Treat duplicate enrollment attempts as effectively idempotent through DB constraint protection
- [ ] Validate that only the owner of the enrollment can confirm or cancel it
- [ ] Make `CANCELLED` terminal

## 5. Concurrency and transaction safety

- [ ] Wrap enrollment creation in one transaction
- [ ] Ensure course load, duplicate check, capacity check, and insert all happen inside the same transaction boundary
- [ ] Load course with `PESSIMISTIC_WRITE` during enrollment creation
- [ ] Ensure all reads used for enrollment creation happen after the course lock is acquired
- [ ] Re-check course status after the row lock is acquired
- [ ] Assume PostgreSQL default isolation level (`READ COMMITTED`)
- [ ] Rely on row-level locking for correctness under that isolation level
- [ ] Rely on the course row lock for capacity consistency, and the DB unique constraint for duplicate protection
- [ ] Consider lock wait timeout behavior so enrollment requests do not block forever
- [ ] Count active enrollments inside the locked transaction
- [ ] Ensure the count query explicitly filters only active statuses: `PENDING`, `CONFIRMED`
- [ ] Reject enrollment when active count has reached capacity
- [ ] Insert `PENDING` enrollment inside the same transaction
- [ ] Ensure flush happens before commit when relying on DB constraints
- [ ] Ensure JPA queries inside the same transaction see the latest state
- [ ] Wrap confirm and cancel in their own transactions
- [ ] Load enrollment with `PESSIMISTIC_WRITE` for confirm and cancel
- [ ] Ensure only one state transition can succeed per enrollment under concurrent requests
- [ ] Convert unique-constraint violations to `DUPLICATE_ENROLLMENT`
- [ ] Treat the service-level duplicate check as a fast-fail optimization; the database is the final source of truth

## 6. Error handling

- [x] Add one `@RestControllerAdvice`
- [x] Define small, clear error codes
- [x] Keep error response structure consistent with `ErrorResponse`
- [x] Reject missing or invalid auth headers with `UNAUTHORIZED`
- [x] Enable `DataIntegrityViolationException` mapping
- [x] Add catch-all unexpected exception mapping
- [ ] Use `COURSE_NOT_OPEN` for both `DRAFT` and `CLOSED`
- [x] Return consistent errors for invalid state transitions
- [x] Return `FORBIDDEN` for role or ownership failures
- [x] Return `NOT_FOUND` for missing course or enrollment
- [ ] Map database exceptions such as `DataIntegrityViolationException` to business errors where needed
- [x] Avoid returning JPA entities directly from controllers

## 7. Tests

- [x] Keep Testcontainers PostgreSQL integration support in place
- [x] Keep the current smoke test passing
- [ ] Replace the placeholder `contextLoads` test with BE-A integration tests
- [ ] Test course creation success
- [ ] Test invalid course status transition
- [ ] Test enrollment rejected for `DRAFT` or `CLOSED`
- [ ] Test duplicate active enrollment rejection
- [ ] Test capacity full rejection
- [ ] Test confirm and cancel state rules
- [ ] Test last-seat race: exactly one request succeeds and the others fail with `COURSE_FULL`
- [ ] Test same-student double submit: only one active enrollment is created
- [ ] Test confirm vs cancel race on the same enrollment: only one state change succeeds
- [ ] Use real multi-threaded execution to simulate concurrency
- [ ] Use a latch or barrier to align concurrent start timing in race-condition tests
- [ ] Keep test focus on integration tests, not a big controller test suite

## 8. README

- [ ] Replace the placeholder README with the required assignment sections
- [ ] Add project overview
- [ ] Add tech stack
- [ ] Add local run instructions
- [ ] Add Docker-based run instructions for the database dependency
- [ ] Add test run instructions
- [ ] Add requirement interpretation and assumptions
- [ ] Add API list and sample requests/responses
- [ ] Add a short concurrency design summary
- [ ] Include one concrete race scenario example
- [ ] Add data model description plus DB schema or ERD explanation
- [ ] Add design decisions and reasons
- [ ] Add trade-offs
- [ ] Add AI usage scope
- [ ] Add unimplemented items and constraints

## 9. Product-level edge cases

- [ ] Check the same user retrying enrollment rapidly because of network retries
- [ ] Check the same payment confirmation request being sent twice
- [ ] Check cancelling after confirmation
- [ ] Check enrollment while the course is being closed
- [ ] Add minimal logging for enrollment success and failure paths

## 10. Important notes to explain in README

- [ ] Explain that `PENDING` reserves a seat
- [ ] Explain why pessimistic locking was chosen
- [ ] Explain why `COUNT` was chosen over a counter field
- [ ] Explain that uniqueness is enforced in both service logic and the database
- [ ] Explain that the service duplicate check is only a fast-fail path and the database is the final source of truth
- [ ] Explain that closing a course blocks new enrollments only
- [ ] Explain that payment is simplified as a status change
- [ ] Document the mapping between API naming (`classes`) and domain naming (`course`)

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
