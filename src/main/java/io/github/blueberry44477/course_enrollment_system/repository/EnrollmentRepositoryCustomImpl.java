package io.github.blueberry44477.course_enrollment_system.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class EnrollmentRepositoryCustomImpl implements EnrollmentRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchByReference(String reference) {
        String sql = "SELECT id, reference, total, status FROM enrollments WHERE reference = '" + reference + "'";
        List<Object[]> rows = entityManager.createNativeQuery(sql).getResultList();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row[0]);
            item.put("reference", row[1]);
            item.put("total", row[2]);
            item.put("status", row[3]);
            result.add(item);
        }
        return result;
    }
}
