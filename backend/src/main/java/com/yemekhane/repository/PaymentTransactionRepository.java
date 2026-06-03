package com.yemekhane.repository;

import com.yemekhane.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    @Query("SELECT DISTINCT pt FROM PaymentTransaction pt LEFT JOIN FETCH pt.user WHERE pt.user.id = :userId ORDER BY pt.islemTarihi DESC")
    List<PaymentTransaction> findByUserIdOrderByIslemTarihiDesc(@Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM PaymentTransaction e WHERE e.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
