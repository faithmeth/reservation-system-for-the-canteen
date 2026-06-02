package com.yemekhane.repository;

import com.yemekhane.entity.RefundReason;
import com.yemekhane.entity.RefundRecord;
import com.yemekhane.entity.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RefundRecordRepository extends JpaRepository<RefundRecord, Long> {
    List<RefundRecord> findByUserIdOrderByIslemTarihiDesc(Long userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM RefundRecord e WHERE e.user.id = :userId")
    void deleteByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);

    List<RefundRecord> findAllByOrderByIslemTarihiDesc();
    boolean existsByUserIdAndTatilTarihi(Long userId, LocalDate tatilTarihi);
    List<RefundRecord> findByStatus(RefundStatus status);
    Optional<RefundRecord> findByReservationIdAndRefundDayAndReason(Long reservationId, LocalDate refundDay, RefundReason reason);
    List<RefundRecord> findByReservationIdAndStatusIn(Long reservationId, List<RefundStatus> statuses);

    @Query("SELECT COALESCE(SUM(r.amountKurus), 0) FROM RefundRecord r WHERE r.reservation.id = :reservationId AND r.status IN (com.yemekhane.entity.RefundStatus.PENDING, com.yemekhane.entity.RefundStatus.PAID)")
    long sumActiveAmountKurusByReservationId(@Param("reservationId") Long reservationId);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM RefundRecord r WHERE r.reservation.id = :reservationId AND r.refundDay = :refundDay AND r.status IN (com.yemekhane.entity.RefundStatus.PENDING, com.yemekhane.entity.RefundStatus.PAID)")
    boolean existsActiveByReservationAndRefundDay(@Param("reservationId") Long reservationId, @Param("refundDay") LocalDate refundDay);

    @Query("SELECT COALESCE(SUM(r.amountKurus), 0) FROM RefundRecord r WHERE r.status IN (com.yemekhane.entity.RefundStatus.PENDING, com.yemekhane.entity.RefundStatus.PAID)")
    long sumTotalRefundLiabilityKurus();

    @Query("SELECT COALESCE(SUM(r.amountKurus), 0) FROM RefundRecord r WHERE r.status = :status")
    long sumAmountKurusByStatus(@Param("status") RefundStatus status);

    @Query("SELECT COALESCE(SUM(r.amountKurus), 0) FROM RefundRecord r WHERE r.user.id = :userId AND r.status = :status")
    long sumUserAmountKurusByStatus(@Param("userId") Long userId, @Param("status") RefundStatus status);

    @Query("SELECT COALESCE(SUM(r.amountKurus), 0) FROM RefundRecord r WHERE r.user.id = :userId AND r.status IN (com.yemekhane.entity.RefundStatus.PENDING, com.yemekhane.entity.RefundStatus.PAID)")
    long sumUserActiveAmountKurus(@Param("userId") Long userId);
}
