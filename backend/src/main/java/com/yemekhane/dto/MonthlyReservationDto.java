package com.yemekhane.dto;

import com.yemekhane.entity.MonthlyReservation;
import com.yemekhane.entity.PaymentStatus;
import com.yemekhane.entity.RefundStatus;
import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

@Data
public class MonthlyReservationDto {
    private Long id;
    private UserDto user;
    private Integer yil;
    private Integer ay;
    private Integer secilenGunSayisi;
    private Double toplamTutar;
    private Long totalAmountKurus;
    private Long paidAmountKurus;
    private Double paidAmount;
    private Long reservationRefundTotalKurus;
    private Double reservationRefundTotal;
    private PaymentStatus odemeDurumu;
    private LocalDateTime islemTarihi;
    private List<LocalDate> secilenGunler;

    public static MonthlyReservationDto fromEntity(MonthlyReservation mr) {
        MonthlyReservationDto dto = new MonthlyReservationDto();
        dto.setId(mr.getId());
        dto.setUser(UserDto.fromEntity(mr.getUser()));
        dto.setYil(mr.getYil());
        dto.setAy(mr.getAy());
        dto.setSecilenGunSayisi(mr.getSecilenGunSayisi());
        dto.setToplamTutar(mr.getToplamTutar());
        dto.setTotalAmountKurus(mr.getTotalAmountKurus());
        dto.setPaidAmountKurus(mr.getPaidAmountKurus());
        dto.setPaidAmount(mr.getPaidAmountKurus() == null ? 0.0 : mr.getPaidAmountKurus() / 100.0);
        dto.setReservationRefundTotalKurus(activeRefundTotal(mr));
        dto.setReservationRefundTotal(dto.getReservationRefundTotalKurus() / 100.0);
        dto.setOdemeDurumu(mr.getOdemeDurumu());
        dto.setIslemTarihi(mr.getIslemTarihi());
        if (mr.getReservationDays() != null) {
            dto.setSecilenGunler(mr.getReservationDays().stream().map(d -> d.getTarih()).collect(java.util.stream.Collectors.toList()));
        }
        return dto;
    }

    private static long activeRefundTotal(MonthlyReservation mr) {
        if (mr.getRefundRecords() == null) return 0L;
        return mr.getRefundRecords().stream()
                .filter(r -> r.getStatus() == RefundStatus.PENDING || r.getStatus() == RefundStatus.PAID)
                .mapToLong(r -> r.getAmountKurus() == null ? 0L : r.getAmountKurus())
                .sum();
    }
}
