package com.company.attendancemanagement.dto.pattern;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class PatternCalendarDto {

    private int year;
    private int month;
    private List<LocalDate> dates;
    private List<String> dayHeaders;    // "1(월)", "2(화)", ...
    private List<String> dayCssClasses; // "", "day-sat", "day-sun", ...
    private List<PatternCalendarRowDto> rows;
    private List<ShiftCodeDto> shiftCodes;
    private Map<String, String> shiftNameMap;  // shiftCode → shiftName (JS용)
    private Map<String, String> shiftTimeMap;  // shiftCode → "09:00~18:00" (JS용)
}