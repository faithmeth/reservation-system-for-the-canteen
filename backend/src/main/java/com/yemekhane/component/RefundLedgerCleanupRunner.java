package com.yemekhane.component;

import com.yemekhane.entity.MonthlyReservation;
import com.yemekhane.entity.RefundReason;
import com.yemekhane.entity.RefundRecord;
import com.yemekhane.entity.RefundStatus;
import com.yemekhane.repository.MonthlyReservationRepository;
import com.yemekhane.repository.RefundRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RefundLedgerCleanupRunner implements CommandLineRunner {

    @Value("${app.migrations.refund-ledger-cleanup:false}")
    private boolean applyCleanup;

    private final RefundRecordRepository refundRecordRepository;
    private final MonthlyReservationRepository reservationRepository;

    @Override
    @Transactional
    public void run(String... args) {
        List<RefundRecord> records = refundRecordRepository.findAll();
        int linked = 0;
        int normalized = 0;
        int cancelledDuplicates = 0;
        int capped = 0;

        Map<String, RefundRecord> activeByKey = new HashMap<>();
        Map<Long, Long> activeAmountByReservation = new HashMap<>();

        for (RefundRecord record : records) {
            record.syncLegacyFields();
            if (record.getReservation() == null && record.getRefundDay() != null) {
                LocalDate day = record.getRefundDay();
                MonthlyReservation reservation = reservationRepository
                        .findByUserIdAndYilAndAy(record.getUser().getId(), day.getYear(), day.getMonthValue())
                        .orElse(null);
                if (reservation != null) {
                    linked++;
                    if (applyCleanup) record.setReservation(reservation);
                }
            }
            if (record.getReason() == null) {
                normalized++;
                if (applyCleanup) record.setReason("Kullanıcı rezervasyon iptali".equals(record.getTatilAciklama()) ? RefundReason.USER_CANCELLED : RefundReason.HOLIDAY);
            }
            if (record.getStatus() == null) {
                normalized++;
                if (applyCleanup) record.setStatus(Boolean.TRUE.equals(record.getIsRefunded()) ? RefundStatus.PAID : RefundStatus.PENDING);
            }
            if (applyCleanup) record.syncLegacyFields();
        }

        for (RefundRecord record : records) {
            if (record.getReservation() == null || record.getRefundDay() == null || record.getStatus() == RefundStatus.CANCELLED) continue;
            String key = record.getReservation().getId() + "|" + record.getRefundDay();
            if (activeByKey.containsKey(key)) {
                cancelledDuplicates++;
                if (applyCleanup) record.setStatus(RefundStatus.CANCELLED);
                continue;
            }
            activeByKey.put(key, record);
        }

        for (RefundRecord record : records) {
            if (record.getReservation() == null || record.getStatus() == RefundStatus.CANCELLED) continue;
            Long reservationId = record.getReservation().getId();
            long paidCap = record.getReservation().getPaidAmountKurus() != null ? record.getReservation().getPaidAmountKurus() : Math.round((record.getReservation().getToplamTutar() == null ? 0.0 : record.getReservation().getToplamTutar()) * 100.0);
            long used = activeAmountByReservation.getOrDefault(reservationId, 0L);
            long amount = record.getAmountKurus() == null ? 0L : record.getAmountKurus();
            if (used + amount > paidCap) {
                capped++;
                long allowed = Math.max(0L, paidCap - used);
                if (applyCleanup) {
                    if (allowed == 0L) record.setStatus(RefundStatus.CANCELLED);
                    else record.setAmountKurus(allowed);
                    record.syncLegacyFields();
                }
                used += allowed;
            } else {
                used += amount;
            }
            activeAmountByReservation.put(reservationId, used);
        }

        if (applyCleanup) {
            refundRecordRepository.saveAll(records);
        }
        System.out.println("Refund ledger cleanup " + (applyCleanup ? "APPLIED" : "DRY-RUN")
                + ": linked=" + linked
                + ", normalized=" + normalized
                + ", duplicate_active_cancelled=" + cancelledDuplicates
                + ", capped=" + capped);
    }
}
