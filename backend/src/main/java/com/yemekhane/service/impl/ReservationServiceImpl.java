package com.yemekhane.service.impl;

import com.yemekhane.dto.MonthlyReservationDto;
import com.yemekhane.dto.ReservationRequest;
import com.yemekhane.entity.MonthlyReservation;
import com.yemekhane.entity.PaymentStatus;
import com.yemekhane.entity.PaymentTransaction;
import com.yemekhane.entity.RefundRecord;
import com.yemekhane.entity.ReservationDay;
import com.yemekhane.entity.Role;
import com.yemekhane.entity.User;
import com.yemekhane.exception.BusinessException;
import com.yemekhane.repository.HolidayRepository;
import com.yemekhane.repository.MonthlyMenuRepository;
import com.yemekhane.repository.MonthlyReservationRepository;
import com.yemekhane.repository.PaymentTransactionRepository;
import com.yemekhane.repository.RefundRecordRepository;
import com.yemekhane.repository.UserRepository;
import com.yemekhane.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.LocalDateTime;
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
    private final RefundRecordRepository refundRecordRepository;
    private final UserRepository userRepository;
    private final PaymentTransactionRepository transactionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<MonthlyReservationDto> getAllReservations() {
        return reservationRepository.findAllByOrderByIslemTarihiDesc()
                .stream()
                .map(MonthlyReservationDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MonthlyReservationDto> getUserReservations(Long userId) {
        return reservationRepository.findByUserIdOrderByIslemTarihiDesc(userId)
                .stream()
                .map(MonthlyReservationDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MonthlyReservationDto createMonthlyReservation(ReservationRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new BusinessException("Kullanıcı bulunamadı."));
        if (user.getRol() != Role.KULLANICI) {
            throw new BusinessException("Yalnızca kullanıcı hesabı için rezervasyon oluşturulabilir.");
        }

        if (request.getSecilenGunler().isEmpty()) {
            throw new BusinessException("En az bir gün seçilmelidir.");
        }
        validateReservationRequest(request, null);

        reservationRepository.findByUserIdAndYilAndAy(request.getUserId(), request.getYil(), request.getAy())
                .ifPresent(r -> {
                    throw new BusinessException("Bu ay için zaten bir rezervasyon mevcut.");
                });

        MonthlyReservation reservation = new MonthlyReservation();
        reservation.setUser(user);
        applyReservationValues(reservation, request);

        List<ReservationDay> days = buildReservationDays(request, reservation, user);
        reservation.setReservationDays(days);

        MonthlyReservation savedReservation = reservationRepository.save(reservation);

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setUser(user);
        transaction.setYil(request.getYil());
        transaction.setAy(request.getAy());
        transaction.setIslemTarihi(savedReservation.getIslemTarihi());
        transaction.setIslemGunSayisi(savedReservation.getSecilenGunSayisi());
        transaction.setIslemTutari(savedReservation.getToplamTutar());
        transaction.setIslemTipi("YENİ REZERVASYON");
        transactionRepository.save(transaction);

        return MonthlyReservationDto.fromEntity(savedReservation);
    }

    @Override
    @Transactional
    public MonthlyReservationDto updateMonthlyReservation(Long id, ReservationRequest request) {
        MonthlyReservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Rezervasyon bulunamadı."));

        if (!reservation.getUser().getId().equals(request.getUserId())) {
            throw new BusinessException("Rezervasyon kullanıcı bilgisi ile istek kullanıcı bilgisi uyuşmuyor.");
        }

        int oldDays = reservation.getSecilenGunSayisi() != null ? reservation.getSecilenGunSayisi() : 0;
        int newDays = request.getSecilenGunler().size();
        int diffDays = newDays - oldDays;

        validateReservationRequest(request, reservation);
        createRefundsForCancelledDays(reservation, request, diffDays);

        applyReservationValues(reservation, request);

        if (reservation.getReservationDays() == null) {
            reservation.setReservationDays(new java.util.ArrayList<>());
        } else {
            reservation.getReservationDays().clear();
        }

        reservation.getReservationDays().addAll(buildReservationDays(request, reservation, reservation.getUser()));

        MonthlyReservation savedReservation = reservationRepository.save(reservation);

        if (diffDays != 0) {
            PaymentTransaction transaction = new PaymentTransaction();
            transaction.setUser(reservation.getUser());
            transaction.setYil(request.getYil());
            transaction.setAy(request.getAy());
            transaction.setIslemTarihi(savedReservation.getIslemTarihi());
            transaction.setIslemGunSayisi(diffDays);
            transaction.setIslemTutari(Math.abs(diffDays * dailyPrice));
            transaction.setIslemTipi(diffDays > 0 ? "EK ÖDEME" : "İPTAL");
            transactionRepository.save(transaction);
        }

        return MonthlyReservationDto.fromEntity(savedReservation);
    }

    @Override
    @Transactional
    public List<MonthlyReservationDto> processBulkReservations(com.yemekhane.dto.BulkReservationRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new BusinessException("Kullanıcı bulunamadı."));

        if (request.getYil() != activeYear) {
            throw new BusinessException("Sistem yalnızca " + activeYear + " yılı için çalışmaktadır.");
        }

        List<MonthlyReservation> savedReservations = new java.util.ArrayList<>();

        for (com.yemekhane.dto.BulkReservationRequest.MonthSelection selection : request.getSelections()) {
            ReservationRequest tempReq = new ReservationRequest();
            tempReq.setUserId(user.getId());
            tempReq.setYil(request.getYil());
            tempReq.setAy(selection.getAy());
            tempReq.setSecilenGunler(selection.getSecilenGunler());

            int oldDays = 0;
            MonthlyReservation reservation;
            boolean isNew = true;

            if (selection.getExistingReservationId() != null) {
                reservation = reservationRepository.findById(selection.getExistingReservationId())
                        .orElseThrow(() -> new BusinessException("Rezervasyon bulunamadı."));
                
                if (!reservation.getUser().getId().equals(request.getUserId())) {
                    throw new BusinessException("Rezervasyon kullanıcı bilgisi ile istek kullanıcı bilgisi uyuşmuyor.");
                }

                oldDays = reservation.getSecilenGunSayisi() != null ? reservation.getSecilenGunSayisi() : 0;
                isNew = false;

                int diffDays = selection.getSecilenGunler().size() - oldDays;
                validateReservationRequest(tempReq, reservation);
                createRefundsForCancelledDays(reservation, tempReq, diffDays);
                applyReservationValues(reservation, tempReq);

                if (reservation.getReservationDays() == null) {
                    reservation.setReservationDays(new java.util.ArrayList<>());
                } else {
                    reservation.getReservationDays().clear();
                }
                reservation.getReservationDays().addAll(buildReservationDays(tempReq, reservation, user));
            } else {
                validateReservationRequest(tempReq, null);

                reservation = new MonthlyReservation();
                reservation.setUser(user);
                applyReservationValues(reservation, tempReq);
                List<ReservationDay> days = buildReservationDays(tempReq, reservation, user);
                reservation.setReservationDays(days);
            }

            MonthlyReservation savedReservation = reservationRepository.save(reservation);
            savedReservations.add(savedReservation);

            int newDays = selection.getSecilenGunler().size();
            int diffDays = newDays - oldDays;

            if (diffDays != 0) {
                PaymentTransaction transaction = new PaymentTransaction();
                transaction.setUser(user);
                transaction.setYil(request.getYil());
                transaction.setAy(selection.getAy());
                transaction.setIslemTarihi(savedReservation.getIslemTarihi());
                transaction.setIslemGunSayisi(Math.abs(diffDays));
                transaction.setIslemTutari(Math.abs(diffDays * dailyPrice));
                transaction.setIslemTipi(isNew ? "YENİ REZERVASYON" : (diffDays > 0 ? "EK ÖDEME" : "İPTAL"));
                transactionRepository.save(transaction);
            }
        }

        return savedReservations.stream().map(MonthlyReservationDto::fromEntity).collect(Collectors.toList());
    }

    private void applyReservationValues(MonthlyReservation reservation, ReservationRequest request) {
        reservation.setYil(request.getYil());
        reservation.setAy(request.getAy());
        reservation.setSecilenGunSayisi(request.getSecilenGunler().size());
        reservation.setToplamTutar(request.getSecilenGunler().size() * dailyPrice);
        reservation.setOdemeDurumu(PaymentStatus.ODENDI);
        reservation.setIslemTarihi(LocalDateTime.now(ZoneId.of(timezone)));
    }

    private List<ReservationDay> buildReservationDays(ReservationRequest request, MonthlyReservation reservation, User user) {
        return request.getSecilenGunler().stream()
                .map(date -> {
                    ReservationDay day = new ReservationDay();
                    day.setMonthlyReservation(reservation);
                    day.setUser(user);
                    day.setTarih(date);
                    return day;
                })
                .collect(Collectors.toList());
    }

    private void validateReservationRequest(ReservationRequest request, MonthlyReservation existingReservation) {
        if (request.getYil() != activeYear) {
            throw new BusinessException("Sistem yalnızca " + activeYear + " yılı için çalışmaktadır.");
        }

        Set<LocalDate> uniqueDates = new HashSet<>(request.getSecilenGunler());
        Set<LocalDate> existingDates = existingReservation == null || existingReservation.getReservationDays() == null
                ? Set.of()
                : existingReservation.getReservationDays().stream()
                        .map(ReservationDay::getTarih)
                        .collect(Collectors.toSet());

        if (uniqueDates.size() != request.getSecilenGunler().size()) {
            throw new BusinessException("Aynı gün birden fazla seçilemez.");
        }

        LocalDate today = LocalDate.now(ZoneId.of(timezone));
        for (LocalDate date : uniqueDates) {
            if (date == null) {
                throw new BusinessException("Seçilen günler boş olamaz.");
            }
            if (date.getYear() != request.getYil() || date.getMonthValue() != request.getAy()) {
                throw new BusinessException("Seçilen günler rezervasyon ayı ve yılı ile uyumlu olmalıdır.");
            }
            boolean existingPastReservationDay = !date.isAfter(today) && existingDates.contains(date);
            if (!date.isAfter(today) && !existingPastReservationDay) {
                throw new BusinessException("Bugün veya geçmiş günler için rezervasyon yapılamaz.");
            }
            if (existingPastReservationDay) {
                continue;
            }
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                throw new BusinessException("Hafta sonu için rezervasyon yapılamaz.");
            }
            if (holidayRepository.findByTarih(date).isPresent()) {
                throw new BusinessException("Tatil günü için rezervasyon yapılamaz: " + date);
            }
            if (menuRepository.findByTarih(date).isEmpty()) {
                throw new BusinessException("Menüsü tanımlanmamış gün için rezervasyon yapılamaz: " + date);
            }
        }

        if (existingReservation != null) {
            Set<LocalDate> removedDates = new HashSet<>(existingDates);
            removedDates.removeAll(uniqueDates);

            boolean removesPastOrToday = removedDates.stream().anyMatch(date -> !date.isAfter(today));
            if (removesPastOrToday) {
                throw new BusinessException("Bugün veya geçmiş günlere ait rezervasyonlar iptal edilemez.");
            }
        }
    }

    private void createRefundsForCancelledDays(MonthlyReservation reservation, ReservationRequest request, int diffDays) {
        if (reservation.getReservationDays() == null || diffDays >= 0) {
            return; // Only create refunds if there's a net reduction in days
        }

        Set<LocalDate> requestedDates = new HashSet<>(request.getSecilenGunler());
        List<LocalDate> cancelledDates = reservation.getReservationDays().stream()
                .map(ReservationDay::getTarih)
                .filter(date -> !requestedDates.contains(date))
                .filter(date -> date.isAfter(LocalDate.now(ZoneId.of(timezone))))
                .collect(Collectors.toList());

        int netCancelledCount = Math.abs(diffDays);
        List<LocalDate> netCancelledDates = cancelledDates.stream()
                .limit(netCancelledCount)
                .collect(Collectors.toList());

        for (LocalDate date : netCancelledDates) {
            RefundRecord refund = new RefundRecord();
            refund.setUser(reservation.getUser());
            refund.setTatilTarihi(date);
            refund.setTatilAciklama("Kullanıcı rezervasyon iptali");
            refund.setIadeEdilen(dailyPrice);
            refund.setIsRefunded(false);
            refundRecordRepository.save(refund);
        }
    }
}
