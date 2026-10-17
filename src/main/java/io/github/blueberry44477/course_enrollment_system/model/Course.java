package io.github.blueberry44477.course_enrollment_system.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "courses")
@Getter
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false)
    private long price;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "course_level", nullable = false)
    private int level;

    @PrePersist
    @PreUpdate
    private void validate() {
        if (code == null || code.isEmpty() || code.length() > 40) {
            throw new IllegalArgumentException("Invalid code");
        }
        if (price < 100 || price > 10_000_000) {
            throw new IllegalArgumentException("Invalid price");
        }
        if (capacity < 1 || capacity > 100) {
            throw new IllegalArgumentException("Invalid capacity");
        }
        if (level < 1 || level > 3) {
            throw new IllegalArgumentException("Invalid level");
        }
    }
}
