package com.yemekhane.dto;

import com.yemekhane.entity.RefundReason;
import com.yemekhane.entity.RefundRecord;
import com.yemekhane.entity.RefundStatus;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class RefundRecordDto {
    private Long id;
    private UserDto user;
    private Long userId;
    private Long reservationId;
    private LocalDate refundDay;
    private Long amountKurus;
    private Double amount;
    private RefundReason reason;
    private RefundStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private LocalDate tatilTarihi;
    private String tatilAciklama;
    private Double iadeEdilen;
    private LocalDateTime islemTarihi;
    private Boolean isRefunded;

    public static RefundRecordDto fromEntity(RefundRecord record) {
        record.syncLegacyFields();
        RefundRecordDto dto = new RefundRecordDto();
        dto.id = record.getId();
        dto.user = UserDto.fromEntity(record.getUser());
        dto.userId = record.getUser().getId();
        dto.reservationId = record.getReservation() == null ? null : record.getReservation().getId();
        dto.refundDay = record.getRefundDay();
        dto.amountKurus = record.getAmountKurus();
        dto.amount = record.getAmountKurus() == null ? 0.0 : record.getAmountKurus() / 100.0;
        dto.reason = record.getReason();
        dto.status = record.getStatus();
        dto.createdAt = record.getCreatedAt();
        dto.paidAt = record.getPaidAt();
        dto.tatilTarihi = record.getTatilTarihi();
        dto.tatilAciklama = record.getTatilAciklama();
        dto.iadeEdilen = record.getIadeEdilen();
        dto.islemTarihi = record.getIslemTarihi();
        dto.isRefunded = record.getIsRefunded();
        return dto;
    }
}
