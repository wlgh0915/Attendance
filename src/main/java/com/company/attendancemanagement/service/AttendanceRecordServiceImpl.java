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
    private static final String REQ_OVERTIME = "\uC5F0\uC7A5";
    private static final String REQ_HOLIDAY_WORK = "\uD734\uC77C\uADFC\uBB34";
    private static final String REQ_EARLY_LEAVE = "\uC870\uD1F4";

    private final AttendanceRecordMapper recordMapper;

    @Override
    public List<AttendanceRecordDto> findByMonth(String company, String empCode,
                                                  String startYmd, String endYmd) {
        return recordMapper.findByMonth(company, empCode, startYmd, endYmd);
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

        applyAutoCheckOut(dto);
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

        if (isBlank(checkIn) || isBlank(checkOut)) {
            return dto.getWorkMin() != null ? dto.getWorkMin() : 0;
        }

        int actualInMin = toMinutes(checkIn);
        int actualOutMin = toActualOutMinute(dto, actualInMin);

        List<Map<String, Object>> overtimes = recordMapper.findApprovedOvertimeRequests(
                dto.getCompany(), dto.getEmpCode(), dto.getYyyymmdd());

        Map<String, Object> planned = recordMapper.findPlannedShift(
                dto.getCompany(), dto.getEmpCode(), dto.getYyyymmdd());

        if (planned == null
                || planned.get("workOnHhmm") == null
                || planned.get("workOffHhmm") == null) {
            // OFF/HOLIDAY 날: 휴일근무 승인 범위 내로 cap
            Map<String, Object> holidayWork = overtimes.stream()
                    .filter(r -> REQ_HOLIDAY_WORK.equals(r.get("reqType")))
                    .findFirst().orElse(null);
            if (holidayWork != null
                    && holidayWork.get("startTime") != null
                    && holidayWork.get("endTime") != null) {
                int approvedStart = requestMinuteByTypeOrNull(holidayWork, "startTime", "startTimeType");
                int approvedEnd   = requestMinuteOrNull(holidayWork, "endTime", "endTimeType", approvedStart);
                int effIn  = Math.max(actualInMin, approvedStart);
                int effOut = Math.min(actualOutMin, approvedEnd);
                return Math.max(0, effOut - effIn);
            }
            return Math.max(0, actualOutMin - actualInMin);
        }

        // 출근 유효 시작 시간: 조출연장 승인 시 승인 시작 시간, 미승인 시 계획 출근시간으로 cap
        Map<String, Object> earlyApproval = overtimes.stream()
                .filter(r -> "\uC870\uCD9C\uC5F0\uC7A5".equals(r.get("reqType")))
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
                .filter(r -> REQ_OVERTIME.equals(r.get("reqType")))
                .findFirst().orElse(null);
        int effectiveOutMin = actualOutMin;
        if (overtimeApproval != null && overtimeApproval.get("endTime") != null) {
            int plannedOnMin = toMinutes(planned.get("workOnHhmm").toString());
            int approvedEndMin = requestMinuteOrNull(overtimeApproval, "endTime", "endTimeType", plannedOnMin);
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
                .filter(r -> REQ_EARLY_LEAVE.equals(r.get("reqType")))
                .findFirst().orElse(null);
        if (earlyLeave != null && earlyLeave.get("startTime") != null) {
            int plannedOnMin = toMinutes(planned.get("workOnHhmm").toString());
            int leaveStartMin = requestMinuteOrNull(earlyLeave, "startTime", "startTimeType", plannedOnMin);
            effectiveOutMin = Math.min(effectiveOutMin, leaveStartMin);
        }

        int minutes = effectiveOutMin - effectiveInMin;
        minutes -= breakOverlapMin(planned, effectiveInMin, effectiveOutMin);
        return Math.max(0, minutes);
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

    private void applyAutoCheckOut(AttendanceRecordDto dto) {
        if (isBlank(dto.getCheckIn())) {
            return;
        }
        Integer checkOutMin = findAutoCheckOutMinute(dto);
        if (checkOutMin == null) {
            return;
        }
        dto.setCheckOut(toHhmm(checkOutMin));
        dto.setOvernightYn(checkOutMin >= 1440 ? "Y" : "N");
    }

    private Integer findAutoCheckOutMinute(AttendanceRecordDto dto) {
        List<Map<String, Object>> requests = recordMapper.findApprovedOvertimeRequests(
                dto.getCompany(), dto.getEmpCode(), dto.getYyyymmdd());
        Map<String, Object> planned = recordMapper.findPlannedShift(
                dto.getCompany(), dto.getEmpCode(), dto.getYyyymmdd());
        Map<String, Object> holidayWork = findRequest(requests, REQ_HOLIDAY_WORK);

        if (planned == null
                || planned.get("workOnHhmm") == null
                || planned.get("workOffHhmm") == null) {
            return approvedEndMinute(holidayWork, toMinutes(dto.getCheckIn()));
        }

        String workDayType = planned.get("workDayType") != null ? planned.get("workDayType").toString() : "";
        if (holidayWork != null && ("OFF".equals(workDayType) || "HOLIDAY".equals(workDayType))) {
            return approvedEndMinute(holidayWork, toMinutes(dto.getCheckIn()));
        }

        int plannedOnMin = toMinutes(planned.get("workOnHhmm").toString());
        int plannedOffMin = toNextDayMinute(toMinutes(planned.get("workOffHhmm").toString()), plannedOnMin);
        int autoOutMin = plannedOffMin;

        Map<String, Object> overtime = findRequest(requests, REQ_OVERTIME);
        Integer overtimeEnd = requestMinuteOrNull(overtime, "endTime", "endTimeType", plannedOnMin);
        if (overtimeEnd != null) {
            autoOutMin = overtimeEnd;
        }

        Map<String, Object> earlyLeave = findRequest(requests, REQ_EARLY_LEAVE);
        Integer earlyLeaveStart = requestMinuteOrNull(earlyLeave, "startTime", "startTimeType", plannedOnMin);
        if (earlyLeaveStart != null) {
            autoOutMin = Math.min(autoOutMin, earlyLeaveStart);
        }

        return autoOutMin;
    }

    private Integer approvedEndMinute(Map<String, Object> request, int fallbackBaseMinute) {
        if (request == null) {
            return null;
        }
        Integer startMinute = requestMinuteByTypeOrNull(request, "startTime", "startTimeType");
        int baseMinute = startMinute != null ? startMinute : fallbackBaseMinute;
        return requestMinuteOrNull(request, "endTime", "endTimeType", baseMinute);
    }

    private Map<String, Object> findRequest(List<Map<String, Object>> requests, String reqType) {
        return requests.stream()
                .filter(r -> reqType.equals(r.get("reqType")))
                .findFirst()
                .orElse(null);
    }

    private Integer requestMinuteOrNull(Map<String, Object> request, String timeKey, String typeKey, int baseMinute) {
        if (request == null || request.get(timeKey) == null) {
            return null;
        }
        int minute = toMinutes(request.get(timeKey).toString());
        Object typeValue = request.get(typeKey);
        if ("N1".equals(typeValue != null ? typeValue.toString() : null) || minute < baseMinute) {
            minute += 1440;
        }
        return minute;
    }

    private Integer requestMinuteByTypeOrNull(Map<String, Object> request, String timeKey, String typeKey) {
        if (request == null || request.get(timeKey) == null) {
            return null;
        }
        int minute = toMinutes(request.get(timeKey).toString());
        Object typeValue = request.get(typeKey);
        if ("N1".equals(typeValue != null ? typeValue.toString() : null)) {
            minute += 1440;
        }
        return minute;
    }

    private String toHhmm(int minute) {
        int normalized = ((minute % 1440) + 1440) % 1440;
        return String.format("%02d:%02d", normalized / 60, normalized % 60);
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
    public void delete(String company, String empCode, String yyyymmdd) {
        recordMapper.delete(company, empCode, yyyymmdd);
    }

    @Override
    public void recalculateIfRecordExists(String company, String empCode, String yyyymmdd) {
        AttendanceRecordDto existing = recordMapper.findByDay(company, empCode, yyyymmdd);
        if (existing == null || isBlank(existing.getCheckIn())) {
            return;
        }
        applyAutoCheckOut(existing);
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
}
