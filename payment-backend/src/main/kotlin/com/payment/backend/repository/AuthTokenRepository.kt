package com.payment.backend.repository

import com.payment.backend.entity.AuthTokenEntity
import com.payment.backend.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

@Repository
interface AuthTokenRepository : JpaRepository<AuthTokenEntity, UUID> {
    fun findByToken(token: String): AuthTokenEntity?
    fun findByUser(user: UserEntity): List<AuthTokenEntity>

    @Modifying
    @Query("DELETE FROM AuthTokenEntity t WHERE t.expiresAt < :now")
    fun deleteExpiredTokens(now: LocalDateTime): Int

    @Modifying
    @Query("DELETE FROM AuthTokenEntity t WHERE t.user = :user")
    fun deleteByUser(user: UserEntity): Int
}