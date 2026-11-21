package io.github.blueberry44477.course_enrollment_system.repository;

import java.util.List;
import java.util.Map;

public interface EnrollmentRepositoryCustom {
    List<Map<String, Object>> searchByReference(String reference);
}
