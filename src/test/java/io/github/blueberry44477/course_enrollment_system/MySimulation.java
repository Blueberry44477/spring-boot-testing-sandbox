package io.github.blueberry44477.course_enrollment_system;

import io.gatling.javaapi.core.Assertion;
import io.gatling.javaapi.core.PopulationBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class MySimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8080");
    // -DtestType=smoke | load | spike
    private static final String TEST_TYPE = System.getProperty("testType", "smoke").toLowerCase();

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json")
            .userAgentHeader("Gatling/3.16");

    private final Iterator<Map<String, Object>> feeder = Stream.generate(() -> {
        String shortId = UUID.randomUUID().toString().substring(0, 8);
        int score = ThreadLocalRandom.current().nextInt(80, 96); // [80; 95]
        return Map.<String, Object>of(
                "shortId", shortId,
                "score", score
        );
    }).iterator();

    private final ScenarioBuilder scn = scenario("Enrollment Workflow Journey")
            .feed(feeder)
            .group("Enrollment Workflow").on(
                    exec(
                            http("Step 1: POST /students - Create Student")
                                    .post("/students")
                                    .body(StringBody("""
                                            {
                                                "code": "S-#{shortId}",
                                                "age": 20,
                                                "entryScore": #{score},
                                                "regular": true
                                            }
                                    """)).asJson()
                                    .check(status().is(201))
                                    .check(jsonPath("$.id").ofLong().saveAs("studentId"))
                    )
                    .exitHereIfFailed(),

                    exec(
                            http("Step 2: POST /courses - Create Course")
                                    .post("/courses")
                                    .body(StringBody("""
                                            {
                                                "code": "C-#{shortId}",
                                                "price": 5000,
                                                "capacity": 100,
                                                "level": 1
                                            }
                                    """)).asJson()
                                    .check(status().is(201))
                                    .check(jsonPath("$.id").ofLong().saveAs("courseId"))
                    )
                    .exitHereIfFailed(),

                    exec(
                            http("Step 3: POST /enrollments - Create Enrollment")
                                    .post("/enrollments")
                                    .body(StringBody("""
                                            {
                                                "reference": "E-#{shortId}",
                                                "studentId": #{studentId},
                                                "courseId": #{courseId},
                                                "couponValid": false
                                            }
                                    """)).asJson()
                                    .check(status().is(201))
                                    .check(jsonPath("$.status").is("ENROLLED"))
                                    .check(jsonPath("$.id").ofLong().saveAs("enrollmentId"))
                    )
                    .exitHereIfFailed(),

                    exec(
                            http("Step 4: GET /enrollments/{id} - Verify ENROLLED State")
                                    .get("/enrollments/#{enrollmentId}")
                                    .check(status().is(200))
                                    .check(jsonPath("$.status").is("ENROLLED"))
                    )
                    .exitHereIfFailed(),

                    exec(
                            http("Step 5: POST /enrollments/{id}/complete - Complete Course")
                                    .post("/enrollments/#{enrollmentId}/complete")
                                    .check(status().is(200))
                                    .check(jsonPath("$.status").is("COMPLETED"))
                    )
                    .exitHereIfFailed(),

                    exec(
                            http("Step 6: GET /enrollments/{id} - Verify Final COMPLETED State")
                                    .get("/enrollments/#{enrollmentId}")
                                    .check(status().is(200))
                                    .check(jsonPath("$.status").is("COMPLETED"))
                    )
            )
            .pause(Duration.ofSeconds(1)); // Pacing / Think time між ітераціями

    private PopulationBuilder buildInjectionProfile() {
        return switch (TEST_TYPE) {
            case "smoke" ->
                scn.injectOpen(atOnceUsers(1));

            case "load" ->
                scn.injectOpen(
                        rampUsersPerSec(1).to(15).during(Duration.ofSeconds(30)),
                        constantUsersPerSec(15).during(Duration.ofMinutes(2))
                );

            case "spike" ->
                scn.injectOpen(
                        constantUsersPerSec(20).during(Duration.ofSeconds(10)),
                        // At once generates given amount of users in one millisecond.
                        // But in real world because of ping, connection, DNS & TLS handshakes
                        // it will extend to a couple of seconds.
                        atOnceUsers(500),
                        constantUsersPerSec(20).during(Duration.ofSeconds(30))
                );

            default -> throw new IllegalArgumentException("Unknown testType: '" + TEST_TYPE +
                    "'. Allowed values: -DtestType=smoke | load | spike");
        };
    }

    private List<Assertion> buildAssertions() {
        return switch (TEST_TYPE) {
            case "smoke" -> List.of(
                    global().failedRequests().percent().is(0.0),
                    global().responseTime().percentile3().lt(2000)
            );
            case "load" -> List.of(
                    global().failedRequests().percent().lt(1.0),
                    global().responseTime().percentile3().lt(500)
            );
            case "spike" -> List.of(
                    global().failedRequests().percent().lt(5.0),
                    global().responseTime().percentile3().lt(1500)
            );
            default -> List.of(global().failedRequests().percent().lt(1.0));
        };
    }

    public MySimulation() {
        setUp(buildInjectionProfile())
                .protocols(httpProtocol)
                .assertions(buildAssertions());
    }
}
