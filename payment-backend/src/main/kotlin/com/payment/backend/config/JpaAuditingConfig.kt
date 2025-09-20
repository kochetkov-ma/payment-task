package com.payment.backend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.security.core.context.SecurityContextHolder
import java.util.*

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
class JpaAuditingConfig {

    @Bean
    fun auditorProvider(): AuditorAware<String> {
        return AuditorAware<String> {
            try {
                val authentication = SecurityContextHolder.getContext().authentication
                if (authentication != null && authentication.isAuthenticated && authentication.name != "anonymousUser") {
                    Optional.of(authentication.name)
                } else {
                    Optional.of("system")
                }
            } catch (e: Exception) {
                Optional.of("system")
            }
        }
    }
}