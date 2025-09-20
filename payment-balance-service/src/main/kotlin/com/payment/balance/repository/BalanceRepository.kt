package com.payment.balance.repository

import com.payment.balance.entity.BalanceEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface BalanceRepository : JpaRepository<BalanceEntity, UUID> {
    fun findByUsername(username: String): BalanceEntity?
    fun existsByUsername(username: String): Boolean
}