package io.github.blueberry44477.course_enrollment_system;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.github.blueberry44477.course_enrollment_system.dto.EnrollmentRequest;
import io.github.blueberry44477.course_enrollment_system.dto.EnrollmentStatus;
import io.github.blueberry44477.course_enrollment_system.exception.EnrollmentRejectedException;
import io.github.blueberry44477.course_enrollment_system.model.Course;
import io.github.blueberry44477.course_enrollment_system.model.Enrollment;
import io.github.blueberry44477.course_enrollment_system.model.Student;
import io.github.blueberry44477.course_enrollment_system.repository.CourseRepository;
import io.github.blueberry44477.course_enrollment_system.repository.EnrollmentRepository;
import io.github.blueberry44477.course_enrollment_system.repository.StudentRepository;
import io.github.blueberry44477.course_enrollment_system.service.EnrollmentService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.wiremock.integrations.testcontainers.WireMockContainer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.Random.class)
@Tag("regression")
class EnrollmentRegressionIT {

    @Container
    @ServiceConnection
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @Container
    static final WireMockContainer wiremock = new WireMockContainer("wiremock/wiremock:latest");

    @Autowired
    private EnrollmentService service;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("policy.url", wiremock::getBaseUrl);
        registry.add("policy.mock", () -> "false");
    }

    @BeforeAll
    static void setupWireMockClient() {
        WireMock.configureFor(wiremock.getHost(), wiremock.getPort());
    }

    @BeforeEach
    void resetData() {
        WireMock.reset();
        stubFor(post(urlEqualTo("/enrollment-policy"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"allowed\": true}")
                        .withStatus(200)));

        enrollmentRepository.deleteAll();
        studentRepository.deleteAll();
        courseRepository.deleteAll();
    }

    private Student student(boolean regular) {
        return studentRepository.save(new Student(null, "STU-" + UUID.randomUUID(), 20, 90, regular));
    }

    private Course course() {
        return courseRepository.save(new Course(null, "CRS-" + UUID.randomUUID(), 100000L, 10, 1));
    }

    private EnrollmentRequest request(Student student, Course course, boolean coupon) {
        return new EnrollmentRequest("REF-" + UUID.randomUUID(), student.getId(), course.getId(), coupon);
    }

    @Test
    @Tag("smoke")
    @DisplayName("Зарахування зберігається у БД")
    void service_enroll_shouldPersist() {
        Enrollment saved = service.enroll(request(student(false), course(), false));

        Enrollment inDb = enrollmentRepository.findById(saved.getId()).orElseThrow();
        assertEquals(saved.getId(), inDb.getId());
        assertEquals(saved.getReference(), inDb.getReference());
        assertEquals(EnrollmentStatus.ENROLLED, inDb.getStatus());
    }

    @Test
    @Tag("smoke")
    @DisplayName("Скасування повертає передоплату")
    void service_cancel_shouldReturnPrepay() {
        Enrollment saved = service.enroll(request(student(false), course(), false));
        Enrollment cancelled = service.cancel(saved.getId(), 7);

        Enrollment inDb = enrollmentRepository.findById(saved.getId()).orElseThrow();

        assertEquals(EnrollmentStatus.CANCELLED, cancelled.getStatus());
        assertEquals(cancelled.getPrepaid(), cancelled.getRefund());

        assertEquals(EnrollmentStatus.CANCELLED, inDb.getStatus());
    }

    @Test
    @DisplayName("Картка слухача зберігає вік")
    void service_save_shouldPersistAge() {
        String code = "STU-" + UUID.randomUUID().toString().substring(0, 8);
        Student saved = studentRepository.save(new Student(null, code, 20, 90, false));

        Student retrieved = studentRepository.findById(saved.getId()).orElseThrow();
        assertEquals(20, retrieved.getAge());
    }

    @Test
    @DisplayName("Картка слухача зберігає вступний бал")
    void service_save_shouldPersistScore() {
        String code = "STU-" + UUID.randomUUID().toString().substring(0, 8);
        Student saved = studentRepository.save(new Student(null, code, 25, 85, true));

        Student inDb = studentRepository.findById(saved.getId()).orElseThrow();
        assertEquals(85, inDb.getEntryScore());
    }

    @Test
    @DisplayName("Зарахування після зовнішнього дозволу")
    void enrollmentAfterPolicyCheck() {
        Course course = course();
        Student student = student(false);
        EnrollmentRequest req = request(student, course, false);

        stubFor(post(urlEqualTo("/enrollment-policy"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"allowed\": true}")
                        .withFixedDelay(200)));

        try (var executor = Executors.newSingleThreadExecutor()) {
            CompletableFuture<Enrollment> operation = CompletableFuture.supplyAsync(
                    () -> service.enroll(req), executor
            );

            // Condition-based wait: await background completion deterministically
            Enrollment saved = operation.join();

            assertNotNull(saved.getId());
            assertEquals(req.getReference(), saved.getReference());
            assertEquals(100000L, saved.getTotal());
            assertEquals(EnrollmentStatus.ENROLLED, saved.getStatus());

            Enrollment inDb = enrollmentRepository.findById(saved.getId()).orElseThrow();
            assertEquals(req.getReference(), inDb.getReference());
        }
    }

    /**
     * FIX for Non-deterministic Query Order:
     * Added ORDER BY reference ASC in SQL query and sorted expected list.
     */
    @Test
    @DisplayName("Вибірка містить дві заявки курсу")
    void enrollmentSelection() {
        Course course = course();
        String prefix = "REF-" + UUID.randomUUID().toString().substring(0, 8);
        List<String> references = List.of(prefix + "-A", prefix + "-B");
        List<String> fixtureOrder = Boolean.getBoolean("regression.reverseFixture") ? references.reversed() : references;

        for (String reference : fixtureOrder) {
            service.enroll(new EnrollmentRequest(reference, student(false).getId(), course.getId(), false));
        }

        List<String> actual = jdbcTemplate.queryForList(
                "SELECT reference FROM enrollments WHERE course_id = ? ORDER BY reference ASC",
                String.class,
                course.getId()
        );
        List<String> expected = references.stream().sorted().toList();
        assertEquals(expected, actual);
    }

    @Test
    @Tag("smoke")
    @DisplayName("Завершення зберігає стан заявки")
    void completion() {
        Enrollment saved = service.enroll(request(student(false), course(), false));
        Enrollment completed = service.complete(saved.getId());

        assertEquals(EnrollmentStatus.COMPLETED, completed.getStatus());
        Enrollment inDb = enrollmentRepository.findById(saved.getId()).orElseThrow();
        assertEquals(EnrollmentStatus.COMPLETED, inDb.getStatus());
    }

    @Test
    @DisplayName("Повторне зарахування відхиляється")
    void duplicateEnrollment() {
        Student student = student(false);
        Course course = course();
        Enrollment saved = service.enroll(request(student, course, false));

        assertThrows(EnrollmentRejectedException.class, () -> service.enroll(request(student, course, false)));
        assertEquals(1, enrollmentRepository.countOccupied(course.getId()));

        Enrollment inDb = enrollmentRepository.findById(saved.getId()).orElseThrow();
        assertEquals(saved.getId(), inDb.getId());
    }

    @Test
    @DisplayName("Зовнішня відмова не створює заявку")
    void policyRejection() {
        Course course = course();
        Student student = student(false);
        EnrollmentRequest req = request(student, course, false);

        stubFor(post(urlEqualTo("/enrollment-policy"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"allowed\": false}")
                        .withStatus(200)));

        assertThrows(EnrollmentRejectedException.class, () -> service.enroll(req));
        assertEquals(0, enrollmentRepository.countOccupied(course.getId()));
    }

    @Test
    @DisplayName("Повторне скасування не змінює заявку")
    void repeatedCancellation() {
        Enrollment saved = service.enroll(request(student(false), course(), false));
        Enrollment cancelled = service.cancel(saved.getId(), 7);

        assertThrows(EnrollmentRejectedException.class, () -> service.cancel(saved.getId(), 7));

        Enrollment inDb = enrollmentRepository.findById(saved.getId()).orElseThrow();
        assertEquals(EnrollmentStatus.CANCELLED, inDb.getStatus());
    }
}
