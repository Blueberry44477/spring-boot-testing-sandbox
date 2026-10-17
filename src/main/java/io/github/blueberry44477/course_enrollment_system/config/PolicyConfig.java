package io.github.blueberry44477.course_enrollment_system.config;

import io.github.blueberry44477.course_enrollment_system.service.EnrollmentPolicy;
import io.github.blueberry44477.course_enrollment_system.service.HttpEnrollmentPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.time.Duration;

@Configuration
public class PolicyConfig {

    @Bean
    @ConditionalOnProperty(name = "policy.mock", havingValue = "false", matchIfMissing = true)
    public EnrollmentPolicy enrollmentPolicy(
            @Value("${policy.url}") String url,
            @Value("${policy.timeout}") Duration timeout) {
        return new HttpEnrollmentPolicy(URI.create(url), timeout);
    }

    @Bean
    @ConditionalOnProperty(name = "policy.mock", havingValue = "true")
    public EnrollmentPolicy mockEnrollmentPolicy() {
        return (student, course) -> true;
    }
}
