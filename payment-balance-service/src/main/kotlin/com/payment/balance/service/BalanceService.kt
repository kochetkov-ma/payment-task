package com.payment.balance.service

import com.payment.balance.entity.BalanceEntity
import com.payment.balance.repository.BalanceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@Service
class BalanceService(
    private val balanceRepository: BalanceRepository
) {

    companion object {
        private val COMMISSION_RATE = BigDecimal("0.01") // 1%
        private val ZERO = BigDecimal.ZERO
    }

    @Transactional(readOnly = true)
    fun findByUsername(username: String): BalanceEntity? {
        return balanceRepository.findByUsername(username)
    }

    @Transactional
    fun createBalance(username: String, initialBalance: BigDecimal = ZERO): BalanceEntity {
        val now = LocalDateTime.now()
        val balance = BalanceEntity(
            username = username,
            balance = initialBalance,
            createdAt = now,
            updatedAt = now
        )
        return balanceRepository.save(balance)
    }

    @Transactional
    fun getOrCreateBalance(username: String): BalanceEntity {
        return findByUsername(username) ?: createBalance(username)
    }

    fun calculateCommission(amount: BigDecimal): BigDecimal {
        return amount.multiply(COMMISSION_RATE)
    }

    @Transactional
    fun holdAmount(username: String, amount: BigDecimal, paymentId: UUID): HoldResult {
        val balance = getOrCreateBalance(username)
        val commission = calculateCommission(amount)
        val totalRequired = amount.add(commission)

        // Check if balance is sufficient
        if (balance.balance < totalRequired) {
            return HoldResult(
                success = false,
                message = "Insufficient balance. Required: $totalRequired, Available: ${balance.balance}",
                commission = commission
            )
        }

        // Hold the amount (subtract from balance, add to reserved)
        val updatedBalance = balance.copy(
            balance = balance.balance.subtract(totalRequired),
            reservedAmount = balance.reservedAmount.add(totalRequired),
            updatedAt = LocalDateTime.now()
        )

        balanceRepository.save(updatedBalance)

        return HoldResult(
            success = true,
            message = "Amount held successfully",
            commission = commission,
            balance = updatedBalance
        )
    }

    @Transactional
    fun releaseAmount(username: String, amount: BigDecimal): Boolean {
        val balance = findByUsername(username) ?: return false

        if (balance.reservedAmount < amount) {
            return false
        }

        val updatedBalance = balance.copy(
            balance = balance.balance.add(amount),
            reservedAmount = balance.reservedAmount.subtract(amount),
            updatedAt = LocalDateTime.now()
        )

        balanceRepository.save(updatedBalance)
        return true
    }

    data class HoldResult(
        val success: Boolean,
        val message: String,
        val commission: BigDecimal,
        val balance: BalanceEntity? = null
    )
}