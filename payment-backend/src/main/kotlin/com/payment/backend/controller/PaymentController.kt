package com.payment.backend.controller

import com.payment.backend.dto.payment.PaymentRequest
import com.payment.backend.dto.payment.PaymentResponse
import com.payment.backend.repository.UserRepository
import com.payment.backend.service.PaymentService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/payments")
class PaymentController(
    private val paymentService: PaymentService,
    private val userRepository: UserRepository
) {

    private val logger = LoggerFactory.getLogger(PaymentController::class.java)

    @PostMapping
    fun createPayment(
        @Valid @RequestBody request: PaymentRequest,
        authentication: Authentication
    ): ResponseEntity<*> {
        return try {
            logger.info("Creating payment for user: ${authentication.name}, amount: ${request.amount}")

            val username = authentication.name
            if (username.isNullOrBlank()) {
                logger.error("Authentication name is null or blank")
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(mapOf("error" to "Invalid authentication"))
            }

            val user = userRepository.findByUsername(username)
            if (user == null) {
                logger.error("User not found: $username")
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(mapOf("error" to "User not found"))
            }

            logger.info("User found: ${user.id}, ${user.username}")
            val response = paymentService.createPayment(request, user)
            logger.info("Payment created successfully: ${response.id}")
            ResponseEntity.status(HttpStatus.CREATED).body(response)
        } catch (ex: Exception) {
            logger.error("Payment creation failed for user: ${authentication.name}", ex)
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(mapOf("error" to (ex.message ?: "Unknown error occurred")))
        }
    }

    @GetMapping("/{id}")
    fun getPayment(@PathVariable id: UUID): ResponseEntity<PaymentResponse> {
        val payment = paymentService.getPaymentById(id)
        return if (payment != null) {
            ResponseEntity.ok(payment)
        } else {
            ResponseEntity.notFound().build()
        }
    }
}