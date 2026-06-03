package com.yemekhane.service.impl;

import com.yemekhane.dto.HolidayDto;
import com.yemekhane.dto.ReservationRequest;
import com.yemekhane.entity.MonthlyMenu;
import com.yemekhane.entity.RefundStatus;
import com.yemekhane.entity.User;
import com.yemekhane.exception.BusinessException;
import com.yemekhane.repository.MonthlyMenuRepository;
import com.yemekhane.repository.MonthlyReservationRepository;
import com.yemekhane.repository.RefundRecordRepository;
import com.yemekhane.repository.UserRepository;
import com.yemekhane.service.HolidayService;
import com.yemekhane.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:reservation_service_test;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false",
        "app.constants.active-year=2026",
        "app.constants.daily-price=100.0",
        "app.constants.timezone=Europe/Istanbul",
        "app.security.jwt-secret=reservation-service-test-secret-key-that-is-long-enough"
})
class ReservationServiceImplTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private HolidayService holidayService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MonthlyMenuRepository menuRepository;

    @Autowired
    private MonthlyReservationRepository reservationRepository;

    @Autowired
    private RefundRecordRepository refundRecordRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.findByEmailAndActiveTrue("kullanici@yemekhane.com").orElseThrow();
    }

    @Test
    void createsReservationWhenBusinessRulesAreSatisfied() {
        LocalDate date = LocalDate.of(2026, 11, 2);
        createMenu(date);

        var reservation = reservationService.createMonthlyReservation(request(date));

        assertThat(reservation.getSecilenGunSayisi()).isEqualTo(1);
        assertThat(reservation.getToplamTutar()).isEqualTo(100.0);
        assertThat(reservation.getPaidAmountKurus()).isEqualTo(10000L);
        assertThat(reservationRepository.findByUserIdAndYilAndAy(user.getId(), 2026, 11)).isPresent();
    }

    @Test
    void rejectsDuplicateSelectedDays() {
        LocalDate date = LocalDate.of(2026, 11, 3);
        createMenu(date);

        ReservationRequest request = new ReservationRequest();
        request.setUserId(user.getId());
        request.setYil(2026);
        request.setAy(11);
        request.setSecilenGunler(List.of(date, date));

        assertThatThrownBy(() -> reservationService.createMonthlyReservation(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ayni gun");
    }

    @Test
    void rejectsWeekendReservation() {
        LocalDate saturday = LocalDate.of(2026, 11, 7);
        createMenu(saturday);

        assertThatThrownBy(() -> reservationService.createMonthlyReservation(request(saturday)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Hafta sonu");
    }

    @Test
    void rejectsDateWithoutMenu() {
        LocalDate dateWithoutMenu = LocalDate.of(2026, 11, 4);

        assertThatThrownBy(() -> reservationService.createMonthlyReservation(request(dateWithoutMenu)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Menusu tanimlanmamis");
    }

    @Test
    void repeatedCancellationCreatesSingleRefundAndDoesNotExceedPaidAmount() {
        LocalDate d1 = LocalDate.of(2026, 11, 9);
        LocalDate d2 = LocalDate.of(2026, 11, 10);
        LocalDate d3 = LocalDate.of(2026, 11, 11);
        createMenu(d1);
        createMenu(d2);
        createMenu(d3);

        var reservation = reservationService.createMonthlyReservation(request(d1, d2, d3));
        ReservationRequest updated = request(d1);

        reservationService.updateMonthlyReservation(reservation.getId(), updated);
        reservationService.updateMonthlyReservation(reservation.getId(), updated);

        var refunds = refundRecordRepository.findAll();
        assertThat(refunds).hasSize(2);
        assertThat(refunds).allSatisfy(refund -> {
            assertThat(refund.getReservation().getId()).isEqualTo(reservation.getId());
            assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
            assertThat(refund.getAmountKurus()).isEqualTo(10000L);
        });
        assertThat(refundRecordRepository.sumActiveAmountKurusByReservationId(reservation.getId())).isEqualTo(20000L);
        assertThat(refundRecordRepository.sumActiveAmountKurusByReservationId(reservation.getId())).isLessThanOrEqualTo(30000L);
    }

    @Test
    void holidayRefundDoesNotDuplicateUserCancellationForSameDay() {
        LocalDate d1 = LocalDate.of(2026, 11, 12);
        LocalDate d2 = LocalDate.of(2026, 11, 13);
        createMenu(d1);
        createMenu(d2);

        var reservation = reservationService.createMonthlyReservation(request(d1, d2));
        reservationService.updateMonthlyReservation(reservation.getId(), request(d1));

        HolidayDto holiday = new HolidayDto();
        holiday.setTarih(d2);
        holiday.setAciklama("Test resmi iptal");
        holidayService.createHoliday(holiday);

        var refunds = refundRecordRepository.findAll();
        assertThat(refunds).hasSize(1);
        assertThat(refunds.get(0).getRefundDay()).isEqualTo(d2);
        assertThat(refundRecordRepository.sumActiveAmountKurusByReservationId(reservation.getId())).isEqualTo(10000L);
    }

    @Test
    void markingPendingRefundPaidChangesOnlyPendingRecords() {
        LocalDate d1 = LocalDate.of(2026, 11, 16);
        LocalDate d2 = LocalDate.of(2026, 11, 17);
        createMenu(d1);
        createMenu(d2);

        var reservation = reservationService.createMonthlyReservation(request(d1, d2));
        reservationService.updateMonthlyReservation(reservation.getId(), request(d1));
        var refund = refundRecordRepository.findAll().get(0);

        holidayService.markRefunded(refund.getId());
        holidayService.markRefunded(refund.getId());

        var reloaded = refundRecordRepository.findById(refund.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RefundStatus.PAID);
        assertThat(reloaded.getPaidAt()).isNotNull();
        assertThat(refundRecordRepository.sumAmountKurusByStatus(RefundStatus.PAID)).isEqualTo(10000L);
    }

    private ReservationRequest request(LocalDate... dates) {
        ReservationRequest request = new ReservationRequest();
        request.setUserId(user.getId());
        request.setYil(dates[0].getYear());
        request.setAy(dates[0].getMonthValue());
        request.setSecilenGunler(List.of(dates));
        return request;
    }

    private void createMenu(LocalDate date) {
        MonthlyMenu menu = new MonthlyMenu();
        menu.setYil(date.getYear());
        menu.setAy(date.getMonthValue());
        menu.setGun(date.getDayOfMonth());
        menu.setTarih(date);
        menu.setYemekListesi("Test Corba, Test Ana Yemek, Test Pilav");
        menu.setAktifMi(true);
        menuRepository.save(menu);
    }
}
