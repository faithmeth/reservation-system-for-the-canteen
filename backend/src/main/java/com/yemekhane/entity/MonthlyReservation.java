package com.yemekhane.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(
    name = "monthly_reservations",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_monthly_reservation_user_year_month", columnNames = {"user_id", "yil", "ay"})
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyReservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private Integer yil;
    private Integer ay;

    private Integer secilenGunSayisi;
    private Double toplamTutar;

    @Column(name = "total_amount_kurus", nullable = false)
    private Long totalAmountKurus = 0L;

    @Column(name = "paid_amount_kurus", nullable = false)
    private Long paidAmountKurus = 0L;

    @Enumerated(EnumType.STRING)
    private PaymentStatus odemeDurumu = PaymentStatus.PAID;

    @OneToMany(mappedBy = "monthlyReservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<ReservationDay> reservationDays;

    @OneToMany(mappedBy = "reservation")
    private java.util.List<RefundRecord> refundRecords;

    private LocalDateTime islemTarihi = LocalDateTime.now(ZoneId.of("Europe/Istanbul"));

    @PrePersist
    @PreUpdate
    public void syncMoneyFields() {
        if (this.totalAmountKurus == null || this.totalAmountKurus == 0L) {
            this.totalAmountKurus = Math.round((this.toplamTutar == null ? 0.0 : this.toplamTutar) * 100.0);
        }
        if (this.paidAmountKurus == null || this.paidAmountKurus == 0L) {
            this.paidAmountKurus = this.totalAmountKurus;
        }
        this.toplamTutar = this.totalAmountKurus / 100.0;
    }
}
