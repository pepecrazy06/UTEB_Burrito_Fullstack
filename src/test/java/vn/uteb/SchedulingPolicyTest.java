package vn.uteb;

import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static vn.uteb.Domain.Store;

class SchedulingPolicyTest {
    final SchedulingPolicy policy=new SchedulingPolicy(Clock.fixed(Instant.parse("2026-10-25T01:00:00Z"),ZoneId.of("America/New_York")));
    Store store(String hours){Store store=new Store();store.name="Chi nhánh kiểm thử";store.hours=hours;return store;}
    @ParameterizedTest @ValueSource(strings={"10:00–22:00","10:00-22:00"," 10:00 — 22:00 ","8:00 – 22:00"})
    void supportedDailyFormats(String hours){assertThatCode(()->policy.validate(store(hours),LocalDateTime.parse("2026-10-26T11:00"))).doesNotThrowAnyException();}
    @ParameterizedTest @ValueSource(strings={"2026-10-26T10:00","2026-10-26T12:00","2026-10-26T21:59:59.999999999"})
    void openingIsInclusive(String time){assertThatCode(()->policy.validate(store("10:00–22:00"),policy.scheduledTime(time))).doesNotThrowAnyException();}
    @ParameterizedTest @ValueSource(strings={"2026-10-26T09:59","2026-10-26T22:00","2026-10-26T22:01"})
    void closingIsExclusiveAndErrorsIdentifyBranchAndHours(String time){assertThatThrownBy(()->policy.validate(store("10:00–22:00"),policy.scheduledTime(time))).hasMessageContaining("Chi nhánh kiểm thử").hasMessageContaining("10:00–22:00").hasMessageContaining("chọn lại");}
    @ParameterizedTest @ValueSource(strings={"2026-10-26T22:00","2026-10-26T23:59","2026-10-27T00:00","2026-10-27T01:59"})
    void overnightWindowIncludesBothSidesOfMidnight(String time){assertThatCode(()->policy.validate(store("22:00–02:00"),policy.scheduledTime(time))).doesNotThrowAnyException();}
    @ParameterizedTest @ValueSource(strings={"2026-10-26T02:00","2026-10-26T12:00","2026-10-26T21:59"})
    void overnightClosingAndDaytimeAreRejected(String time){assertThatThrownBy(()->policy.validate(store("22:00–02:00"),policy.scheduledTime(time))).hasMessageContaining("ngày hôm sau");}
    @ParameterizedTest @ValueSource(strings={"","24/24","24:00–02:00","10:60-22:00","10:00","10:00–10:00","Thứ 2: 10:00–22:00","10:00–12:00, 14:00–22:00"})
    void unknownOrAmbiguousHoursFailClosed(String hours){assertThatThrownBy(()->policy.validate(store(hours),policy.scheduledTime("2026-10-26T12:00"))).hasMessageContaining("chưa có giờ hoạt động hợp lệ");}
    @ParameterizedTest @ValueSource(strings={"2026-10-24T12:00","2026-10-25T07:59","2026-10-25T08:00"})
    void rejectsPastAndNow(String time){assertThatThrownBy(()->policy.scheduledTime(time)).hasMessageContaining("thời gian đã qua");}
    @ParameterizedTest @ValueSource(strings={"2026-10-25T08:01","2026-10-25T08:30"})
    void preservesStrictThirtyMinuteLeadTime(String time){assertThatThrownBy(()->policy.scheduledTime(time)).hasMessageContaining("30 phút");}
    @Test void explicitVietnamClockIgnoresTheSuppliedClocksOriginalZone(){assertThat(policy.scheduledTime("2026-10-25T08:31")).isEqualTo(LocalDateTime.parse("2026-10-25T08:31"));}
    @ParameterizedTest @ValueSource(strings={"2026-11-01T08:00","2026-11-02T10:00"})
    void rejectsSevenDayBoundaryAndBeyond(String time){assertThatThrownBy(()->policy.scheduledTime(time)).hasMessageContaining("7 ngày");}
    @Test void acceptsJustBeforeSevenDayBoundary(){assertThat(policy.scheduledTime("2026-11-01T07:59:59")).isNotNull();}
    @ParameterizedTest @ValueSource(strings={"invalid","2026-02-30T10:00","2026-10-26","2026-10-26T10:00Z","2026-10-26T10:00+07:00"})
    void rejectsMalformedOrOffsetDateTime(String value){assertThatThrownBy(()->policy.scheduledTime(value)).hasMessageContaining("Ngày giờ hẹn không hợp lệ");}
    @Test void noScheduleRemainsAsap(){assertThat(policy.value(Map.of())).isEqualTo("ASAP");assertThat(policy.value(Map.of("scheduleMode","ASAP","scheduledAt","invalid"))).isEqualTo("ASAP");assertThat(policy.scheduledTime("ASAP")).isNull();assertThatCode(()->policy.validate(store("invalid"),null)).doesNotThrowAnyException();}
    @Test void scheduleModeCannotSilentlyBecomeAsap(){assertThatThrownBy(()->policy.value(Map.of("scheduleMode","SCHEDULE","scheduledAt",""))).hasMessageContaining("Vui lòng chọn");assertThatThrownBy(()->policy.value(Map.of("scheduleMode","SCHEDULE","scheduledAt","ASAP"))).hasMessageContaining("Vui lòng chọn");assertThatThrownBy(()->policy.value(Map.of("scheduleMode","INVALID"))).hasMessageContaining("không hợp lệ");}
    @Test void inactiveBranchIsNotAClaimAboutUnsupportedHolidayCalendar(){Store store=store("10:00–22:00");store.active=false;assertThatThrownBy(()->policy.validate(store,policy.scheduledTime("2026-10-26T12:00"))).hasMessageContaining("ngừng nhận đơn");}
}
