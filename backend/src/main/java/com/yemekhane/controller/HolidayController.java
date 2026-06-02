package com.yemekhane.controller;

import com.yemekhane.dto.HolidayDto;
import com.yemekhane.dto.RefundRecordDto;
import com.yemekhane.entity.Role;
import com.yemekhane.exception.BusinessException;
import com.yemekhane.service.HolidayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import com.yemekhane.security.UserDetailsImpl;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/holidays")
@RequiredArgsConstructor
public class HolidayController {

    private final HolidayService holidayService;

    @GetMapping
    public ResponseEntity<List<HolidayDto>> getAllHolidays() {
        return ResponseEntity.ok(holidayService.getAllHolidays());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<HolidayDto> createHoliday(@Valid @RequestBody HolidayDto request) {
        return ResponseEntity.ok(holidayService.createHoliday(request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHoliday(@PathVariable Long id) {
        holidayService.deleteHoliday(id);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/refunds")
    public ResponseEntity<List<RefundRecordDto>> getAllRefunds() {
        return ResponseEntity.ok(holidayService.getAllRefunds());
    }

    @GetMapping("/refunds/user/{userId}")
    public ResponseEntity<List<RefundRecordDto>> getUserRefunds(@PathVariable Long userId, HttpServletRequest request) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || "anonymousUser".equals(principal)) throw new BusinessException("Oturum bulunamadi.");
        UserDetailsImpl authenticatedUser = (UserDetailsImpl) principal;
        if (authenticatedUser.getRoleEnum() != Role.ADMIN && !authenticatedUser.getId().equals(userId)) {
            throw new BusinessException("Baska bir kullanicinin iadelerine erisemezsiniz.");
        }
        return ResponseEntity.ok(holidayService.getUserRefunds(userId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/refunds/{id}/mark-refunded")
    public ResponseEntity<Void> markRefunded(@PathVariable Long id) {
        holidayService.markRefunded(id);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/refunds/mark-paid")
    public ResponseEntity<Map<String, Integer>> markRefundsPaid(@RequestBody RefundIdsRequest request) {
        int updated = holidayService.markRefundsPaid(request.getIds());
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/refunds/mark-all-paid")
    public ResponseEntity<Map<String, Integer>> markAllPendingRefunded() {
        int updated = holidayService.markAllPendingRefunded();
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @Data
    public static class RefundIdsRequest {
        private List<Long> ids;
    }
}
