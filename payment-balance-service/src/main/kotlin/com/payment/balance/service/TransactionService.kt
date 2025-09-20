package com.payment.balance.service

import com.payment.balance.entity.BalanceEntity
import com.payment.balance.entity.TransactionEntity
import com.payment.balance.entity.TransactionStatus
import com.payment.balance.entity.TransactionType
import com.payment.balance.repository.TransactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.*

@Service
class TransactionService(
    private val transactionRepository: TransactionRepository
) {

    @Transactional
    fun createHoldTransaction(
        balance: BalanceEntity,
        amount: BigDecimal,
        paymentId: UUID,
        status: TransactionStatus = TransactionStatus.COMPLETED
    ): TransactionEntity {
        val transaction = TransactionEntity(
            balance = balance,
            amount = amount,
            type = TransactionType.HOLD,
            paymentId = paymentId,
            status = status
        )
        return transactionRepository.save(transaction)
    }

    @Transactional
    fun createReleaseTransaction(
        balance: BalanceEntity,
        amount: BigDecimal,
        paymentId: UUID?,
        status: TransactionStatus = TransactionStatus.COMPLETED
    ): TransactionEntity {
        val transaction = TransactionEntity(
            balance = balance,
            amount = amount,
            type = TransactionType.RELEASE,
            paymentId = paymentId,
            status = status
        )
        return transactionRepository.save(transaction)
    }

    @Transactional
    fun createDepositTransaction(
        balance: BalanceEntity,
        amount: BigDecimal,
        status: TransactionStatus = TransactionStatus.COMPLETED
    ): TransactionEntity {
        val transaction = TransactionEntity(
            balance = balance,
            amount = amount,
            type = TransactionType.DEPOSIT,
            paymentId = null,
            status = status
        )
        return transactionRepository.save(transaction)
    }

    @Transactional
    fun createWithdrawalTransaction(
        balance: BalanceEntity,
        amount: BigDecimal,
        status: TransactionStatus = TransactionStatus.COMPLETED
    ): TransactionEntity {
        val transaction = TransactionEntity(
            balance = balance,
            amount = amount,
            type = TransactionType.WITHDRAWAL,
            paymentId = null,
            status = status
        )
        return transactionRepository.save(transaction)
    }

    @Transactional(readOnly = true)
    fun findByPaymentId(paymentId: UUID): List<TransactionEntity> {
        return transactionRepository.findByPaymentId(paymentId)
    }

    @Transactional(readOnly = true)
    fun findByBalanceUsername(username: String): List<TransactionEntity> {
        return transactionRepository.findByBalanceUsernameOrderByCreatedAtDesc(username)
    }
}