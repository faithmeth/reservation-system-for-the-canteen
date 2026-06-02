package com.yemekhane.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSummaryDto {
    private long paidReservationCount;
    private long pendingReservationCount;
    private double totalRevenue;
    private double grossRevenue;
    private double totalRefundAmount;
    private double totalRefundLiability;
    private double paidRefundTotal;
    private double pendingRefundTotal;
    private double netRevenue;
}
