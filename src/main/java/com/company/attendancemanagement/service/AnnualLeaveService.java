package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.annual.AnnualLeaveUsageCandidateDto;
import com.company.attendancemanagement.dto.request.AttendanceRequestDto;
import com.company.attendancemanagement.mapper.AnnualLeaveMapper;
import com.company.attendancemanagement.mapper.AttendanceRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AnnualLeaveService {

    private static final BigDecimal HALF_DAY = new BigDecimal("0.5");
    private static final BigDecimal ONE_DAY = BigDecimal.ONE;
    private static final int SCALE = 5;

    private final AnnualLeaveMapper annualLeaveMapper;
    private final AttendanceRequestMapper requestMapper;

    public BigDecimal availableDay(String company, String empCode, int yyyy) {
        BigDecimal totalDay = totalDay(company, empCode, yyyy);
        BigDecimal reservedUseDay = sumUsage(company, empCode, yyyy, true, null);
        return totalDay.subtract(reservedUseDay).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public void validateAvailableForSubmit(AttendanceRequestDto request) {
        Map<Integer, BigDecimal> requestUseByYear = usageByYear(request);
        if (requestUseByYear.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, BigDecimal> entry : requestUseByYear.entrySet()) {
            int yyyy = entry.getKey();
            annualLeaveMapper.ensureAnnualDetail(request.getCompany(), yyyy, request.getEmpCode());
            BigDecimal totalDay = totalDay(request.getCompany(), request.getEmpCode(), yyyy);
            BigDecimal reservedUseDay = sumUsage(
                    request.getCompany(), request.getEmpCode(), yyyy, true, request.getRequestId());
            BigDecimal afterUseDay = reservedUseDay.add(entry.getValue());
            if (afterUseDay.compareTo(totalDay) > 0) {
                BigDecimal over = afterUseDay.subtract(totalDay);
                throw new IllegalArgumentException("잔여 연차가 부족하여 상신할 수 없습니다. 부족 일수: "
                        + formatDay(over));
            }
        }
    }

    public void validateAvailableForApproval(AttendanceRequestDto request) {
        Set<Integer> years = affectedYears(request);
        if (years.isEmpty()) {
            return;
        }
        for (int yyyy : years) {
            annualLeaveMapper.ensureAnnualDetail(request.getCompany(), yyyy, request.getEmpCode());
            BigDecimal totalDay = totalDay(request.getCompany(), request.getEmpCode(), yyyy);
            BigDecimal reservedUseDay = sumUsage(request.getCompany(), request.getEmpCode(), yyyy, true, null);
            if (reservedUseDay.compareTo(totalDay) > 0) {
                BigDecimal over = reservedUseDay.subtract(totalDay);
                throw new IllegalArgumentException("잔여 연차가 부족하여 승인할 수 없습니다. 부족 일수: "
                        + formatDay(over));
            }
        }
    }

    public void refreshApprovedUsage(AttendanceRequestDto request) {
        Set<Integer> years = affectedYears(request);
        for (int yyyy : years) {
            refreshApprovedUsage(request.getCompany(), request.getEmpCode(), yyyy);
        }
    }

    public void refreshApprovedUsage(String company, String empCode, int yyyy) {
        annualLeaveMapper.ensureAnnualDetail(company, yyyy, empCode);
        BigDecimal approvedUseDay = sumUsage(company, empCode, yyyy, false, null);
        annualLeaveMapper.updateUsage(company, yyyy, empCode, approvedUseDay);
    }

    public boolean isAnnualLeaveRequest(AttendanceRequestDto request) {
        return !usageByYear(request).isEmpty();
    }

    private BigDecimal totalDay(String company, String empCode, int yyyy) {
        BigDecimal totalDay = annualLeaveMapper.findTotalDay(company, yyyy, empCode);
        return totalDay == null ? new BigDecimal("20") : totalDay;
    }

    private BigDecimal sumUsage(String company, String empCode, int yyyy,
                                boolean includeSubmitted, String excludeRequestId) {
        List<AnnualLeaveUsageCandidateDto> candidates = annualLeaveMapper.findUsageCandidates(
                company, empCode, yyyy, includeSubmitted, excludeRequestId);
        BigDecimal total = BigDecimal.ZERO;
        for (AnnualLeaveUsageCandidateDto candidate : candidates) {
            total = total.add(usageForYear(company, empCode, yyyy, candidate));
        }
        return total.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private Map<Integer, BigDecimal> usageByYear(AttendanceRequestDto request) {
        AnnualLeaveUsageCandidateDto candidate = new AnnualLeaveUsageCandidateDto();
        candidate.setRequestId(request.getRequestId());
        candidate.setRequestCategory(request.getRequestCategory());
        candidate.setRequestWorkCode(request.getRequestWorkCode());
        candidate.setWorkDate(request.getWorkDate());
        candidate.setEndDate(request.getEndDate());
        candidate.setRequestWorkMin(request.getRequestWorkMin());

        java.util.LinkedHashMap<Integer, BigDecimal> usage = new java.util.LinkedHashMap<>();
        for (int year : affectedYears(candidate)) {
            BigDecimal day = usageForYear(request.getCompany(), request.getEmpCode(), year, candidate);
            if (day.compareTo(BigDecimal.ZERO) > 0) {
                usage.merge(year, day, BigDecimal::add);
            }
        }
        return usage;
    }

    private BigDecimal usageForYear(String company, String empCode, int yyyy,
                                    AnnualLeaveUsageCandidateDto candidate) {
        String category = candidate.getRequestCategory();
        String workCode = candidate.getRequestWorkCode();
        if ("OTHER".equals(category)) {
            if (!"06".equals(workCode)) {
                return BigDecimal.ZERO;
            }
            return BigDecimal.valueOf(overlapDays(candidate.getWorkDate(), candidate.getEndDate(), yyyy))
                    .setScale(SCALE, RoundingMode.HALF_UP);
        }
        if (!"GENERAL".equals(category) && category != null && !category.isBlank()
                && !"LEAVE".equals(category)) {
            return BigDecimal.ZERO;
        }
        if (isHalfDay(workCode)) {
            return HALF_DAY;
        }
        if ("조퇴".equals(workCode) || "외출".equals(workCode)) {
            return timeRatioDay(company, empCode, candidate.getWorkDate(), candidate.getRequestWorkMin());
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal timeRatioDay(String company, String empCode, String workDate, Integer requestWorkMin) {
        if (requestWorkMin == null || requestWorkMin <= 0) {
            return BigDecimal.ZERO;
        }
        int plannedMin = plannedWorkMin(company, empCode, workDate);
        if (plannedMin <= 0) {
            throw new IllegalArgumentException("계획 근무시간을 확인할 수 없어 연차 차감일을 계산할 수 없습니다.");
        }
        return BigDecimal.valueOf(requestWorkMin)
                .divide(BigDecimal.valueOf(plannedMin), SCALE, RoundingMode.HALF_UP);
    }

    private int plannedWorkMin(String company, String empCode, String workDate) {
        Map<String, Object> shiftInfo = requestMapper.findPlannedShiftInfo(company, empCode, workDate);
        if (shiftInfo == null) {
            return 0;
        }
        String workOn = stringValue(shiftInfo, "workOnHhmm", "WORKONHHMM");
        String workOff = stringValue(shiftInfo, "workOffHhmm", "WORKOFFHHMM");
        if (workOn == null || workOff == null) {
            return 0;
        }
        int start = toMinute(workOn);
        int end = toMinute(workOff);
        if (end <= start) {
            end += 1440;
        }
        return Math.max(0, end - start - breakOverlapMin(shiftInfo, start, end));
    }

    private int breakOverlapMin(Map<String, Object> shiftInfo, int startMin, int endMin) {
        return overlapBreak(shiftInfo.get("break1StartHhmm"), shiftInfo.get("break1EndHhmm"), startMin, endMin)
                + overlapBreak(shiftInfo.get("break2StartHhmm"), shiftInfo.get("break2EndHhmm"), startMin, endMin);
    }

    private int overlapBreak(Object breakStartValue, Object breakEndValue, int startMin, int endMin) {
        if (breakStartValue == null || breakEndValue == null) return 0;
        String breakStartText = breakStartValue.toString();
        String breakEndText = breakEndValue.toString();
        if (breakStartText.isBlank() || breakEndText.isBlank()) return 0;
        int breakStart = toMinute(breakStartText);
        int breakEnd = breakStartText.compareTo(breakEndText) > 0
                ? 1440 + toMinute(breakEndText)
                : toMinute(breakEndText);
        if (endMin > 1440 && breakStart < startMin) {
            breakStart += 1440;
            breakEnd += 1440;
        }
        return Math.max(0, Math.min(endMin, breakEnd) - Math.max(startMin, breakStart));
    }

    private Set<Integer> affectedYears(AttendanceRequestDto request) {
        AnnualLeaveUsageCandidateDto candidate = new AnnualLeaveUsageCandidateDto();
        candidate.setRequestCategory(request.getRequestCategory());
        candidate.setRequestWorkCode(request.getRequestWorkCode());
        candidate.setWorkDate(request.getWorkDate());
        candidate.setEndDate(request.getEndDate());
        return affectedYears(candidate);
    }

    private Set<Integer> affectedYears(AnnualLeaveUsageCandidateDto candidate) {
        Set<Integer> years = new HashSet<>();
        if ("OTHER".equals(candidate.getRequestCategory()) && "06".equals(candidate.getRequestWorkCode())) {
            LocalDate start = LocalDate.parse(candidate.getWorkDate());
            LocalDate end = candidate.getEndDate() == null || candidate.getEndDate().isBlank()
                    ? start
                    : LocalDate.parse(candidate.getEndDate());
            for (int year = start.getYear(); year <= end.getYear(); year++) {
                years.add(year);
            }
            return years;
        }
        if (isAnnualGeneralWorkCode(candidate.getRequestWorkCode()) && candidate.getWorkDate() != null) {
            years.add(LocalDate.parse(candidate.getWorkDate()).getYear());
        }
        return years;
    }

    private int overlapDays(String startDate, String endDate, int yyyy) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = endDate == null || endDate.isBlank() ? start : LocalDate.parse(endDate);
        LocalDate yearStart = LocalDate.of(yyyy, 1, 1);
        LocalDate yearEnd = LocalDate.of(yyyy, 12, 31);
        LocalDate overlapStart = start.isAfter(yearStart) ? start : yearStart;
        LocalDate overlapEnd = end.isBefore(yearEnd) ? end : yearEnd;
        if (overlapEnd.isBefore(overlapStart)) {
            return 0;
        }
        return (int) (overlapEnd.toEpochDay() - overlapStart.toEpochDay() + 1);
    }

    private boolean isAnnualGeneralWorkCode(String workCode) {
        return isHalfDay(workCode) || "조퇴".equals(workCode) || "외출".equals(workCode);
    }

    private boolean isHalfDay(String workCode) {
        return "오전반차".equals(workCode)
                || "오후반차".equals(workCode)
                || "전반차".equals(workCode)
                || "후반차".equals(workCode);
    }

    private int toMinute(String hhmm) {
        String[] parts = hhmm.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    private String stringValue(Map<String, Object> map, String camelKey, String upperKey) {
        Object value = map.get(camelKey);
        if (value == null) value = map.get(upperKey);
        if (value == null) return null;
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private String formatDay(BigDecimal day) {
        return day.setScale(SCALE, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "일";
    }
}
