package io.github.blueberry44477.course_enrollment_system.property;

import io.github.blueberry44477.course_enrollment_system.dto.EnrollmentRequest;
import io.github.blueberry44477.course_enrollment_system.dto.EnrollmentStatus;
import io.github.blueberry44477.course_enrollment_system.exception.EnrollmentRejectedException;
import io.github.blueberry44477.course_enrollment_system.model.Course;
import io.github.blueberry44477.course_enrollment_system.model.Enrollment;
import io.github.blueberry44477.course_enrollment_system.model.Student;
import io.github.blueberry44477.course_enrollment_system.repository.CourseRepository;
import io.github.blueberry44477.course_enrollment_system.repository.EnrollmentRepository;
import io.github.blueberry44477.course_enrollment_system.repository.StudentRepository;
import io.github.blueberry44477.course_enrollment_system.service.EnrollmentPolicy;
import io.github.blueberry44477.course_enrollment_system.service.EnrollmentService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.lifecycle.BeforeTry;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class EnrollmentServicePropertyTest {

    record EnrollmentScenario(
            Student student,
            Course course,
            EnrollmentRequest request,
            int occupiedSeats,
            boolean alreadyEnrolled,
            boolean policyAllowed
    ) {}

    private StudentRepository studentRepository;
    private CourseRepository courseRepository;
    private EnrollmentRepository enrollmentRepository;
    private EnrollmentPolicy enrollmentPolicy;

    private EnrollmentService enrollmentService;

    @BeforeTry
    void setUpTry() {
        // Create fresh mocks and a fresh service instance per try so shrinking never reuses stale @InjectMocks references.
        studentRepository = mock(StudentRepository.class);
        courseRepository = mock(CourseRepository.class);
        enrollmentRepository = mock(EnrollmentRepository.class);
        enrollmentPolicy = mock(EnrollmentPolicy.class);
        enrollmentService = new EnrollmentService(
                studentRepository,
                courseRepository,
                enrollmentRepository,
                enrollmentPolicy
        );
    }

    @Property
    void isEligible_shouldSuccess_forCourseRules(
            @ForAll @IntRange(min = 0, max = 120) int age,
            @ForAll @IntRange(min = 0, max = 100) int entryScore,
            @ForAll @IntRange(min = -1, max = 5) int level,
            @ForAll boolean regular
    ) {
        boolean actual = enrollmentService.isEligible(age, entryScore, level, regular);

        boolean expected = switch(level) {
            case 1 -> entryScore >= 40;
            case 2 -> entryScore >= 60;
            case 3 -> entryScore >= 80 && age >= 18;
            default -> false;
        };

        assertEquals(expected, actual, () -> "Mismatch for age=%d, score=%d, level=%d, regular=%s"
                .formatted(age, entryScore, level, regular));
    }

    // Refund invariant.
    @Property
    void calculateRefund_shouldSuccess_forVariousBounds(
            @ForAll @LongRange(min = -1_000, max = 10_001_000) long prepaid,
            @ForAll @IntRange(min = -30, max = 365) int daysBeforeStart
    ) {
        if (prepaid < 0 || prepaid > 10_000_000) {
            assertThrows(IllegalArgumentException.class,
                    () -> enrollmentService.calculateRefund(prepaid, daysBeforeStart));
        } else {
            long refund = enrollmentService.calculateRefund(prepaid, daysBeforeStart);
            assertEquals(prepaid, refund);
        }
    }

    @Provide
    Arbitrary<EnrollmentScenario> enrollmentScenarios() {
        Arbitrary<Student> students = Combinators.combine(
                Arbitraries.strings().alpha().numeric().ofMinLength(3).ofMaxLength(20),
                Arbitraries.integers().between(14, 100),
                Arbitraries.integers().between(0, 100),
                Arbitraries.of(true, false)
        ).as((code, age, score, regular) -> new Student(1L, code, age, score, regular));

        Arbitrary<Course> courses = Combinators.combine(
                Arbitraries.strings().alpha().numeric().ofMinLength(3).ofMaxLength(20),
                Arbitraries.longs().between(100L, 10_000_000L),
                Arbitraries.integers().between(1, 50),
                Arbitraries.integers().between(1, 3)
        ).as((code, price, cap, level) -> new Course(10L, code, price, cap, level));

        Arbitrary<EnrollmentRequest> requests = Combinators.combine(
                Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(40),
                Arbitraries.of(true, false)
        ).as((ref, coupon) -> new EnrollmentRequest(ref, 1L, 10L, coupon));

        Arbitrary<Integer> occupied = Arbitraries.integers().between(0, 60);
        Arbitrary<Boolean> booleans = Arbitraries.of(true, false);

        return Combinators.combine(students, courses, requests, occupied, booleans, booleans)
                .as(EnrollmentScenario::new);
    }

    @Property
    void rejectedEnrollmentNeverPersistsState(
            @ForAll("enrollmentScenarios") EnrollmentScenario scenario
    ) {
        // Незалежні doubles для кожного окремого try (важливо для shrinking!)
        when(studentRepository.findById(1L)).thenReturn(Optional.of(scenario.student()));
        when(courseRepository.findById(10L)).thenReturn(Optional.of(scenario.course()));
        when(enrollmentRepository.hasEnrollment(1L, 10L)).thenReturn(scenario.alreadyEnrolled());
        when(enrollmentRepository.countOccupied(10L)).thenReturn(scenario.occupiedSeats());
        when(enrollmentPolicy.isAllowed(scenario.student(), scenario.course())).thenReturn(scenario.policyAllowed());
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean eligible = enrollmentService.isEligible(
                scenario.student().getAge(),
                scenario.student().getEntryScore(),
                scenario.course().getLevel(),
                scenario.student().isRegular()
        );

        boolean shouldReject = scenario.alreadyEnrolled()
                || scenario.occupiedSeats() >= scenario.course().getCapacity()
                || !eligible
                || !scenario.policyAllowed();

        if (shouldReject) {
            assertThrows(EnrollmentRejectedException.class, () -> enrollmentService.enroll(scenario.request()));
            verify(enrollmentRepository, never()).save(any());
        } else {
            Enrollment saved = enrollmentService.enroll(scenario.request());
            assertEquals(EnrollmentStatus.ENROLLED, saved.getStatus());
            assertTrue(saved.getTotal() >= 0);
            verify(enrollmentRepository, times(1)).save(any());
        }
    }


}
