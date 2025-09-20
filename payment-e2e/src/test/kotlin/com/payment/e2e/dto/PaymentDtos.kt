package com.payment.e2e.dto

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

// DTOs for payments
data class PaymentRequest(
    val amount: BigDecimal,
    val callbackUrl: String
)

data class PaymentResponse(
    val id: UUID,
    val amount: BigDecimal,
    val status: String,
    val callbackUrl: String,
    val userId: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime?
)

// Payment status enum
enum class PaymentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}