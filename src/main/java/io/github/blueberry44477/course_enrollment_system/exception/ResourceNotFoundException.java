package io.github.blueberry44477.course_enrollment_system.exception;

public class ResourceNotFoundException extends IllegalArgumentException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}