package io.github.blueberry44477.course_enrollment_system.controller;

import java.net.URI;
import java.sql.SQLException;

import io.github.blueberry44477.course_enrollment_system.exception.ResourceNotFoundException;
import io.github.blueberry44477.course_enrollment_system.model.Course;
import io.github.blueberry44477.course_enrollment_system.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController {
    private final CourseRepository repository;
    public record Input(String code, long price, int capacity, int level) {}

    @PostMapping
    public ResponseEntity<Course> create(@RequestBody Input input) {
        var saved = repository.save(new Course(null, input.code(), input.price(), input.capacity(), input.level()));
        return ResponseEntity.created(URI.create("/courses/" + saved.getId())).body(saved);
    }

    @GetMapping("/{id}")
    public Course get(@PathVariable long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Курс не знайдено"));
    }
}