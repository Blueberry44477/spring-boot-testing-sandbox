# Course Enrollment System — Testing Sandbox

This repository is a dedicated **Testing Sandbox** and **System Under Test (SUT)** built with Spring Boot. It serves as an experimental ground and reference implementation for exploring, comparing, and benchmarking various testing strategies and frameworks across the testing pyramid.

## Purpose

The primary goal of this sandbox is to demonstrate how to test a realistic, non-trivial layered architecture containing:
- **Database Persistence & Migrations** (MySQL, Spring Data JPA, Liquibase)
- **Business Logic & Entity Invariants** (Validation, unique constraints, state machine transitions)
- **External Network Dependencies** (HTTP calls to an external Policy API with mockable fallbacks)
- **Performance & Workload Characteristics**

---

## Tech Stack & Tools

- **Language & Runtime:** Java 25
- **Framework:** Spring Boot 4.1.1 (Spring MVC, Spring Data JPA)
- **Database:** MySQL 8.0, Liquibase
- **Testing Ecosystem:**
  - **Integration Testing:** JUnit 5, Testcontainers, WireMock
  - **Performance & Load Testing:** Gatling (Java DSL), k6
  - **API Exploration:** Postman, SpringDoc OpenAPI (Swagger UI)

---

## Test Suites in the Sandbox

### 1. Integration Testing

The integration test suite is deliberately divided into distinct layers to respect the **Single Responsibility Principle (SRP)** and optimize execution speed:

#### Repository Tests (`StudentRepositoryIT.java`)
- **Focus:** Validates custom SQL queries, JPA entity mapping, schema validations, and database constraints.
- **Approach:** Uses `@DataJpaTest` and `@AutoConfigureTestDatabase` alongside a **MySQL Testcontainer**.
- **Benefits:** Fast execution since the full web context and external service clients are omitted.

#### Service & Business Flow Tests (`EnrollmentServiceIT.java`)
- **Focus:** Validates complex enrollment workflows, entity state transitions (`ENROLLED` → `COMPLETED` / `CANCELLED`), and HTTP interactions with external services.
- **Approach:** Uses `@SpringBootTest(webEnvironment = RANDOM_PORT)` with a **MySQL Testcontainer** and a **WireMockContainer** to stub `/enrollment-policy`.
- **Benefits:** Ensures end-to-end integration across services, repositories, and HTTP clients mirroring a production environment.

**How to run integration tests:**
```bash
./mvnw clean verify
```
*(Requires Docker to spin up MySQL and WireMock containers).*

---

### 2. Performance & Load Testing

The repository includes realistic, multi-step user journeys to evaluate system throughput, latency percentiles, and resource utilization under various load conditions.

#### Gatling Java SDK (`MySimulation.java`)
Implements an end-to-end 6-step enrollment lifecycle (`POST /students` → `POST /courses` → `POST /enrollments` → `GET /enrollments/{id}` → `POST /complete` → `GET /enrollments/{id}`) using Gatling's modern Java DSL.

Key features:
- **Dynamic Data Feeders:** In-memory UUID feeders satisfying unique database constraints on each iteration.
- **Session Correlation:** Extracts and chains generated IDs (`studentId`, `courseId`, `enrollmentId`) through Gatling Expression Language (`#{var}`).
- **Fail-Fast Mechanics:** Uses `.exitHereIfFailed()` to stop virtual users immediately if an upstream step fails.
- **Group Timing:** Encapsulates the journey in `.group("Enrollment Workflow")` to measure total workflow duration.
- **Mock Policy Support:** Configurable via `policy.mock: true` in `application.yaml` to isolate performance runs from third-party outages.

**Supported Workload Profiles (selectable via CLI):**

| Profile | Command | Purpose | Workload Characteristics |
| :--- | :--- | :--- | :--- |
| **Smoke** | `./mvnw gatling:test -DtestType=smoke` | Sanity / Pipeline Check | 1 concurrent user, verifies 100% OK and no regressions |
| **Load** | `./mvnw gatling:test -DtestType=load` | Steady-State Capacity | Ramps to 15 users/sec over 30s + 2 min constant rate (SLA: $P_{95} < 500\text{ms}$) |
| **Spike** | `./mvnw gatling:test -DtestType=spike` | Surge & Recovery | 20 users/sec baseline with an abrupt surge of 500 users at once |

**Running against custom hosts:**
```bash
./mvnw gatling:test -DtestType=load -DbaseUrl=http://staging-host:8080
```

#### k6 (`k6/`)
JavaScript-based performance tests located under `k6/` providing an alternative load generator for cross-tool comparison.

**How to run k6 tests:**
```bash
k6 run k6/test.js
```

---

## Running the Application Locally

To boot the application:
   ```bash
   ./mvnw spring-boot:run
   ```
   *(Swagger UI will be available at `http://localhost:8080/swagger-ui.html`).*
