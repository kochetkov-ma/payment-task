package com.payment.backend.dto.auth

import java.util.*

data class AuthResponse(
    val token: String,
    val type: String = "Bearer",
    val userId: UUID,
    val username: String
)