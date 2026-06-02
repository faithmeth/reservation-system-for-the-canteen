package com.yemekhane.service;

import com.yemekhane.dto.HolidayDto;
import com.yemekhane.dto.RefundRecordDto;
import com.yemekhane.entity.Holiday;
import com.yemekhane.entity.RefundReason;
import com.yemekhane.entity.RefundStatus;
import com.yemekhane.entity.ReservationDay;
import com.yemekhane.exception.BusinessException;
import com.yemekhane.repository.HolidayRepository;
import com.yemekhane.repository.RefundRecordRepository;
import com.yemekhane.repository.ReservationDayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HolidayService {

    @Value("${app.constants.daily-price:100.0}")
    private double dailyPrice;

    private final HolidayRepository holidayRepository;
    private final ReservationDayRepository reservationDayRepository;
    private final RefundRecordRepository refundRecordRepository;
    private final RefundLedgerService refundLedgerService;

    public List<HolidayDto> getAllHolidays() {
        return holidayRepository.findAll().stream().map(HolidayDto::fromEntity).collect(Collectors.toList());
    }

    @Transactional
    public HolidayDto createHoliday(HolidayDto request) {
        if (request.getTarih().getYear() != 2026) throw new BusinessException("Sistem yalnizca 2026 yili icin calismaktadir.");
        if (holidayRepository.findByTarih(request.getTarih()).isPresent()) throw new BusinessException("Bu tarihte zaten bir tatil tanimli.");
        Holiday holiday = new Holiday();
        holiday.setTarih(request.getTarih());
        holiday.setAciklama(request.getAciklama());
        Holiday saved = holidayRepository.save(holiday);
        processRefundsForDate(request.getTarih(), request.getAciklama());
        return HolidayDto.fromEntity(saved);
    }

    private void processRefundsForDate(LocalDate tarih, String aciklama) {
        List<ReservationDay> affectedDays = reservationDayRepository.findByTarih(tarih);
        long amountKurus = Math.round(dailyPrice * 100.0);
        for (ReservationDay day : affectedDays) {
            refundLedgerService.createRefundIfAllowed(day.getMonthlyReservation(), tarih, amountKurus, RefundReason.HOLIDAY, aciklama);
        }
    }

    @Transactional
    public void markRefunded(Long id) {
        refundLedgerService.markPaid(id);
    }

    @Transactional
    public int markAllPendingRefunded() {
        return refundLedgerService.markAllPendingPaid();
    }

    @Transactional
    public int markRefundsPaid(List<Long> ids) {
        return refundLedgerService.markPaid(ids);
    }

    @Transactional
    public void deleteHoliday(Long id) {
        if (!holidayRepository.existsById(id)) throw new BusinessException("Tatil gunu bulunamadi.");
        holidayRepository.deleteById(id);
    }

    public List<RefundRecordDto> getAllRefunds() {
        return refundRecordRepository.findAllByOrderByIslemTarihiDesc().stream().map(RefundRecordDto::fromEntity).collect(Collectors.toList());
    }

    public List<RefundRecordDto> getUserRefunds(Long userId) {
        return refundRecordRepository.findByUserIdOrderByIslemTarihiDesc(userId).stream().map(RefundRecordDto::fromEntity).collect(Collectors.toList());
    }

    public long getPendingRefundCount() {
        return refundRecordRepository.findByStatus(RefundStatus.PENDING).size();
    }
}
