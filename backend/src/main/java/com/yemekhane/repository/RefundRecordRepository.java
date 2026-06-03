package com.yemekhane.repository;

import com.yemekhane.entity.RefundRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRecordRepository extends JpaRepository<RefundRecord, Long> {

    @Query("SELECT COALESCE(SUM(r.iadeEdilen), 0.0) FROM RefundRecord r")
    Double sumAllRefundAmount();

    @Query("SELECT COALESCE(SUM(r.iadeEdilen), 0.0) FROM RefundRecord r WHERE r.isRefunded = true")
    Double sumCompletedRefundAmount();
    
    @Query("SELECT DISTINCT rr FROM RefundRecord rr LEFT JOIN FETCH rr.user WHERE rr.user.id = :userId ORDER BY rr.islemTarihi DESC")
    List<RefundRecord> findByUserIdOrderByIslemTarihiDesc(@Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM RefundRecord e WHERE e.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Query("SELECT DISTINCT rr FROM RefundRecord rr LEFT JOIN FETCH rr.user ORDER BY rr.islemTarihi DESC")
    List<RefundRecord> findAllByOrderByIslemTarihiDesc();
    
    boolean existsByUserIdAndTatilTarihi(Long userId, LocalDate tatilTarihi);
}
