package com.yemekhane.repository;

import com.yemekhane.entity.MonthlyReservation;
import com.yemekhane.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MonthlyReservationRepository extends JpaRepository<MonthlyReservation, Long> {

    List<MonthlyReservation> findByUserIdOrderByIslemTarihiDesc(Long userId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM MonthlyReservation e WHERE e.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    Optional<MonthlyReservation> findByUserIdAndYilAndAy(Long userId, Integer yil, Integer ay);

    long countByOdemeDurumu(PaymentStatus status);

    @Query("SELECT COALESCE(SUM(mr.toplamTutar), 0) FROM MonthlyReservation mr WHERE mr.odemeDurumu = :status")
    double sumToplamTutarByOdemeDurumu(@Param("status") PaymentStatus status);

    @Query("SELECT COALESCE(SUM(mr.toplamTutar), 0) FROM MonthlyReservation mr")
    double sumAllToplamTutar();

    @Query("SELECT COALESCE(SUM(mr.paidAmountKurus), 0) FROM MonthlyReservation mr")
    long sumPaidAmountKurus();

    @Query("SELECT COALESCE(SUM(mr.paidAmountKurus), 0) FROM MonthlyReservation mr WHERE mr.user.id = :userId")
    long sumPaidAmountKurusByUserId(@Param("userId") Long userId);

    @Query("SELECT mr.yil, mr.ay, COALESCE(SUM(mr.secilenGunSayisi), 0), COALESCE(SUM(mr.toplamTutar), 0) FROM MonthlyReservation mr GROUP BY mr.yil, mr.ay ORDER BY mr.yil, mr.ay")
    List<Object[]> findMonthlyStats();

    @Query("SELECT COUNT(mr) FROM MonthlyReservation mr WHERE mr.yil = :year AND mr.ay = :month")
    long countByYilAndAy(@Param("year") int year, @Param("month") int month);

    @Query("SELECT COUNT(DISTINCT mr.user.id) FROM MonthlyReservation mr")
    long countDistinctUsers();
}
