package com.yemekhane.service;

import com.yemekhane.dto.*;
import com.yemekhane.entity.MonthlyMenu;
import com.yemekhane.entity.PaymentStatus;
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

    // -------------------------------------------------------------------------
    // 1. Overview
    // -------------------------------------------------------------------------
    public AdminStatisticsOverviewDto getOverview() {
        LocalDate today = LocalDate.now(ZoneId.of(timezone));
        int currentYear  = today.getYear();
        int currentMonth = today.getMonthValue();

        long totalReservations     = reservationRepository.count();
        long thisMonthReservations = reservationRepository.countByYilAndAy(currentYear, currentMonth);
        long todayReservations     = reservationDayRepository.countTodayReservations(today);
        
        double currentReservationsTutar = reservationRepository.sumAllToplamTutar();
        
        // Brüt gelir (Gross Revenue): Mevcut rezervasyon tutarları + Toplam iade edilecek tutar (iptal + tatil)
        // Çünkü iptal edilen veya tatil olan günlerin parası currentReservationsTutar'dan düşüyor.
        double allRefundsAmount = refundRecordRepository.sumAllRefundAmount();
        double totalRevenue = currentReservationsTutar + allRefundsAmount; 

        // Sadece kullanıcının "İade Aldım" diyerek tahsil ettiği tamamlanmış iadeler
        double totalRefundAmount = refundRecordRepository.sumCompletedRefundAmount();

        // Net gelir: Brüt gelir - Tamamlanmış iadeler
        double netRevenue = totalRevenue - totalRefundAmount;
        
        long activeUserCount       = reservationRepository.countDistinctUsers();
        long holidayCount          = holidayRepository.count();

        return AdminStatisticsOverviewDto.builder()
                .totalReservations(totalReservations)
                .thisMonthReservations(thisMonthReservations)
                .todayReservations(todayReservations)
                .totalRevenue(totalRevenue)
                .totalRefundAmount(totalRefundAmount)
                .netRevenue(netRevenue)
                .activeUserCount(activeUserCount)
                .holidayCount(holidayCount)
                .build();
    }

    // -------------------------------------------------------------------------
    // 2. Most reserved days
    // -------------------------------------------------------------------------
    public List<MostReservedDayDto> getMostReservedDays(int limit) {
        List<Object[]> rows = reservationDayRepository.findMostReservedDays();
        return rows.stream()
                .limit(limit)
                .map(row -> {
                    LocalDate date = (LocalDate) row[0];
                    long count     = ((Number) row[1]).longValue();
                    return MostReservedDayDto.builder()
                            .reservationDate(date)
                            .dayOfWeek(date.getDayOfWeek()
                                    .getDisplayName(TextStyle.FULL, new Locale("tr")))
                            .reservationCount(count)
                            .estimatedRevenue(count * dailyPrice)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // 3. Favorite (most reserved) menus
    //    Logic: join MonthlyMenu.tarih == ReservationDay.tarih
    //    Metric name is "En Çok Rezerve Edilen Menüler" – not "liked menus"
    // -------------------------------------------------------------------------
    public List<FavoriteMenuDto> getFavoriteMenus(int limit) {
        List<Object[]> mostReserved = reservationDayRepository.findMostReservedDays();
        long grandTotal = reservationDayRepository.count();

        return mostReserved.stream()
                .limit(limit)
                .map(row -> {
                    LocalDate date = (LocalDate) row[0];
                    long count     = ((Number) row[1]).longValue();

                    String menuName = menuRepository.findByTarih(date)
                            .map(MonthlyMenu::getYemekListesi)
                            .orElse("Menü bulunamadı");

                    double percentage = grandTotal > 0
                            ? Math.round((count * 100.0 / grandTotal) * 10.0) / 10.0
                            : 0.0;

                    return FavoriteMenuDto.builder()
                            .menuName(menuName)
                            .serviceDate(date)
                            .reservationCount(count)
                            .totalRevenue(count * dailyPrice)
                            .percentageShare(percentage)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // 4. Monthly reservation trend
    // -------------------------------------------------------------------------
    public List<MonthlyReservationStatsDto> getMonthlyStats() {
        List<Object[]> rows = reservationRepository.findMonthlyStats();

        // Daha performanslı olması için tüm iptal/iade verisini DB'den gruplu alabiliriz veya tek seferde çekebiliriz.
        // Şimdilik sadece tek bir findAll ile hallediyoruz ki her ay için ayrı sorgu atmayalım.
        List<com.yemekhane.entity.RefundRecord> allRefunds = refundRecordRepository.findAll();

        Map<String, Double> allRefundsByMonth = new HashMap<>();
        Map<String, Double> completedRefundsByMonth = new HashMap<>();

        for (var r : allRefunds) {
            if (r.getTatilTarihi() != null) {
                String key = r.getTatilTarihi().getYear() + "-" + r.getTatilTarihi().getMonthValue();
                double amount = Optional.ofNullable(r.getIadeEdilen()).orElse(0.0);
                
                // Herhangi bir iade/iptal brüt gelire eklenmeli
                allRefundsByMonth.merge(key, amount, Double::sum);
                
                // Sadece iade edilenler net gelirden düşülmeli
                if (Boolean.TRUE.equals(r.getIsRefunded())) {
                    completedRefundsByMonth.merge(key, amount, Double::sum);
                }
            }
        }

        return rows.stream()
                .map(row -> {
                    int year   = ((Number) row[0]).intValue();
                    int month  = ((Number) row[1]).intValue();
                    long count = ((Number) row[2]).longValue();
                    double baseRev = ((Number) row[3]).doubleValue();
                    
                    String key = year + "-" + month;
                    double refundAmt = allRefundsByMonth.getOrDefault(key, 0.0);
                    double rev = baseRev + refundAmt; // Gross Revenue for this month
                    double ref = completedRefundsByMonth.getOrDefault(key, 0.0); // Completed Refunds for this month

                    String monthName = Month.of(month)
                            .getDisplayName(TextStyle.FULL, new Locale("tr"));

                    return MonthlyReservationStatsDto.builder()
                            .year(year)
                            .month(month)
                            .monthName(monthName)
                            .reservationCount(count)
                            .revenue(rev)
                            .refundAmount(ref)
                            .netRevenue(rev - ref)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // 5. Payment summary
    // -------------------------------------------------------------------------
    public PaymentSummaryDto getPaymentSummary() {
        long paid    = reservationRepository.countByOdemeDurumu(PaymentStatus.ODENDI);
        long pending = reservationRepository.countByOdemeDurumu(PaymentStatus.BEKLIYOR);
        
        double currentReservationsTutar = reservationRepository.sumAllToplamTutar();
        double allRefundsAmount = refundRecordRepository.sumAllRefundAmount();
        double totalRev = currentReservationsTutar + allRefundsAmount; // Gross Revenue

        double totalRef = refundRecordRepository.sumCompletedRefundAmount(); // Completed/Paid Refunds

        return PaymentSummaryDto.builder()
                .paidReservationCount(paid)
                .pendingReservationCount(pending)
                .totalRevenue(totalRev)
                .totalRefundAmount(totalRef)
                .netRevenue(totalRev - totalRef)
                .build();
    }

    // -------------------------------------------------------------------------
    // 6. Refund summary
    // -------------------------------------------------------------------------
    public RefundSummaryDto getRefundSummary() {
        var allRefunds = refundRecordRepository.findAll();
        long total     = allRefunds.size();
        long holiday   = allRefunds.stream()
                .filter(r -> r.getTatilTarihi() != null && !"Kullanıcı rezervasyon iptali".equals(r.getTatilAciklama()))
                .count();
        double amount  = allRefunds.stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsRefunded()))
                .mapToDouble(r -> Optional.ofNullable(r.getIadeEdilen()).orElse(0.0))
                .sum();

        // Days with most refunds (Holidays)
        Map<LocalDate, Long> refundsByDay = allRefunds.stream()
                .filter(r -> r.getTatilTarihi() != null && !"Kullanıcı rezervasyon iptali".equals(r.getTatilAciklama()))
                .collect(Collectors.groupingBy(
                        com.yemekhane.entity.RefundRecord::getTatilTarihi,
                        Collectors.counting()
                ));

        List<MostReservedDayDto> topRefundDays = refundsByDay.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> MostReservedDayDto.builder()
                        .reservationDate(e.getKey())
                        .dayOfWeek(e.getKey().getDayOfWeek()
                                .getDisplayName(TextStyle.FULL, new Locale("tr")))
                        .reservationCount(e.getValue())
                        .estimatedRevenue(e.getValue() * dailyPrice)
                        .build())
                .collect(Collectors.toList());

        return RefundSummaryDto.builder()
                .totalRefundRecords(total)
                .holidayRefundCount(holiday)
                .totalRefundAmount(amount)
                .mostRefundedDays(topRefundDays)
                .build();
    }

    // -------------------------------------------------------------------------
    // 7. Most cancelled days (excluding holidays)
    // -------------------------------------------------------------------------
    public List<MostReservedDayDto> getMostCancelledDays(int limit) {
        var allRefunds = refundRecordRepository.findAll();

        Map<LocalDate, Long> cancelsByDay = allRefunds.stream()
                .filter(r -> r.getTatilTarihi() != null && "Kullanıcı rezervasyon iptali".equals(r.getTatilAciklama()))
                .collect(Collectors.groupingBy(
                        com.yemekhane.entity.RefundRecord::getTatilTarihi,
                        Collectors.counting()
                ));

        return cancelsByDay.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, Long>comparingByValue().reversed())
                .limit(limit)
                .map(e -> MostReservedDayDto.builder()
                        .reservationDate(e.getKey())
                        .dayOfWeek(e.getKey().getDayOfWeek()
                                .getDisplayName(TextStyle.FULL, new Locale("tr")))
                        .reservationCount(e.getValue())
                        .estimatedRevenue(e.getValue() * dailyPrice)
                        .build())
                .collect(Collectors.toList());
    }
}
