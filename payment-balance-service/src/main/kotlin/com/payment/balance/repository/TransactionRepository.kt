package com.payment.balance.repository

import com.payment.balance.entity.BalanceEntity
import com.payment.balance.entity.TransactionEntity
import com.payment.balance.entity.TransactionStatus
import com.payment.balance.entity.TransactionType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface TransactionRepository : JpaRepository<TransactionEntity, UUID> {
    fun findByBalance(balance: BalanceEntity): List<TransactionEntity>
    fun findByPaymentId(paymentId: UUID): List<TransactionEntity>
    fun findByStatus(status: TransactionStatus): List<TransactionEntity>
    fun findByBalanceAndStatus(balance: BalanceEntity, status: TransactionStatus): List<TransactionEntity>
    fun findByBalanceAndType(balance: BalanceEntity, type: TransactionType): List<TransactionEntity>
    fun findByBalanceUsernameOrderByCreatedAtDesc(username: String): List<TransactionEntity>
}