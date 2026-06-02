package com.yemekhane.service.impl;

import com.yemekhane.dto.MonthlyReservationDto;
import com.yemekhane.dto.ReservationRequest;
import com.yemekhane.entity.MonthlyReservation;
import com.yemekhane.entity.PaymentStatus;
import com.yemekhane.entity.PaymentTransaction;
import com.yemekhane.entity.RefundReason;
import com.yemekhane.entity.ReservationDay;
import com.yemekhane.entity.Role;
import com.yemekhane.entity.User;
import com.yemekhane.exception.BusinessException;
import com.yemekhane.repository.HolidayRepository;
import com.yemekhane.repository.MonthlyMenuRepository;
import com.yemekhane.repository.MonthlyReservationRepository;
import com.yemekhane.repository.PaymentTransactionRepository;
import com.yemekhane.repository.UserRepository;
import com.yemekhane.service.RefundLedgerService;
import com.yemekhane.service.ReservationService;
import com.yemekhane.service.factory.ReservationFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    @Value("${app.constants.daily-price:100.0}")
    private double dailyPrice;

    @Value("${app.constants.active-year:2026}")
    private int activeYear;

    @Value("${app.constants.timezone:Europe/Istanbul}")
    private String timezone;

    private final MonthlyReservationRepository reservationRepository;
    private final MonthlyMenuRepository menuRepository;
    private final HolidayRepository holidayRepository;
    private final UserRepository userRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final ReservationFactory reservationFactory;
    private final RefundLedgerService refundLedgerService;

    @Override
    @Transactional(readOnly = true)
    public List<MonthlyReservationDto> getAllReservations() {
        return reservationRepository.findAll().stream().map(MonthlyReservationDto::fromEntity).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MonthlyReservationDto> getUserReservations(Long userId) {
        return reservationRepository.findByUserIdOrderByIslemTarihiDesc(userId).stream().map(MonthlyReservationDto::fromEntity).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MonthlyReservationDto createMonthlyReservation(ReservationRequest request) {
        User user = userRepository.findById(request.getUserId()).orElseThrow(() -> new BusinessException("Kullanici bulunamadi."));
        if (Boolean.FALSE.equals(user.getActive())) throw new BusinessException("Pasif kullanici icin rezervasyon olusturulamaz.");
        if (user.getRol() != Role.KULLANICI) throw new BusinessException("Yalnizca kullanici hesabi icin rezervasyon olusturulabilir.");
        if (request.getSecilenGunler().isEmpty()) throw new BusinessException("En az bir gun secilmelidir.");
        validateReservationRequest(request, null);

        reservationRepository.findByUserIdAndYilAndAy(request.getUserId(), request.getYil(), request.getAy()).ifPresent(r -> {
            throw new BusinessException("Bu ay icin zaten bir rezervasyon mevcut.");
        });

        MonthlyReservation reservation = new MonthlyReservation();
        reservation.setUser(user);
        applyReservationValues(reservation, request, PaymentStatus.PAID);
        reservation.setPaidAmountKurus(reservation.getTotalAmountKurus());
        reservation.setReservationDays(buildReservationDays(request, reservation, user));

        try {
            MonthlyReservation savedReservation = reservationRepository.save(reservation);
            createPaymentTransaction(user, request.getYil(), request.getAy(), savedReservation.getIslemTarihi(), savedReservation.getSecilenGunSayisi(), savedReservation.getToplamTutar(), "YENI REZERVASYON");
            return MonthlyReservationDto.fromEntity(savedReservation);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("Bu ay icin zaten bir rezervasyon mevcut.");
        }
    }

    @Override
    @Transactional
    public MonthlyReservationDto updateMonthlyReservation(Long id, ReservationRequest request) {
        MonthlyReservation reservation = reservationRepository.findById(id).orElseThrow(() -> new BusinessException("Rezervasyon bulunamadi."));
        validateReservationOwnerAndUser(reservation, request.getUserId());

        int oldDays = reservation.getSecilenGunSayisi() != null ? reservation.getSecilenGunSayisi() : 0;
        int newDays = request.getSecilenGunler().size();
        int diffDays = newDays - oldDays;

        validateReservationRequest(request, reservation);
        createRefundsForCancelledDays(reservation, request, diffDays);

        PaymentStatus nextStatus = diffDays < 0 ? PaymentStatus.REFUND_PENDING : PaymentStatus.PAID;
        applyReservationValues(reservation, request, nextStatus);
        if (diffDays > 0) reservation.setPaidAmountKurus(reservation.getPaidAmountKurus() + toKurus(diffDays * dailyPrice));

        if (reservation.getReservationDays() == null) reservation.setReservationDays(new java.util.ArrayList<>()); else reservation.getReservationDays().clear();
        reservation.getReservationDays().addAll(buildReservationDays(request, reservation, reservation.getUser()));
        MonthlyReservation savedReservation = reservationRepository.save(reservation);

        if (diffDays != 0) {
            createPaymentTransaction(reservation.getUser(), request.getYil(), request.getAy(), savedReservation.getIslemTarihi(), diffDays, Math.abs(diffDays * dailyPrice), diffDays > 0 ? "EK ODEME" : "IPTAL");
        }
        return MonthlyReservationDto.fromEntity(savedReservation);
    }

    @Override
    @Transactional
    public List<MonthlyReservationDto> processBulkReservations(com.yemekhane.dto.BulkReservationRequest request) {
        User user = userRepository.findById(request.getUserId()).orElseThrow(() -> new BusinessException("Kullanici bulunamadi."));
        if (Boolean.FALSE.equals(user.getActive())) throw new BusinessException("Pasif kullanici icin rezervasyon islemi yapilamaz.");
        if (request.getYil() != activeYear) throw new BusinessException("Sistem yalnizca " + activeYear + " yili icin calismaktadir.");

        int totalOldDays = 0;
        int totalNewDays = 0;
        List<MonthlyReservation> savedReservations = new java.util.ArrayList<>();

        for (com.yemekhane.dto.BulkReservationRequest.MonthSelection selection : request.getSelections()) {
            totalNewDays += selection.getSecilenGunler().size();
            if (selection.getExistingReservationId() != null) {
                MonthlyReservation reservation = reservationRepository.findById(selection.getExistingReservationId()).orElseThrow(() -> new BusinessException("Rezervasyon bulunamadi."));
                validateReservationOwnerAndUser(reservation, request.getUserId());
                int oldDays = reservation.getSecilenGunSayisi() != null ? reservation.getSecilenGunSayisi() : 0;
                totalOldDays += oldDays;

                ReservationRequest tempReq = buildTempRequest(user.getId(), request.getYil(), selection.getAy(), selection.getSecilenGunler());
                validateReservationRequest(tempReq, reservation);
                createRefundsForCancelledDays(reservation, tempReq, selection.getSecilenGunler().size() - oldDays);
                applyReservationValues(reservation, tempReq, selection.getSecilenGunler().size() < oldDays ? PaymentStatus.REFUND_PENDING : PaymentStatus.PAID);
                if (selection.getSecilenGunler().size() > oldDays) reservation.setPaidAmountKurus(reservation.getPaidAmountKurus() + toKurus((selection.getSecilenGunler().size() - oldDays) * dailyPrice));
                if (reservation.getReservationDays() == null) reservation.setReservationDays(new java.util.ArrayList<>()); else reservation.getReservationDays().clear();
                reservation.getReservationDays().addAll(buildReservationDays(tempReq, reservation, user));
                savedReservations.add(reservationRepository.save(reservation));
            } else {
                ReservationRequest tempReq = buildTempRequest(user.getId(), request.getYil(), selection.getAy(), selection.getSecilenGunler());
                validateReservationRequest(tempReq, null);
                MonthlyReservation reservation = new MonthlyReservation();
                reservation.setUser(user);
                applyReservationValues(reservation, tempReq, PaymentStatus.PAID);
                reservation.setPaidAmountKurus(reservation.getTotalAmountKurus());
                reservation.setReservationDays(buildReservationDays(tempReq, reservation, user));
                savedReservations.add(reservationRepository.save(reservation));
            }
        }

        int globalDiffDays = totalNewDays - totalOldDays;
        if (globalDiffDays != 0) {
            createPaymentTransaction(user, request.getYil(), request.getSelections().isEmpty() ? 1 : request.getSelections().get(0).getAy(), LocalDateTime.now(ZoneId.of(timezone)), globalDiffDays, Math.abs(globalDiffDays * dailyPrice), globalDiffDays > 0 ? "EK ODEME" : "IPTAL");
        }
        return savedReservations.stream().map(MonthlyReservationDto::fromEntity).collect(Collectors.toList());
    }

    private ReservationRequest buildTempRequest(Long userId, Integer year, Integer month, List<LocalDate> days) {
        ReservationRequest tempReq = new ReservationRequest();
        tempReq.setUserId(userId);
        tempReq.setYil(year);
        tempReq.setAy(month);
        tempReq.setSecilenGunler(days);
        return tempReq;
    }

    private void validateReservationOwnerAndUser(MonthlyReservation reservation, Long userId) {
        if (!reservation.getUser().getId().equals(userId)) throw new BusinessException("Rezervasyon kullanici bilgisi ile istek kullanici bilgisi uyusmuyor.");
        if (Boolean.FALSE.equals(reservation.getUser().getActive())) throw new BusinessException("Pasif kullanici rezervasyonu guncellenemez.");
    }

    private void applyReservationValues(MonthlyReservation reservation, ReservationRequest request, PaymentStatus status) {
        reservationFactory.applyReservationValues(reservation, request.getYil(), request.getAy(), request.getSecilenGunler().size(), dailyPrice, status);
        reservation.setTotalAmountKurus(toKurus(request.getSecilenGunler().size() * dailyPrice));
        if (reservation.getPaidAmountKurus() == null || reservation.getPaidAmountKurus() == 0L) reservation.setPaidAmountKurus(reservation.getTotalAmountKurus());
        reservation.setIslemTarihi(LocalDateTime.now(ZoneId.of(timezone)));
    }

    private void createPaymentTransaction(User user, Integer yil, Integer ay, LocalDateTime islemTarihi, Integer gunSayisi, Double tutar, String islemTipi) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setUser(user);
        transaction.setYil(yil);
        transaction.setAy(ay);
        transaction.setIslemTarihi(islemTarihi);
        transaction.setIslemGunSayisi(gunSayisi);
        transaction.setIslemTutari(tutar);
        transaction.setIslemTipi(islemTipi);
        transactionRepository.save(transaction);
    }

    private List<ReservationDay> buildReservationDays(ReservationRequest request, MonthlyReservation reservation, User user) {
        return reservationFactory.buildReservationDays(request.getSecilenGunler(), reservation, user);
    }

    private void validateReservationRequest(ReservationRequest request, MonthlyReservation existingReservation) {
        if (request.getYil() != activeYear) throw new BusinessException("Sistem yalnizca " + activeYear + " yili icin calismaktadir.");
        Set<LocalDate> uniqueDates = new HashSet<>(request.getSecilenGunler());
        Set<LocalDate> existingDates = existingReservation == null || existingReservation.getReservationDays() == null ? Set.of() : existingReservation.getReservationDays().stream().map(ReservationDay::getTarih).collect(Collectors.toSet());
        if (uniqueDates.size() != request.getSecilenGunler().size()) throw new BusinessException("Ayni gun birden fazla secilemez.");
        LocalDate today = LocalDate.now(ZoneId.of(timezone));
        for (LocalDate date : uniqueDates) {
            if (date == null) throw new BusinessException("Secilen gunler bos olamaz.");
            if (date.getYear() != request.getYil() || date.getMonthValue() != request.getAy()) throw new BusinessException("Secilen gunler rezervasyon ayi ve yili ile uyumlu olmalidir.");
            boolean existingPastReservationDay = !date.isAfter(today) && existingDates.contains(date);
            if (!date.isAfter(today) && !existingPastReservationDay) throw new BusinessException("Bugun veya gecmis gunler icin rezervasyon yapilamaz.");
            if (existingPastReservationDay) continue;
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) throw new BusinessException("Hafta sonu icin rezervasyon yapilamaz.");
            if (holidayRepository.findByTarih(date).isPresent()) throw new BusinessException("Tatil gunu icin rezervasyon yapilamaz: " + date);
            if (menuRepository.findByTarih(date).isEmpty()) throw new BusinessException("Menusu tanimlanmamis gun icin rezervasyon yapilamaz: " + date);
        }
        if (existingReservation != null) {
            Set<LocalDate> removedDates = new HashSet<>(existingDates);
            removedDates.removeAll(uniqueDates);
            boolean removesPastOrToday = removedDates.stream().anyMatch(date -> !date.isAfter(today));
            if (removesPastOrToday) throw new BusinessException("Bugun veya gecmis gunlere ait rezervasyonlar iptal edilemez.");
        }
    }

    private void createRefundsForCancelledDays(MonthlyReservation reservation, ReservationRequest request, int diffDays) {
        if (reservation.getReservationDays() == null || diffDays >= 0) return;
        Set<LocalDate> requestedDates = new HashSet<>(request.getSecilenGunler());
        reservation.getReservationDays().stream()
                .map(ReservationDay::getTarih)
                .filter(date -> !requestedDates.contains(date))
                .filter(date -> date.isAfter(LocalDate.now(ZoneId.of(timezone))))
                .limit(Math.abs(diffDays))
                .forEach(date -> refundLedgerService.createRefundIfAllowed(reservation, date, toKurus(dailyPrice), RefundReason.USER_CANCELLED, "Kullanici rezervasyon iptali"));
    }

    private long toKurus(double amount) {
        return Math.round(amount * 100.0);
    }
}
