package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.calendar.AttendanceDayDto;
import com.company.attendancemanagement.dto.calendar.AttendanceReqSummaryDto;
import com.company.attendancemanagement.dto.calendar.EmpSimpleDto;
import com.company.attendancemanagement.dto.calendar.WeeklyEmpDto;
import com.company.attendancemanagement.dto.department.DepartmentDto;
import com.company.attendancemanagement.dto.record.DailyAttendanceDto;
import com.company.attendancemanagement.mapper.AttendanceCalendarMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WeeklyAttendanceServiceImpl implements WeeklyAttendanceService {

    private final AttendanceCalendarMapper calendarMapper;
    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;

    @Override
    public List<WeeklyEmpDto> getWeeklyByDept(String company, String deptCode, LocalDate weekStart) {
        LocalDate weekEnd   = weekStart.plusDays(6);
        String    startDate = weekStart.toString();
        String    endDate   = weekEnd.toString();
        String    startYmd  = weekStart.format(YMD);
        String    endYmd    = weekEnd.format(YMD);

        List<EmpSimpleDto> emps   = calendarMapper.findEmpsByDept(company, deptCode);
        List<WeeklyEmpDto> result = new ArrayList<>();

        for (EmpSimpleDto emp : emps) {
            List<AttendanceDayDto>        days    = calendarMapper.findDailyShifts(company, emp.getEmpCode(), startDate, endDate);
            List<AttendanceReqSummaryDto> requests = calendarMapper.findRequests(company, emp.getEmpCode(), startDate, endDate);
            List<DailyAttendanceDto>      records  = calendarMapper.findRecords(company, emp.getEmpCode(), startYmd, endYmd);

            Map<String, List<AttendanceReqSummaryDto>> reqByDate = requests.stream()
                    .collect(Collectors.groupingBy(AttendanceReqSummaryDto::getWorkDate));

            Map<String, DailyAttendanceDto> recordByDate = records.stream()
                    .collect(Collectors.toMap(r -> {
                        String y = r.getYyyymmdd();
                        return y.substring(0, 4) + "-" + y.substring(4, 6) + "-" + y.substring(6, 8);
                    }, r -> r));

            days.forEach(day -> {
                day.setRequests(reqByDate.getOrDefault(day.getWorkDate(), List.of()));
                day.setRecord(recordByDate.get(day.getWorkDate()));
            });

            WeeklyEmpDto dto = new WeeklyEmpDto();
            dto.setEmpCode(emp.getEmpCode());
            dto.setEmpName(emp.getEmpName());
            dto.setDeptName(emp.getDeptName());
            dto.setDays(days);
            result.add(dto);
        }

        return result;
    }

    @Override
    public List<WeeklyEmpDto> getDeptMonthly(String company, String deptCode, YearMonth ym) {
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd   = ym.atEndOfMonth();
        String    startDate  = monthStart.toString();
        String    endDate    = monthEnd.toString();
        String    startYmd   = monthStart.format(YMD);
        String    endYmd     = monthEnd.format(YMD);

        List<EmpSimpleDto> emps   = calendarMapper.findEmpsByDept(company, deptCode);
        List<WeeklyEmpDto> result = new ArrayList<>();

        for (EmpSimpleDto emp : emps) {
            List<AttendanceDayDto>        days     = calendarMapper.findDailyShifts(company, emp.getEmpCode(), startDate, endDate);
            List<AttendanceReqSummaryDto> requests = calendarMapper.findRequests(company, emp.getEmpCode(), startDate, endDate);
            List<DailyAttendanceDto>      records  = calendarMapper.findRecords(company, emp.getEmpCode(), startYmd, endYmd);

            Map<String, List<AttendanceReqSummaryDto>> reqByDate = requests.stream()
                    .collect(Collectors.groupingBy(AttendanceReqSummaryDto::getWorkDate));

            Map<String, DailyAttendanceDto> recordByDate = records.stream()
                    .collect(Collectors.toMap(r -> {
                        String y = r.getYyyymmdd();
                        return y.substring(0, 4) + "-" + y.substring(4, 6) + "-" + y.substring(6, 8);
                    }, r -> r));

            days.forEach(day -> {
                day.setRequests(reqByDate.getOrDefault(day.getWorkDate(), List.of()));
                day.setRecord(recordByDate.get(day.getWorkDate()));
            });

            WeeklyEmpDto dto = new WeeklyEmpDto();
            dto.setEmpCode(emp.getEmpCode());
            dto.setEmpName(emp.getEmpName());
            dto.setDeptName(emp.getDeptName());
            dto.setDays(days);
            result.add(dto);
        }

        return result;
    }

    @Override
    public List<DepartmentDto> getAccessibleDepts(String company, String deptCode) {
        return calendarMapper.findAccessibleDepts(company, deptCode);
    }
}
