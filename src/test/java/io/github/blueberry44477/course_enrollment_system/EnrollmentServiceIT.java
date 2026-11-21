package io.github.blueberry44477.course_enrollment_system;

import static org.junit.jupiter.api.Assertions.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.wiremock.integrations.testcontainers.WireMockContainer;

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

import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers 
public class EnrollmentServiceIT {

    @Container 
    @ServiceConnection 
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @Container
    static final WireMockContainer wiremock = new WireMockContainer("wiremock/wiremock:latest");

    @Autowired
    private EnrollmentService enrollmentService;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private EnrollmentRepository enrollmentRepository;

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
        enrollmentRepository.deleteAll();
        studentRepository.deleteAll();
        courseRepository.deleteAll();
    }

    @Test
    void service_enroll_shouldSuccess_whenAllConditionsMet() {
        Student student = studentRepository.save(new Student(null, "STU-" + UUID.randomUUID(), 20, 85, true));
        Course course = courseRepository.save(new Course(null, "CRS-" + UUID.randomUUID(), 5000, 10, 1));

        stubFor(post(urlEqualTo("/enrollment-policy"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"allowed\": true}").withStatus(200)));
        
        EnrollmentRequest request = new EnrollmentRequest("REF-" + UUID.randomUUID(), student.getId(), course.getId(), false);
        Enrollment enrollment = enrollmentService.enroll(request);
        
        assertNotNull(enrollment.getId());
        assertEquals(EnrollmentStatus.ENROLLED, enrollment.getStatus());
        verify(1, postRequestedFor(urlEqualTo("/enrollment-policy")));
    }

    @Test
    void service_enroll_shouldFail_whenPolicyRejects() {
        Student student = studentRepository.save(new Student(null, "STU-" + UUID.randomUUID(), 20, 85, true));
        Course course = courseRepository.save(new Course(null, "CRS-" + UUID.randomUUID(), 5000, 10, 1));

        stubFor(post(urlEqualTo("/enrollment-policy"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"allowed\": false}").withStatus(200)));
        
        EnrollmentRequest request = new EnrollmentRequest("REF-" + UUID.randomUUID(), student.getId(), course.getId(), false);
        
        assertThrows(EnrollmentRejectedException.class, () -> enrollmentService.enroll(request));
        assertEquals(0, enrollmentRepository.count());
    }

    @Test
    // @DisplayName("")
    void service_enroll_shouldFail_whenCourseCapacityReached() {
        Student student1 = studentRepository.save(new Student(null, "STU-" + UUID.randomUUID(), 20, 85, true));
        Student student2 = studentRepository.save(new Student(null, "STU-" + UUID.randomUUID(), 22, 90, true));
        Course course = courseRepository.save(new Course(null, "CRS-" + UUID.randomUUID(), 5000, 
            1, 1)); // capacity = 1

        stubFor(post(urlEqualTo("/enrollment-policy"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"allowed\": true}").withStatus(200)));

        // First student successfuly takes the place.
        enrollmentService.enroll(new EnrollmentRequest("REF-1", student1.getId(), course.getId(), false));
        
        // Second student enrol should fail.
        EnrollmentRequest request2 = new EnrollmentRequest("REF-2", student2.getId(), course.getId(), false);
        assertThrows(EnrollmentRejectedException.class, () -> enrollmentService.enroll(request2));
        
        // Request should be made only for the first student.
        verify(1, postRequestedFor(urlEqualTo("/enrollment-policy")));
    }

    @Test
    void service_enroll_shouldFail_whenAgeRuleFails() {
        // level 3: score >= 80 ТА age >= 18.
        // Student has score 85, and age 16.
        Student youngStudent = studentRepository.save(new Student(null, "STU-" + UUID.randomUUID(), 16, 85, true));
        Course advancedCourse = courseRepository.save(new Course(null, "CRS-" + UUID.randomUUID(), 5000, 10, 3)); // level 3

        EnrollmentRequest request = new EnrollmentRequest("REF-" + UUID.randomUUID(), youngStudent.getId(), advancedCourse.getId(), false);
        
        assertThrows(EnrollmentRejectedException.class, () -> enrollmentService.enroll(request));
        
        verify(0, postRequestedFor(urlEqualTo("/enrollment-policy")));
    }

    @Test
    void service_complete_shouldChangeStatusToCompleted() {
        Student student = studentRepository.save(new Student(null, "STU-" + UUID.randomUUID(), 20, 85, true));
        Course course = courseRepository.save(new Course(null, "CRS-" + UUID.randomUUID(), 5000, 10, 1));
        stubFor(post(urlEqualTo("/enrollment-policy"))
                .willReturn(aResponse().withBody("{\"allowed\": true}").withStatus(200)));
        
        Enrollment enrollment = enrollmentService.enroll(new EnrollmentRequest("REF-" + UUID.randomUUID(), student.getId(), course.getId(), false));
        
        enrollmentService.complete(enrollment.getId());
        
        Enrollment completed = enrollmentRepository.findById(enrollment.getId()).orElseThrow();
        assertEquals(EnrollmentStatus.COMPLETED, completed.getStatus());
    }

    @Test
    void service_cancel_shouldFail_whenStatusIsCompleted() {
        Student student = studentRepository.save(new Student(null, "STU-" + UUID.randomUUID(), 20, 85, true));
        Course course = courseRepository.save(new Course(null, "CRS-" + UUID.randomUUID(), 5000, 10, 1));
        stubFor(post(urlEqualTo("/enrollment-policy"))
                .willReturn(aResponse().withBody("{\"allowed\": true}").withStatus(200)));
        
        Enrollment enrollment = enrollmentService.enroll(new EnrollmentRequest("REF-" + UUID.randomUUID(), student.getId(), course.getId(), false));
        enrollmentService.complete(enrollment.getId());
        
        assertThrows(EnrollmentRejectedException.class, () -> enrollmentService.cancel(enrollment.getId(), 5));
    }
}
