package com.yemekhane.service;

import com.yemekhane.dto.*;
import com.yemekhane.entity.MonthlyMenu;
import com.yemekhane.entity.PaymentStatus;
import com.yemekhane.entity.RefundStatus;
import com.yemekhane.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
public class AdminStatisticsService {

    @Value("${app.constants.daily-price:100.0}")
    private double dailyPrice;

    @Value("${app.constants.timezone:Europe/Istanbul}")
    private String timezone;

    private final MonthlyReservationRepository reservationRepository;
    private final ReservationDayRepository reservationDayRepository;
    private final RefundRecordRepository refundRecordRepository;
    private final HolidayRepository holidayRepository;
    private final MonthlyMenuRepository menuRepository;

    public AdminStatisticsOverviewDto getOverview() {
        LocalDate today = LocalDate.now(ZoneId.of(timezone));
        int currentYear = today.getYear();
        int currentMonth = today.getMonthValue();
        double grossRevenue = reservationRepository.sumPaidAmountKurus() / 100.0;
        double totalRefundLiability = refundRecordRepository.sumTotalRefundLiabilityKurus() / 100.0;
        double paidRefundTotal = refundRecordRepository.sumAmountKurusByStatus(RefundStatus.PAID) / 100.0;
        double pendingRefundTotal = refundRecordRepository.sumAmountKurusByStatus(RefundStatus.PENDING) / 100.0;
        return AdminStatisticsOverviewDto.builder()
                .totalReservations(reservationRepository.count())
                .thisMonthReservations(reservationRepository.countByYilAndAy(currentYear, currentMonth))
                .todayReservations(reservationDayRepository.countTodayReservations(today))
                .totalRevenue(grossRevenue)
                .grossRevenue(grossRevenue)
                .totalRefundAmount(totalRefundLiability)
                .totalRefundLiability(totalRefundLiability)
                .paidRefundTotal(paidRefundTotal)
                .pendingRefundTotal(pendingRefundTotal)
                .netRevenue(grossRevenue - totalRefundLiability)
                .activeUserCount(reservationRepository.countDistinctUsers())
                .holidayCount(holidayRepository.count())
                .build();
    }

    public List<MostReservedDayDto> getMostReservedDays(int limit) {
        return reservationDayRepository.findMostReservedDays().stream().limit(limit).map(row -> {
            LocalDate date = (LocalDate) row[0];
            long count = ((Number) row[1]).longValue();
            return MostReservedDayDto.builder().reservationDate(date).dayOfWeek(date.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("tr"))).reservationCount(count).estimatedRevenue(count * dailyPrice).build();
        }).collect(Collectors.toList());
    }

    public List<FavoriteMenuDto> getFavoriteMenus(int limit) {
        List<Object[]> mostReserved = reservationDayRepository.findMostReservedDays();
        long grandTotal = reservationDayRepository.count();
        return mostReserved.stream().limit(limit).map(row -> {
            LocalDate date = (LocalDate) row[0];
            long count = ((Number) row[1]).longValue();
            String menuName = menuRepository.findByTarih(date).map(MonthlyMenu::getYemekListesi).orElse("Menu bulunamadi");
            double percentage = grandTotal > 0 ? Math.round((count * 100.0 / grandTotal) * 10.0) / 10.0 : 0.0;
            return FavoriteMenuDto.builder().menuName(menuName).serviceDate(date).reservationCount(count).totalRevenue(count * dailyPrice).percentageShare(percentage).build();
        }).collect(Collectors.toList());
    }

    public List<MonthlyReservationStatsDto> getMonthlyStats() {
        Map<String, Double> refundByMonth = new HashMap<>();
        refundRecordRepository.findAll().stream()
                .filter(r -> r.getStatus() == RefundStatus.PENDING || r.getStatus() == RefundStatus.PAID)
                .forEach(r -> {
                    LocalDate day = r.getRefundDay() != null ? r.getRefundDay() : r.getTatilTarihi();
                    if (day != null) refundByMonth.merge(day.getYear() + "-" + day.getMonthValue(), (r.getAmountKurus() == null ? 0L : r.getAmountKurus()) / 100.0, Double::sum);
                });
        return reservationRepository.findMonthlyStats().stream().map(row -> {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            long count = ((Number) row[2]).longValue();
            double rev = ((Number) row[3]).doubleValue();
            double ref = refundByMonth.getOrDefault(year + "-" + month, 0.0);
            return MonthlyReservationStatsDto.builder().year(year).month(month).monthName(Month.of(month).getDisplayName(TextStyle.FULL, new Locale("tr"))).reservationCount(count).revenue(rev).refundAmount(ref).netRevenue(rev - ref).build();
        }).collect(Collectors.toList());
    }

    public PaymentSummaryDto getPaymentSummary() {
        double grossRevenue = reservationRepository.sumPaidAmountKurus() / 100.0;
        double totalRefundLiability = refundRecordRepository.sumTotalRefundLiabilityKurus() / 100.0;
        double paidRefundTotal = refundRecordRepository.sumAmountKurusByStatus(RefundStatus.PAID) / 100.0;
        double pendingRefundTotal = refundRecordRepository.sumAmountKurusByStatus(RefundStatus.PENDING) / 100.0;
        return PaymentSummaryDto.builder()
                .paidReservationCount(reservationRepository.countByOdemeDurumu(PaymentStatus.PAID))
                .pendingReservationCount(reservationRepository.countByOdemeDurumu(PaymentStatus.PENDING_PAYMENT))
                .totalRevenue(grossRevenue)
                .grossRevenue(grossRevenue)
                .totalRefundAmount(totalRefundLiability)
                .totalRefundLiability(totalRefundLiability)
                .paidRefundTotal(paidRefundTotal)
                .pendingRefundTotal(pendingRefundTotal)
                .netRevenue(grossRevenue - totalRefundLiability)
                .build();
    }

    public RefundSummaryDto getRefundSummary() {
        var activeRefunds = refundRecordRepository.findAll().stream().filter(r -> r.getStatus() == RefundStatus.PENDING || r.getStatus() == RefundStatus.PAID).toList();
        long holiday = activeRefunds.stream().filter(r -> r.getReason() == com.yemekhane.entity.RefundReason.HOLIDAY).count();
        double amount = activeRefunds.stream().mapToLong(r -> r.getAmountKurus() == null ? 0L : r.getAmountKurus()).sum() / 100.0;
        Map<LocalDate, Long> refundsByDay = activeRefunds.stream().filter(r -> r.getRefundDay() != null).collect(Collectors.groupingBy(com.yemekhane.entity.RefundRecord::getRefundDay, Collectors.counting()));
        List<MostReservedDayDto> topRefundDays = refundsByDay.entrySet().stream().sorted(Map.Entry.<LocalDate, Long>comparingByValue().reversed()).limit(5).map(e -> MostReservedDayDto.builder().reservationDate(e.getKey()).dayOfWeek(e.getKey().getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("tr"))).reservationCount(e.getValue()).estimatedRevenue(e.getValue() * dailyPrice).build()).collect(Collectors.toList());
        return RefundSummaryDto.builder().totalRefundRecords(activeRefunds.size()).holidayRefundCount(holiday).totalRefundAmount(amount).mostRefundedDays(topRefundDays).build();
    }

    public List<MostReservedDayDto> getMostCancelledDays(int limit) {
        Map<LocalDate, Long> cancelsByDay = refundRecordRepository.findAll().stream()
                .filter(r -> r.getReason() == com.yemekhane.entity.RefundReason.USER_CANCELLED && r.getStatus() != RefundStatus.CANCELLED)
                .filter(r -> r.getRefundDay() != null)
                .collect(Collectors.groupingBy(com.yemekhane.entity.RefundRecord::getRefundDay, Collectors.counting()));
        return cancelsByDay.entrySet().stream().sorted(Map.Entry.<LocalDate, Long>comparingByValue().reversed()).limit(limit).map(e -> MostReservedDayDto.builder().reservationDate(e.getKey()).dayOfWeek(e.getKey().getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("tr"))).reservationCount(e.getValue()).estimatedRevenue(e.getValue() * dailyPrice).build()).collect(Collectors.toList());
    }
}
