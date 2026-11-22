package io.github.blueberry44477.course_enrollment_system.fuzz;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import io.github.blueberry44477.course_enrollment_system.dto.EnrollmentRequest;
import io.github.blueberry44477.course_enrollment_system.exception.EnrollmentRejectedException;
import io.github.blueberry44477.course_enrollment_system.model.Course;
import io.github.blueberry44477.course_enrollment_system.model.Enrollment;
import io.github.blueberry44477.course_enrollment_system.model.Student;
import io.github.blueberry44477.course_enrollment_system.repository.CourseRepository;
import io.github.blueberry44477.course_enrollment_system.repository.EnrollmentRepository;
import io.github.blueberry44477.course_enrollment_system.repository.StudentRepository;
import io.github.blueberry44477.course_enrollment_system.service.EnrollmentPolicy;
import io.github.blueberry44477.course_enrollment_system.service.EnrollmentService;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.code_intelligence.jazzer.junit.FuzzTest;

import java.util.Optional;

public class EnrollmentServiceFuzzTest {

    private final StudentRepository studentRepo = mock(StudentRepository.class);
    private final CourseRepository courseRepo = mock(CourseRepository.class);
    private final EnrollmentRepository enrollmentRepo = mock(EnrollmentRepository.class);
    private final EnrollmentPolicy policy = mock(EnrollmentPolicy.class);
    private final EnrollmentService service = new EnrollmentService(studentRepo, courseRepo, enrollmentRepo, policy);

    private void resetDoubles() {
        Mockito.reset(studentRepo, courseRepo, enrollmentRepo, policy);
    }

    @FuzzTest(maxDuration = "15s")
    void enroll_Fuzz(FuzzedDataProvider data) {
        resetDoubles();

        long studentId = data.consumeLong();
        long courseId = data.consumeLong();
        String reference = data.consumeBoolean() ? null : data.consumeString(100);
        boolean couponValid = data.consumeBoolean();

        if (data.consumeBoolean()) {
            when(studentRepo.findById(studentId)).thenReturn(Optional.of(
                    new Student(studentId, "S1", data.consumeInt(), data.consumeInt(), data.consumeBoolean())
            ));
        }
        if (data.consumeBoolean()) {
            when(courseRepo.findById(courseId)).thenReturn(Optional.of(
                    new Course(courseId, "C1", data.consumeLong(), data.consumeInt(), data.consumeInt())
            ));
        }

        when(enrollmentRepo.hasEnrollment(studentId, courseId)).thenReturn(data.consumeBoolean());
        when(enrollmentRepo.countOccupied(courseId)).thenReturn(data.consumeInt());
        when(policy.isAllowed(any(), any())).thenReturn(data.consumeBoolean());
        when(enrollmentRepo.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));
        try {
            service.enroll(new EnrollmentRequest(reference, studentId, courseId, couponValid));
        } catch (EnrollmentRejectedException | IllegalArgumentException expected) {
            // Normal
        } catch (RuntimeException unexpected) {
            fail("Unexpected exception in enroll(): " + unexpected.getClass().getName(), unexpected);
        }
    }

}
