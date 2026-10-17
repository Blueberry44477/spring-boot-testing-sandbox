package io.github.blueberry44477.course_enrollment_system.controller;

import java.net.URI;
import java.sql.SQLException;

import io.github.blueberry44477.course_enrollment_system.exception.ResourceNotFoundException;
import io.github.blueberry44477.course_enrollment_system.model.Student;
import io.github.blueberry44477.course_enrollment_system.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/students")
@RequiredArgsConstructor
public class StudentController {
    private final StudentRepository repository;

    public record Input(String code, int age, int entryScore, boolean regular) {}

    @PostMapping
    public ResponseEntity<Student> create(@RequestBody Input input) {
        var saved = repository.save(new Student(null, input.code(), input.age(), input.entryScore(), input.regular()));
        return ResponseEntity.created(URI.create("/students/" + saved.getId())).body(saved);
    }

    @GetMapping("/{id}")
    public Student get(@PathVariable long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Слухача не знайдено"));
    }
}
