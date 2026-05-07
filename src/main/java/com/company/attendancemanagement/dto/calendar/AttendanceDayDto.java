package com.company.attendancemanagement.dto.calendar;

import com.company.attendancemanagement.dto.record.DailyAttendanceDto;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class AttendanceDayDto {
    private String workDate;       // yyyy-MM-dd
    private int    dayOfWeekNum;   // 1=일, 2=월, ..., 7=토 (MSSQL DATEPART)
    private String shiftCode;
    private String shiftName;
    private String  workOnHhmm;
    private String  workOffHhmm;
    private Integer shiftWorkMin;  // 휴게 제외 계획 근무분
    private String  workDayType;   // WORK / OFF / HOLIDAY
    private boolean inCurrentMonth;
    private List<AttendanceReqSummaryDto> requests = new ArrayList<>();
    private DailyAttendanceDto record;             // 출퇴근 실적 (없으면 null)
}
