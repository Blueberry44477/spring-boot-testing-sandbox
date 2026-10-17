package io.github.blueberry44477.course_enrollment_system.service;

import io.github.blueberry44477.course_enrollment_system.dto.EnrollmentRequest;
import io.github.blueberry44477.course_enrollment_system.dto.EnrollmentStatus;
import io.github.blueberry44477.course_enrollment_system.exception.EnrollmentRejectedException;
import io.github.blueberry44477.course_enrollment_system.model.Course;
import io.github.blueberry44477.course_enrollment_system.model.Enrollment;
import io.github.blueberry44477.course_enrollment_system.model.Student;
import io.github.blueberry44477.course_enrollment_system.repository.CourseRepository;
import io.github.blueberry44477.course_enrollment_system.repository.EnrollmentRepository;
import io.github.blueberry44477.course_enrollment_system.repository.StudentRepository;
import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor 
public class EnrollmentService {
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentPolicy enrollmentPolicy;

    public long calculateTotal(long price, boolean regular, boolean couponValid) {
        // Якщо правило не передбачає знижки — її немає.
        return price;
    }

    public boolean isEligible(int age, int entryScore, int level, boolean regular) {
        if (level == 1) {
            return entryScore >= 40;
        } else if (level == 2) {
            return entryScore >= 60;
        } else if (level == 3) {
            return entryScore >= 80 && age >= 18;
        }
        return false; // Invalid level or unhandled
    }

    public long calculateRefund(long prepaid, int daysBeforeStart) {
        if (prepaid < 0 || prepaid > 10_000_000) {
            throw new IllegalArgumentException("Invalid prepaid amount");
        }
        // Якщо не змінює повернення — повертається вся передоплата.
        return prepaid;
    }

    @Transactional
    public Enrollment enroll(@Valid @NotNull EnrollmentRequest request) {
        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));

        if (enrollmentRepository.hasEnrollment(student.getId(), course.getId())) {
            throw new EnrollmentRejectedException("Student already has an active enrollment for this course");
        }

        int occupied = enrollmentRepository.countOccupied(course.getId());
        if (occupied >= course.getCapacity()) {
            throw new EnrollmentRejectedException("Course capacity reached");
        }

        if (!isEligible(student.getAge(), student.getEntryScore(), course.getLevel(), student.isRegular())) {
            throw new EnrollmentRejectedException("Student not eligible for this course level");
        }

        if (!enrollmentPolicy.isAllowed(student, course)) {
            throw new EnrollmentRejectedException("Policy rejected enrollment");
        }

        long total = calculateTotal(course.getPrice(), student.isRegular(), request.isCouponValid());
        
        Enrollment enrollment = new Enrollment(
                null,
                request.getReference(),
                student.getId(),
                course.getId(),
                EnrollmentStatus.ENROLLED,
                total,
                total,
                0L,
                request.isCouponValid()
        );

        return enrollmentRepository.save(enrollment);
    }

    @Transactional
    public Enrollment complete(Long id) {
        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Enrollment not found"));
        
        if (enrollment.getStatus() != EnrollmentStatus.ENROLLED) {
            throw new EnrollmentRejectedException("Cannot complete an enrollment that is not ENROLLED");
        }
        int affected = enrollmentRepository.updateStatus(id, EnrollmentStatus.COMPLETED, enrollment.getRefund());
        if (affected == 0) {
            throw new EnrollmentRejectedException("Enrollment not found or not in ENROLLED status");
        }
        enrollment.setStatus(EnrollmentStatus.COMPLETED);
        return enrollment;
    }

    @Transactional
    public Enrollment cancel(Long id, int daysBeforeStart) {
        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Enrollment not found"));
       
        if (enrollment.getStatus() != EnrollmentStatus.ENROLLED) {
            throw new EnrollmentRejectedException("Cannot cancel an enrollment that is not ENROLLED");
        }
        
        long refund = calculateRefund(enrollment.getPrepaid(), daysBeforeStart);
        int affected = enrollmentRepository.updateStatus(id, EnrollmentStatus.CANCELLED, refund);
        if (affected == 0) {
            throw new EnrollmentRejectedException("Enrollment not found or not in ENROLLED status");
        }
        enrollment.setStatus(EnrollmentStatus.CANCELLED);
        enrollment.setRefund(refund);
        return enrollment;
    }
}
