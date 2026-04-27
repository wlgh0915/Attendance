package com.company.attendancemanagement.dto.pattern;

import lombok.Data;
import java.util.List;

@Data
public class PatternCalendarRowDto {

    private String workPatternCode;
    private String workPatternName;

    // 인덱스 i = (day - 1), null이면 해당 날짜에 근태코드 없음
    private List<String> shiftCodes;
    private List<String> shiftNames;
}