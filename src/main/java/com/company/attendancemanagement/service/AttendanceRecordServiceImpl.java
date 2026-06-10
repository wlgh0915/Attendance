package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.record.AttendanceRecordDto;
import com.company.attendancemanagement.mapper.AttendanceRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AttendanceRecordServiceImpl implements AttendanceRecordService {

    private static final int MAX_WEEK_MIN = 3120;
    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;

    private final AttendanceRecordMapper recordMapper;

    @Override
    public List<AttendanceRecordDto> findByMonth(String company, String empCode,
                                                  String startYmd, String endYmd) {
        return recordMapper.findByMonth(company, empCode, startYmd, endYmd);
    }

    @Override
    public AttendanceRecordDto findByDay(String company, String empCode, String yyyymmdd) {
        return recordMapper.findByDay(company, empCode, yyyymmdd);
    }

    @Override
    public Map<String, Object> getPlannedShift(String company, String empCode, String yyyymmdd) {
        Map<String, Object> planned = recordMapper.findPlannedShift(company, empCode, yyyymmdd);
        return planned != null ? planned : Map.of();
    }

    @Override
    public void upsert(AttendanceRecordDto dto) {
        String company = dto.getCompany();
        String empCode = dto.getEmpCode();
        String yyyymmdd = dto.getYyyymmdd();

        if (recordMapper.countRetiredOnDate(company, empCode, yyyymmdd) > 0) {
            throw new IllegalArgumentException("퇴사일 이후에는 출퇴근을 등록할 수 없습니다.");
        }

        int workMin = calculateWorkMin(dto);
        dto.setWorkMin(workMin);

        String weekStart = toWeekBound(yyyymmdd, true);
        String weekEnd = toWeekBound(yyyymmdd, false);
        int weekOtherMin = recordMapper.sumWeekWorkMin(company, empCode, weekStart, weekEnd, yyyymmdd);
        int weekTotalMin = weekOtherMin + workMin;
        if (weekTotalMin > MAX_WEEK_MIN) {
            int remaining = MAX_WEEK_MIN - weekOtherMin;
            throw new IllegalArgumentException(String.format(
                    "주 52시간을 초과합니다. 이번 주 잔여 가능 시간: %d시간 %d분",
                    remaining / 60, remaining % 60));
        }

        calculateLate(dto);
        recordMapper.upsert(dto);
    }

    @Override
    public int calculateWorkMin(AttendanceRecordDto dto) {
        String checkIn = dto.getCheckIn();
        String checkOut = dto.getCheckOut();

        if (isBlank(checkIn)) {
            // actualShiftCode 있으면 → 출장 등 OTHER 승인 레코드 → 계획 근무분 반환
            if (!isBlank(dto.getActualShiftCode())) {
                Map<String, Object> planned = recordMapper.findPlannedShift(
                        dto.getCompany(), dto.getEmpCode(), dto.getYyyymmdd());
                int shiftMin = calcShiftMinFromPlanned(planned);
                if (shiftMin > 0) return shiftMin;
            }
            return dto.getWorkMin() != null ? dto.getWorkMin() : 0;
        }

        int actualInMin = toMinutes(checkIn);

        List<Map<String, Object>> overtimes = recordMapper.findApprovedOvertimeRequests(
                dto.getCompany(), dto.getEmpCode(), dto.getYyyymmdd());

        Map<String, Object> planned = recordMapper.findPlannedShift(
                dto.getCompany(), dto.getEmpCode(), dto.getYyyymmdd());

        int actualOutMin;
        if (isBlank(checkOut)) {
            // checkOut 없으면 계획 퇴근시간(+ 승인된 연장/휴일근무 종료시간)을 가상 퇴근으로 사용
            actualOutMin = resolveVirtualCheckout(planned, overtimes, actualInMin);
            if (actualOutMin <= actualInMin) return 0;
        } else {
            actualOutMin = toActualOutMinute(dto, actualInMin);
        }

        if (planned == null
                || planned.get("workOnHhmm") == null
                || planned.get("workOffHhmm") == null) {
            // OFF/HOLIDAY 날: 휴일근무 승인 범위 내로 cap (승인된 연장도 포함)
            Map<String, Object> holidayWork = overtimes.stream()
                    .filter(r -> "휴일근무".equals(r.get("reqType")))
                    .findFirst().orElse(null);
            if (holidayWork != null
                    && holidayWork.get("startTime") != null
                    && holidayWork.get("endTime") != null) {
                int approvedStart = toMinutes(holidayWork.get("startTime").toString());
                int approvedEnd   = toMinutes(holidayWork.get("endTime").toString());
                // 휴일에 추가 연장 승인이 있으면 종료 상한을 연장 종료시간까지 확장
                Map<String, Object> overtimeApproval = overtimes.stream()
                        .filter(r -> "연장".equals(r.get("reqType")))
                        .findFirst().orElse(null);
                int effectiveCap = approvedEnd;
                if (overtimeApproval != null && overtimeApproval.get("endTime") != null) {
                    int approvedOtEnd = toMinutes(overtimeApproval.get("endTime").toString());
                    String ett = String.valueOf(overtimeApproval.getOrDefault("endTimeType", "N0"));
                    if ("N1".equals(ett) || approvedOtEnd == 0) approvedOtEnd += 1440;
                    effectiveCap = Math.max(approvedEnd, approvedOtEnd);
                }
                int effIn  = Math.max(actualInMin, approvedStart);
                int effOut = Math.min(actualOutMin, effectiveCap);
                return Math.max(0, effOut - effIn);
            }
            return Math.max(0, actualOutMin - actualInMin);
        }

        // 출근 유효 시작 시간: 조출연장 승인 시 승인 시작 시간, 미승인 시 계획 출근시간으로 cap
        Map<String, Object> earlyApproval = overtimes.stream()
                .filter(r -> "조출연장".equals(r.get("reqType")))
                .findFirst().orElse(null);

        int effectiveInMin = actualInMin;
        if (earlyApproval != null && earlyApproval.get("startTime") != null) {
            int approvedEarlyStart = toMinutes(earlyApproval.get("startTime").toString());
            effectiveInMin = Math.max(actualInMin, approvedEarlyStart);
        } else {
            // 조출연장 미승인 또는 startTime 없는 경우 → 계획 출근시간으로 cap
            int plannedOnMin = toMinutes(planned.get("workOnHhmm").toString());
            effectiveInMin = Math.max(actualInMin, plannedOnMin);
        }

        // 연장 승인 시 승인 종료시간까지만, 미승인·endTime없는 경우 계획 퇴근시간까지만 인정
        Map<String, Object> overtimeApproval = overtimes.stream()
                .filter(r -> "연장".equals(r.get("reqType")))
                .findFirst().orElse(null);
        int effectiveOutMin = actualOutMin;
        if (overtimeApproval != null && overtimeApproval.get("endTime") != null) {
            int approvedEndMin = toMinutes(overtimeApproval.get("endTime").toString());
            String endTimeType = String.valueOf(overtimeApproval.getOrDefault("endTimeType", "N0"));
            if ("N1".equals(endTimeType) || approvedEndMin == 0) {
                approvedEndMin += 1440;
            }
            effectiveOutMin = Math.min(actualOutMin, approvedEndMin);
        } else {
            // 연장 미승인 또는 endTime 없는 경우 → 계획 퇴근시간으로 cap
            int plannedOffMin = toMinutes(planned.get("workOffHhmm").toString());
            if (actualOutMin > 1440 && plannedOffMin < toMinutes(planned.get("workOnHhmm").toString())) {
                plannedOffMin += 1440;
            }
            effectiveOutMin = Math.min(actualOutMin, plannedOffMin);
        }

        // 조퇴 승인 시 조퇴 시작시간까지만 인정
        Map<String, Object> earlyLeave = overtimes.stream()
                .filter(r -> "조퇴".equals(r.get("reqType")))
                .findFirst().orElse(null);
        if (earlyLeave != null && earlyLeave.get("startTime") != null) {
            int leaveStartMin = toMinutes(earlyLeave.get("startTime").toString());
            effectiveOutMin = Math.min(effectiveOutMin, leaveStartMin);
        }

        int minutes = effectiveOutMin - effectiveInMin;
        minutes -= breakOverlapMin(planned, effectiveInMin, effectiveOutMin);
        return Math.max(0, minutes);
    }

    /**
     * checkOut이 없을 때 계획 퇴근시간(승인된 연장/휴일근무 있으면 해당 종료시간)을 가상 퇴근으로 반환.
     * 반환값이 actualInMin 이하면 계산 불가 → 호출측에서 0 반환.
     */
    private int resolveVirtualCheckout(Map<String, Object> planned,
                                       List<Map<String, Object>> overtimes,
                                       int actualInMin) {
        if (planned == null || planned.get("workOffHhmm") == null) {
            // OFF/HOLIDAY: 승인된 휴일근무 종료시간 사용
            return overtimes.stream()
                    .filter(r -> "휴일근무".equals(r.get("reqType")) && r.get("endTime") != null)
                    .findFirst()
                    .map(r -> toMinutes(r.get("endTime").toString()))
                    .orElse(actualInMin);
        }
        int plannedOnMin   = toMinutes(planned.get("workOnHhmm").toString());
        int rawPlannedOff  = toMinutes(planned.get("workOffHhmm").toString());
        final int plannedOffMin = rawPlannedOff <= plannedOnMin ? rawPlannedOff + 1440 : rawPlannedOff;

        // 승인된 연장근무가 있으면 해당 종료시간까지 확장
        return overtimes.stream()
                .filter(r -> "연장".equals(r.get("reqType")) && r.get("endTime") != null)
                .findFirst()
                .map(r -> {
                    int approvedEnd = toMinutes(r.get("endTime").toString());
                    String ett = String.valueOf(r.getOrDefault("endTimeType", "N0"));
                    if ("N1".equals(ett) || approvedEnd == 0) approvedEnd += 1440;
                    return Math.max(plannedOffMin, approvedEnd);
                })
                .orElse(plannedOffMin);
    }

    private int breakOverlapMin(Map<String, Object> planned, int startMin, int endMin) {
        return overlapBreak(planned.get("break1StartHhmm"), planned.get("break1EndHhmm"), startMin, endMin)
                + overlapBreak(planned.get("break2StartHhmm"), planned.get("break2EndHhmm"), startMin, endMin);
    }

    private int overlapBreak(Object breakStartValue, Object breakEndValue, int startMin, int endMin) {
        if (breakStartValue == null || breakEndValue == null) return 0;
        String breakStartText = breakStartValue.toString();
        String breakEndText = breakEndValue.toString();
        if (breakStartText.isBlank() || breakEndText.isBlank()) return 0;
        int breakStart = toMinutes(breakStartText);
        int breakEnd = toNextDayMinute(toMinutes(breakEndText), breakStart);
        if (endMin > 1440 && breakStart < startMin) {
            breakStart += 1440;
            breakEnd += 1440;
        }
        return Math.max(0, Math.min(endMin, breakEnd) - Math.max(startMin, breakStart));
    }

    private int toNextDayMinute(int minute, int baseMinute) {
        return minute < baseMinute ? minute + 1440 : minute;
    }

    private int toActualOutMinute(AttendanceRecordDto dto, int actualInMin) {
        int actualOutMin = toMinutes(dto.getCheckOut());
        if ("Y".equals(dto.getOvernightYn())) {
            return actualOutMin + 1440;
        }
        if (actualOutMin < actualInMin) {
            throw new IllegalArgumentException("퇴근 시간이 출근 시간보다 빠르면 익일여부를 Y로 선택해야 합니다.");
        }
        return actualOutMin;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String toWeekBound(String yyyymmdd, boolean monday) {
        LocalDate date = LocalDate.parse(yyyymmdd, YMD);
        LocalDate bound = monday
                ? date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return bound.format(YMD);
    }

    @Override
    public void autoSetCheckoutIfLater(String company, String empCode, String yyyymmdd,
                                       String endTime, String endTimeType) {
        if (isBlank(endTime)) return;
        AttendanceRecordDto existing = recordMapper.findByDay(company, empCode, yyyymmdd);
        if (existing == null || isBlank(existing.getCheckIn())) return;

        int newCheckoutAbs = toMinutes(endTime);
        boolean newOvernight = "N1".equals(endTimeType);
        if (newOvernight) newCheckoutAbs += 1440;

        if (!isBlank(existing.getCheckOut())) {
            int curAbs = toMinutes(existing.getCheckOut());
            if ("Y".equals(existing.getOvernightYn())) curAbs += 1440;
            if (newCheckoutAbs <= curAbs) return;
        }

        existing.setCheckOut(endTime);
        existing.setOvernightYn(newOvernight ? "Y" : "N");
        int workMin = calculateWorkMin(existing);
        existing.setWorkMin(workMin);
        calculateLate(existing);
        recordMapper.upsert(existing);
    }

    @Override
    public void delete(String company, String empCode, String yyyymmdd) {
        recordMapper.delete(company, empCode, yyyymmdd);
    }

    @Override
    public void recalculateIfRecordExists(String company, String empCode, String yyyymmdd) {
        AttendanceRecordDto existing = recordMapper.findByDay(company, empCode, yyyymmdd);
        if (existing == null || isBlank(existing.getCheckIn())) {
            return;
        }
        int workMin = calculateWorkMin(existing);
        existing.setWorkMin(workMin);
        calculateLate(existing);
        recordMapper.upsert(existing);
    }

    private void calculateLate(AttendanceRecordDto dto) {
        if (dto.getCheckIn() == null) {
            dto.setLateYn("N");
            dto.setLateMin(0);
            return;
        }

        // OTHER 근무변경 승인이 반영된 실제 시프트 기준으로 지각 판단
        Map<String, Object> planned = recordMapper.findPlannedShift(
                dto.getCompany(), dto.getEmpCode(), dto.getYyyymmdd());

        if (planned == null || planned.get("workOnHhmm") == null) {
            dto.setLateYn("N");
            dto.setLateMin(0);
            return;
        }

        String workDayType = planned.get("workDayType") != null ? planned.get("workDayType").toString() : "";
        if ("OFF".equals(workDayType) || "HOLIDAY".equals(workDayType)) {
            dto.setLateYn("N");
            dto.setLateMin(0);
            return;
        }

        int scheduled = toMinutes(planned.get("workOnHhmm").toString());
        int actual = toMinutes(dto.getCheckIn());

        if (actual > scheduled) {
            dto.setLateYn("Y");
            dto.setLateMin(actual - scheduled);
        } else {
            dto.setLateYn("N");
            dto.setLateMin(0);
        }
    }

    private int toMinutes(String hhmm) {
        String[] parts = hhmm.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    private int calcShiftMinFromPlanned(Map<String, Object> planned) {
        if (planned == null) return 0;
        Object on  = planned.get("workOnHhmm");
        Object off = planned.get("workOffHhmm");
        if (on == null || off == null || on.toString().isBlank() || off.toString().isBlank()) return 0;
        try {
            int onMin  = toMinutes(on.toString());
            int offMin = toMinutes(off.toString());
            if (offMin <= onMin) offMin += 1440;
            int total = offMin - onMin;
            for (String p : new String[]{"break1", "break2"}) {
                Object bs = planned.get(p + "StartHhmm");
                Object be = planned.get(p + "EndHhmm");
                if (bs != null && be != null && !bs.toString().isBlank() && !be.toString().isBlank()) {
                    int bsMin = toMinutes(bs.toString());
                    int beMin = toMinutes(be.toString());
                    if (beMin <= bsMin) beMin += 1440;
                    total -= Math.max(0, beMin - bsMin);
                }
            }
            return Math.max(0, total);
        } catch (Exception e) {
            return 0;
        }
    }
}
