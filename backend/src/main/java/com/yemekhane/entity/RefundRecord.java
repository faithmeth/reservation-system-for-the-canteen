package com.yemekhane.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(
    name = "refund_records",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_refund_reservation_day_reason", columnNames = {"reservation_id", "refund_day", "reason"})
    },
    indexes = {
        @Index(name = "idx_refund_user_status", columnList = "user_id,status"),
        @Index(name = "idx_refund_reservation_status", columnList = "reservation_id,status")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private MonthlyReservation reservation;

    @Column(name = "refund_day")
    private LocalDate refundDay;

    @Column(name = "amount_kurus", nullable = false)
    private Long amountKurus = 0L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundReason reason = RefundReason.HOLIDAY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status = RefundStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    private LocalDate tatilTarihi;
    private String tatilAciklama;
    private Double iadeEdilen;
    private LocalDateTime islemTarihi;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private Boolean isRefunded = false;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Europe/Istanbul"));
        if (this.createdAt == null) this.createdAt = now;
        if (this.islemTarihi == null) this.islemTarihi = this.createdAt;
        syncLegacyFields();
    }

    @PreUpdate
    public void preUpdate() {
        syncLegacyFields();
    }

    public void syncLegacyFields() {
        if (this.refundDay == null && this.tatilTarihi != null) this.refundDay = this.tatilTarihi;
        if (this.tatilTarihi == null && this.refundDay != null) this.tatilTarihi = this.refundDay;
        if ((this.amountKurus == null || this.amountKurus == 0L) && this.iadeEdilen != null) {
            this.amountKurus = Math.round(this.iadeEdilen * 100.0);
        }
        if (this.amountKurus == null) this.amountKurus = 0L;
        this.iadeEdilen = this.amountKurus / 100.0;
        if (this.reason == null) {
            this.reason = "Kullanıcı rezervasyon iptali".equals(this.tatilAciklama) ? RefundReason.USER_CANCELLED : RefundReason.HOLIDAY;
        }
        if (this.tatilAciklama == null) {
            this.tatilAciklama = this.reason == RefundReason.USER_CANCELLED ? "Kullanıcı rezervasyon iptali" : "Tatil/resmi gün iadesi";
        }
        if (this.status == null) {
            this.status = Boolean.TRUE.equals(this.isRefunded) ? RefundStatus.PAID : RefundStatus.PENDING;
        }
        this.isRefunded = this.status == RefundStatus.PAID;
        if (this.status == RefundStatus.PAID && this.paidAt == null) {
            this.paidAt = LocalDateTime.now(ZoneId.of("Europe/Istanbul"));
        }
        if (this.islemTarihi == null) {
            this.islemTarihi = this.createdAt != null ? this.createdAt : LocalDateTime.now(ZoneId.of("Europe/Istanbul"));
        }
    }
}
