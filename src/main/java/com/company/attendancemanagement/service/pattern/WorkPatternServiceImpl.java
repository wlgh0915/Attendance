package com.company.attendancemanagement.service.pattern;

import com.company.attendancemanagement.dto.pattern.ShiftCodeDto;
import com.company.attendancemanagement.dto.pattern.WorkPatternDetailDto;
import com.company.attendancemanagement.dto.pattern.WorkPatternMasterDto;
import com.company.attendancemanagement.dto.pattern.WorkPatternSaveRequest;
import com.company.attendancemanagement.mapper.pattern.WorkPatternMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkPatternServiceImpl implements WorkPatternService {

    private final WorkPatternMapper workPatternMapper;

    @Override
    public List<WorkPatternMasterDto> getPatternList(String company) {
        return workPatternMapper.findPatternList(company);
    }

    @Override
    public WorkPatternSaveRequest getPatternDetail(String company, String workPatternCode) {
        WorkPatternSaveRequest request = new WorkPatternSaveRequest();
        request.setMaster(workPatternMapper.findPatternMaster(company, workPatternCode));
        request.setDetails(workPatternMapper.findPatternDetails(company, workPatternCode));
        return request;
    }

    @Override
    public List<ShiftCodeDto> getShiftCodes(String company) {
        return workPatternMapper.findShiftCodes(company);
    }

    @Override
    @Transactional
    public void savePattern(WorkPatternSaveRequest request) {
        validateRequest(request);

        String company = request.getMaster().getCompany();
        String patternCode = request.getMaster().getWorkPatternCode();

        List<ShiftCodeDto> shiftCodes = workPatternMapper.findShiftCodes(company);
        Map<String, ShiftCodeDto> shiftMap = shiftCodes.stream()
                .collect(Collectors.toMap(ShiftCodeDto::getShiftCode, Function.identity()));

        validateBusinessRules(request.getMaster(), request.getDetails(), shiftMap);

        if (workPatternMapper.existsPatternCode(company, patternCode) > 0) {
            workPatternMapper.updatePatternMaster(request.getMaster());
            workPatternMapper.deletePatternDetails(company, patternCode);
        } else {
            workPatternMapper.insertPatternMaster(request.getMaster());
        }

        for (WorkPatternDetailDto detail : request.getDetails()) {
            ShiftCodeDto shift = shiftMap.get(detail.getShiftCode());

            detail.setCompany(company);
            detail.setWorkPatternCode(patternCode);

            if (shift != null) {
                detail.setWorkTypeCode(shift.getWorkTypeCode());
                detail.setWorkDayType(shift.getWorkDayType());
            }

            workPatternMapper.insertPatternDetail(detail);
        }
    }

    private void validateRequest(WorkPatternSaveRequest request) {
        if (request == null || request.getMaster() == null) {
            throw new IllegalArgumentException("패턴 기본정보가 없습니다.");
        }
        if (request.getMaster().getWorkPatternCode() == null || request.getMaster().getWorkPatternCode().isBlank()) {
            throw new IllegalArgumentException("패턴코드는 필수입니다.");
        }
        if (request.getMaster().getWorkPatternName() == null || request.getMaster().getWorkPatternName().isBlank()) {
            throw new IllegalArgumentException("패턴명은 필수입니다.");
        }
        if (request.getMaster().getStartDate() == null) {
            throw new IllegalArgumentException("시작일은 필수입니다.");
        }
        if (request.getDetails() == null || request.getDetails().isEmpty()) {
            throw new IllegalArgumentException("패턴 상세 정보가 없습니다.");
        }
    }

    private void validateBusinessRules(WorkPatternMasterDto master,
                                       List<WorkPatternDetailDto> details,
                                       Map<String, ShiftCodeDto> shiftMap) {

        LocalDate startDate = master.getStartDate();
        ShiftCodeDto prevShift = null;
        int weekMinutes = 0;
        int weekDays = 0;

        for (int i = 0; i < details.size(); i++) {
            WorkPatternDetailDto detail = details.get(i);
            LocalDate targetDate = startDate.plusDays(i);

            ShiftCodeDto shift = shiftMap.get(detail.getShiftCode());
            if (shift == null) {
                throw new IllegalArgumentException((i + 1) + "일차 근태코드를 선택하세요.");
            }

            boolean saturday = targetDate.getDayOfWeek() == DayOfWeek.SATURDAY;
            boolean sunday = targetDate.getDayOfWeek() == DayOfWeek.SUNDAY;

            boolean isNight = isNightShift(shift);
            boolean isDay = isDayShift(shift);
            boolean isOff = isOffShift(shift);
            boolean isHoliday = isHolidayShift(shift);

            if (prevShift != null && isNightShift(prevShift) && isDay) {
                throw new IllegalArgumentException(targetDate + " : 익일근무 다음날에는 주간근무를 넣을 수 없습니다.");
            }

            if (saturday && !isOff && !isNight) {
                throw new IllegalArgumentException(targetDate + " : 토요일은 휴무 또는 익일근무만 가능합니다.");
            }

            if (sunday && !isHoliday && !isNight) {
                throw new IllegalArgumentException(targetDate + " : 일요일은 휴일 또는 익일근무만 가능합니다.");
            }

            if (isDay && (saturday || sunday)) {
                throw new IllegalArgumentException(targetDate + " : 주간 근무조는 평일만 가능합니다.");
            }

            int workMinutes = shift.getWorkMinutes() == null ? 0 : shift.getWorkMinutes();

            if (workMinutes > 480) {
                throw new IllegalArgumentException(targetDate + " : 하루 근무시간은 8시간을 초과할 수 없습니다.");
            }

            if (!isOff && !isHoliday) {
                weekDays++;
                weekMinutes += workMinutes;
            }

            if (targetDate.getDayOfWeek() == DayOfWeek.SUNDAY || i == details.size() - 1) {
                if (weekDays > 6) {
                    throw new IllegalArgumentException(targetDate + " 주차 : 주 최대 6일 근무만 가능합니다.");
                }
                if (weekMinutes > 3120) {
                    throw new IllegalArgumentException(targetDate + " 주차 : 주 52시간을 초과할 수 없습니다.");
                }
                weekDays = 0;
                weekMinutes = 0;
            }

            prevShift = shift;
        }
    }

    private boolean isNightShift(ShiftCodeDto shift) {
        String shiftName = shift.getShiftName() == null ? "" : shift.getShiftName();
        String workTypeCode = shift.getWorkTypeCode() == null ? "" : shift.getWorkTypeCode();
        return shiftName.contains("익일") || shiftName.contains("야간") || "NIGHT".equalsIgnoreCase(workTypeCode);
    }

    private boolean isDayShift(ShiftCodeDto shift) {
        String shiftName = shift.getShiftName() == null ? "" : shift.getShiftName();
        String workTypeCode = shift.getWorkTypeCode() == null ? "" : shift.getWorkTypeCode();
        return shiftName.contains("주간") || "DAY".equalsIgnoreCase(workTypeCode);
    }

    private boolean isOffShift(ShiftCodeDto shift) {
        String shiftName = shift.getShiftName() == null ? "" : shift.getShiftName();
        return shiftName.contains("휴무");
    }

    private boolean isHolidayShift(ShiftCodeDto shift) {
        String shiftName = shift.getShiftName() == null ? "" : shift.getShiftName();
        return shiftName.contains("휴일");
    }
}