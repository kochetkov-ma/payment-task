package com.payment.backend.service

import com.payment.backend.dto.auth.AuthResponse
import com.payment.backend.dto.auth.LoginRequest
import com.payment.backend.dto.auth.RegisterRequest
import com.payment.backend.entity.UserEntity
import com.payment.backend.repository.UserRepository
import com.payment.backend.security.JwtTokenProvider
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authenticationManager: AuthenticationManager,
    private val jwtTokenProvider: JwtTokenProvider
) {

    fun register(request: RegisterRequest): AuthResponse {
        if (userRepository.existsByUsername(request.username)) {
            throw IllegalArgumentException("Username already exists")
        }

        if (userRepository.existsByEmail(request.email)) {
            throw IllegalArgumentException("Email already exists")
        }

        val user = UserEntity(
            username = request.username,
            password = passwordEncoder.encode(request.password),
            email = request.email,
            enabled = true
        )

        val savedUser = userRepository.save(user)
        val token = jwtTokenProvider.generateToken(savedUser.username)

        return AuthResponse(
            token = token,
            userId = savedUser.id!!,
            username = savedUser.username
        )
    }

    fun login(request: LoginRequest): AuthResponse {
        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.username, request.password)
        )

        val user = userRepository.findByUsername(request.username)
            ?: throw IllegalArgumentException("User not found")

        val token = jwtTokenProvider.generateToken(authentication)

        return AuthResponse(
            token = token,
            userId = user.id!!,
            username = user.username
        )
    }
}