package com.payment.balance.entity

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "balances")
@EntityListeners(AuditingEntityListener::class)
data class BalanceEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(unique = true, nullable = false)
    val username: String,

    @Column(nullable = false, precision = 19, scale = 2)
    val balance: BigDecimal,

    @Column(name = "reserved_amount", nullable = false, precision = 19, scale = 2)
    val reservedAmount: BigDecimal = BigDecimal.ZERO,

    @OneToMany(mappedBy = "balance", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val transactions: List<TransactionEntity> = emptyList(),

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null,

    @LastModifiedDate
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null,

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    val createdBy: String? = null,

    @LastModifiedBy
    @Column(name = "updated_by")
    val updatedBy: String? = null
) {
    @PrePersist
    fun prePersist() {
        val now = LocalDateTime.now()
        if (createdAt == null) {
            createdAt = now
        }
        updatedAt = now
    }

    @PreUpdate
    fun preUpdate() {
        updatedAt = LocalDateTime.now()
    }
}