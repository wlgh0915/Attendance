package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.calendar.AttendanceDayDto;
import com.company.attendancemanagement.dto.calendar.AttendanceReqSummaryDto;
import com.company.attendancemanagement.dto.calendar.EmpSimpleDto;
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

        List<AttendanceDayDto> days     = calendarMapper.findDailyShifts(company, empCode, startDate, endDate);
        List<AttendanceReqSummaryDto> requests = calendarMapper.findRequests(company, empCode, startDate, endDate);

        Map<String, List<AttendanceReqSummaryDto>> reqByDate = requests.stream()
                .collect(Collectors.groupingBy(AttendanceReqSummaryDto::getWorkDate));

        days.forEach(day -> day.setRequests(reqByDate.getOrDefault(day.getWorkDate(), List.of())));
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
}