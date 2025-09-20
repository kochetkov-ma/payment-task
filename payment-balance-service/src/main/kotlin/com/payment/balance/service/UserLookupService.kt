package com.payment.balance.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.util.*

@Service
class UserLookupService(
    private val restTemplate: RestTemplate = RestTemplate()
) {

    @Value("\${payment.backend.url:http://localhost:8080}")
    private lateinit var paymentBackendUrl: String

    fun findUsernameByUserId(userId: UUID): String? {
        return try {
            val response = restTemplate.getForObject(
                "$paymentBackendUrl/api/internal/users/$userId/username",
                String::class.java
            )
            response
        } catch (e: Exception) {
            // For now, return a mock username based on userId
            // In a real implementation, this would be handled differently
            "user_${userId.toString().substring(0, 8)}"
        }
    }
}