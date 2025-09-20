package com.payment.e2e.dto

import java.util.*

// DTOs for authentication
data class RegisterRequest(
    val username: String,
    val password: String,
    val email: String
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class AuthResponse(
    val token: String,
    val type: String = "Bearer",
    val userId: UUID,
    val username: String
)