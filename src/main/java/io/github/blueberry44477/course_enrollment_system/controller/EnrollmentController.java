package io.github.blueberry44477.course_enrollment_system.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;

import io.github.blueberry44477.course_enrollment_system.dto.EnrollmentRequest;
import io.github.blueberry44477.course_enrollment_system.dto.EnrollmentStatus;
import io.github.blueberry44477.course_enrollment_system.exception.ResourceNotFoundException;
import io.github.blueberry44477.course_enrollment_system.model.Enrollment;
import io.github.blueberry44477.course_enrollment_system.repository.EnrollmentRepository;
import io.github.blueberry44477.course_enrollment_system.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {
    private final EnrollmentService service;
    private final EnrollmentRepository repository;

    public record CancelInput(int daysBeforeStart) {}
    public record EditInput(String reference, Long total, Long prepaid, EnrollmentStatus status) {}

    @PostMapping
    public ResponseEntity<Enrollment> create(@RequestBody EnrollmentRequest input) {
        var saved = service.enroll(input);
        return ResponseEntity.created(URI.create("/enrollments/" + saved.getId())).body(saved);
    }

    @GetMapping("/{id}")
    public Enrollment get(@PathVariable long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Заявку не знайдено"));
    }

    @PostMapping("/{id}/complete")
    public Enrollment complete(@PathVariable long id) {
        return service.complete(id);
    }

    @PostMapping("/{id}/cancel")
    public Enrollment cancel(@PathVariable long id, @RequestBody CancelInput input) {
        return service.cancel(id, input.daysBeforeStart());
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam String reference) {
        return repository.searchByReference(reference);
    }

    // Expected only to change reference, but changes other fields also.
    @PatchMapping("/{id}")
    public Enrollment edit(@PathVariable long id, @RequestBody EditInput input) {
        Enrollment enrollment = get(id);
        if (input.reference() != null) {
            enrollment.setReference(input.reference());
        }
        if (input.total() != null) {
            enrollment.setTotal(input.total());
        }
        if (input.prepaid() != null) {
            enrollment.setPrepaid(input.prepaid());
        }
        if (input.status() != null) {
            enrollment.setStatus(input.status());
        }
        return repository.save(enrollment);
    }
}