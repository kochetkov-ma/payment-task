package com.payment.balance.dto.kafka

import java.math.BigDecimal
import java.util.*

data class HoldRequest(
    val paymentId: UUID,
    val userId: String, // Changed to String to accommodate username
    val amount: BigDecimal
)