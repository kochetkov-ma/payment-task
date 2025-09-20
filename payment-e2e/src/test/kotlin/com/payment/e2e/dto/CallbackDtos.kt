package com.payment.e2e.dto

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

// DTOs for callback
data class PaymentCallbackRequest(
    val paymentId: UUID,
    val status: String,
    val amount: BigDecimal,
    val timestamp: LocalDateTime,
    val message: String? = null
)