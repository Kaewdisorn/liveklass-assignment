# Working Checklist

Simple checklist for building the BE-A take-home without overcomplicating it.

Main goal: finish the required flow cleanly, make concurrency safe, and leave a repo that is easy to review.

## 0. Current review snapshot (2026-04-22)

- [x] Spring Boot starter scaffold exists
- [x] BE-A implementation has not started yet beyond the default application class and placeholder test
- [x] `./mvnw test` currently fails because no datasource is configured for the application context
- [x] README is still a placeholder (still needs full content)
- [x] Docker Compose added at `docker/postgres-compose.yml`

## 1. Project setup

- [x] Create Spring Boot project with Java 21
- [x] Add the required runtime dependencies: Web, Validation, JPA, PostgreSQL
- [x] Replace scaffold defaults: `artifactId`, app name, and `DemoApplication` naming
- [x] Set up PostgreSQL with Docker Compose
- [x] Configure datasource settings for local run and tests (`application.yml`)
- [x] Confirm the app starts and connects to the database
- [x] Make the base test suite pass before starting feature work
- [ ] Decide and document the lightweight auth approach: `userId` and role via header or request parameter
- [x] Keep the package structure small: controller / service / repository / entity / dto

## 2. Database and schema

- [ ] Create `course` table
- [ ] Create `enrollment` table
- [ ] Add index on `enrollment(course_id, status)`
- [ ] Add index on `enrollment(student_id, requested_at)`
- [ ] Add unique partial index for one active enrollment per student per course
- [ ] Store enum values as strings, not ordinals

## 3. Course flow

- [ ] Implement course create API
- [ ] Validate course create request fields: title, description, price, capacity, start date, end date
- [ ] Implement course status change API
- [ ] Implement course list API with optional `status` filter
- [ ] Implement course detail API
- [ ] Return current active enrollment count from course detail
- [ ] Allow only `CREATOR` to create courses and change course status
- [ ] Enforce `DRAFT -> OPEN -> CLOSED` only
- [ ] Reject all other transitions explicitly
- [ ] Keep naming consistent between API, DTO, and domain, or document the mapping clearly

## 4. Enrollment flow

- [ ] Implement enrollment create API
- [ ] Implement enrollment confirm API
- [ ] Implement enrollment cancel API
- [ ] Implement my enrollments API
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
- [ ] Treat the service-level duplicate check as a fast-fail optimization; the database constraint is the final source of truth

## 6. Error handling

- [ ] Add one `@RestControllerAdvice`
- [ ] Define small, clear error codes
- [ ] Keep response structure consistent across success and error cases
- [ ] Use `COURSE_NOT_OPEN` for both `DRAFT` and `CLOSED`
- [ ] Return consistent errors for invalid state transitions
- [ ] Return `FORBIDDEN` for role or ownership failures
- [ ] Return `NOT_FOUND` for missing course or enrollment
- [ ] Map database exceptions such as `DataIntegrityViolationException` to business errors
- [ ] Avoid returning JPA entities directly from controllers

## 7. Tests

- [ ] Replace the placeholder `contextLoads` test with BE-A integration tests once datasource setup is in place
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
- [ ] Add a short Concurrency Design Summary
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

## 11. Scope control

- [ ] Do not spend time on optional items until all required APIs, tests, and docs are complete
- [ ] Treat cancellation-period restriction and creator-only student list as optional extras
- [ ] Do not add waitlist
- [ ] Do not add pagination unless everything required is already done
- [ ] Do not add Redis, queues, or extra infrastructure
- [ ] Do not add Spring Security for this take-home
- [ ] Keep the solution boring and reliable

## 12. Final check before submission

- [ ] Start from a clean database and run the app again
- [ ] Run the test suite from a clean state
- [ ] Verify all required APIs work end to end
- [ ] Verify no unexpected lazy loading or N+1 query behavior in course detail
- [ ] Re-read README once as if I were the reviewer
- [ ] Verify the repository has readable commit history and is ready to submit as a public URL
- [ ] Make sure the repository is public and runnable on `main`
