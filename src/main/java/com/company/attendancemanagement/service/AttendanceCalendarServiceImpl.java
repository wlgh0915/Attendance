package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.calendar.AttendanceDayDto;
import com.company.attendancemanagement.dto.calendar.AttendanceReqSummaryDto;
import com.company.attendancemanagement.dto.calendar.EmpSimpleDto;
import com.company.attendancemanagement.dto.department.DepartmentDto;
import com.company.attendancemanagement.dto.record.DailyAttendanceDto;
import com.company.attendancemanagement.mapper.AttendanceCalendarMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceCalendarServiceImpl implements AttendanceCalendarService {

    private final AttendanceCalendarMapper calendarMapper;

    @Override
    public List<AttendanceDayDto> getCalendar(String company, String empCode, YearMonth ym) {
        String startDate = ym.atDay(1).toString();
        String endDate   = ym.atEndOfMonth().toString();
        String startYmd  = startDate.replace("-", "");
        String endYmd    = endDate.replace("-", "");

        List<AttendanceDayDto>        days     = calendarMapper.findDailyShifts(company, empCode, startDate, endDate);
        List<AttendanceReqSummaryDto> requests = calendarMapper.findRequests(company, empCode, startDate, endDate);
        List<DailyAttendanceDto>      records  = calendarMapper.findRecords(company, empCode, startYmd, endYmd);

        Map<String, List<AttendanceReqSummaryDto>> reqByDate = requests.stream()
                .collect(Collectors.groupingBy(AttendanceReqSummaryDto::getWorkDate));

        // "20260428" → "2026-04-28" 변환 후 날짜 키로 사용
        Map<String, DailyAttendanceDto> recordByDate = records.stream()
                .collect(Collectors.toMap(r -> {
                    String y = r.getYyyymmdd();
                    return y.substring(0, 4) + "-" + y.substring(4, 6) + "-" + y.substring(6, 8);
                }, r -> r));

        days.forEach(day -> {
            day.setRequests(reqByDate.getOrDefault(day.getWorkDate(), List.of()));
            day.setRecord(recordByDate.get(day.getWorkDate()));
        });
        return days;
    }

    @Override
    public Map<String, String> getEmpInfo(String company, String empCode) {
        return calendarMapper.findEmpInfo(company, empCode);
    }

    @Override
    public List<EmpSimpleDto> getEmpsByDept(String company, String deptCode) {
        return calendarMapper.findEmpsByDept(company, deptCode);
    }

    @Override
    public List<DepartmentDto> getAccessibleDepts(String company, String deptCode) {
        return calendarMapper.findAccessibleDepts(company, deptCode);
    }
}