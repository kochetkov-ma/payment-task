package com.payment.backend.dto.kafka

import java.math.BigDecimal
import java.util.*

data class HoldResponse(
    val paymentId: UUID,
    val success: Boolean,
    val message: String?,
    val commission: BigDecimal? = null
)