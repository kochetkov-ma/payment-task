package com.payment.balance.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import java.util.*

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
class JpaAuditingConfig {

    @Bean
    fun auditorProvider(): AuditorAware<String> {
        return AuditorAware<String> {
            // For balance service, use a simple system auditor
            // In a real implementation, this could be enhanced to track service calls
            Optional.of("balance-service")
        }
    }
}