package com.payment.backend.repository

import com.payment.backend.entity.PaymentEntity
import com.payment.backend.entity.PaymentStatus
import com.payment.backend.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface PaymentRepository : JpaRepository<PaymentEntity, UUID> {
    fun findByUser(user: UserEntity): List<PaymentEntity>
    fun findByExternalId(externalId: String): PaymentEntity?
    fun findByStatus(status: PaymentStatus): List<PaymentEntity>
    fun findByUserAndStatus(user: UserEntity, status: PaymentStatus): List<PaymentEntity>
}