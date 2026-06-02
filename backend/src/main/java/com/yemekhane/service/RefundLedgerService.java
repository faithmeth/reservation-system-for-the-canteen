package com.yemekhane.service;

import com.yemekhane.entity.MonthlyReservation;
import com.yemekhane.entity.RefundReason;
import com.yemekhane.entity.RefundRecord;
import com.yemekhane.entity.RefundStatus;
import com.yemekhane.exception.BusinessException;
import com.yemekhane.repository.RefundRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RefundLedgerService {

    private final RefundRecordRepository refundRecordRepository;

    @Transactional
    public Optional<RefundRecord> createRefundIfAllowed(MonthlyReservation reservation, LocalDate refundDay, long amountKurus, RefundReason reason, String description) {
        if (reservation == null || reservation.getId() == null) {
            throw new BusinessException("İade için geçerli rezervasyon bulunamadı.");
        }
        if (refundDay == null) {
            throw new BusinessException("İade günü boş olamaz.");
        }
        if (amountKurus <= 0) {
            return Optional.empty();
        }

        Optional<RefundRecord> sameReason = refundRecordRepository.findByReservationIdAndRefundDayAndReason(reservation.getId(), refundDay, reason);
        if (sameReason.isPresent() && sameReason.get().getStatus() != RefundStatus.CANCELLED) {
            return sameReason;
        }

        boolean sameDayAlreadyActive = refundRecordRepository.existsActiveByReservationAndRefundDay(reservation.getId(), refundDay);
        if (sameDayAlreadyActive) {
            return Optional.empty();
        }

        long activeTotal = refundRecordRepository.sumActiveAmountKurusByReservationId(reservation.getId());
        long paidCap = normalizePaidAmountKurus(reservation);
        long remaining = Math.max(0L, paidCap - activeTotal);
        long cappedAmount = Math.min(amountKurus, remaining);
        if (cappedAmount <= 0) {
            return Optional.empty();
        }

        RefundRecord refund = new RefundRecord();
        refund.setUser(reservation.getUser());
        refund.setReservation(reservation);
        refund.setRefundDay(refundDay);
        refund.setAmountKurus(cappedAmount);
        refund.setReason(reason);
        refund.setStatus(RefundStatus.PENDING);
        refund.setTatilAciklama(description);
        refund.syncLegacyFields();

        try {
            return Optional.of(refundRecordRepository.save(refund));
        } catch (DataIntegrityViolationException ex) {
            return refundRecordRepository.findByReservationIdAndRefundDayAndReason(reservation.getId(), refundDay, reason)
                    .filter(r -> r.getStatus() != RefundStatus.CANCELLED);
        }
    }

    @Transactional
    public void markPaid(Long refundId) {
        RefundRecord record = refundRecordRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException("İade kaydı bulunamadı."));
        markPaid(record);
    }

    @Transactional
    public int markPaid(Collection<Long> refundIds) {
        if (refundIds == null || refundIds.isEmpty()) {
            return 0;
        }
        List<RefundRecord> pending = refundRecordRepository.findAllById(refundIds).stream()
                .filter(r -> r.getStatus() == RefundStatus.PENDING)
                .toList();
        pending.forEach(this::markPaid);
        refundRecordRepository.saveAll(pending);
        return pending.size();
    }

    @Transactional
    public int markAllPendingPaid() {
        List<RefundRecord> pending = refundRecordRepository.findByStatus(RefundStatus.PENDING);
        pending.forEach(this::markPaid);
        refundRecordRepository.saveAll(pending);
        return pending.size();
    }

    private void markPaid(RefundRecord record) {
        if (record.getStatus() != RefundStatus.PENDING) {
            return;
        }
        record.setStatus(RefundStatus.PAID);
        record.setPaidAt(LocalDateTime.now(ZoneId.of("Europe/Istanbul")));
        record.syncLegacyFields();
    }

    private long normalizePaidAmountKurus(MonthlyReservation reservation) {
        if (reservation.getPaidAmountKurus() != null && reservation.getPaidAmountKurus() > 0L) {
            return reservation.getPaidAmountKurus();
        }
        if (reservation.getTotalAmountKurus() != null && reservation.getTotalAmountKurus() > 0L) {
            return reservation.getTotalAmountKurus();
        }
        return Math.round((reservation.getToplamTutar() == null ? 0.0 : reservation.getToplamTutar()) * 100.0);
    }
}
