package com.payment.backend.dto.payment

import com.payment.backend.entity.PaymentStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class PaymentResponse(
    val id: UUID,
    val amount: BigDecimal,
    val status: PaymentStatus,
    val callbackUrl: String,
    val userId: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime?
)