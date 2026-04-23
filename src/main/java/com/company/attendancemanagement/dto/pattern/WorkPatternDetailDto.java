package com.company.attendancemanagement.dto.pattern;

import lombok.Data;

@Data
public class WorkPatternDetailDto {

    private String company;
    private String workPatternCode;

    // 순번 (1일차, 2일차...)
    private Integer seq;

    private String dayType;

    // 근태코드 (핵심)
    private String shiftCode;

    private String workTypeCode;
    private String workDayType;

    // ===== 화면용 =====
    private String shiftName;
    private Integer workMinutes;
    private String weekdayName;
    private String warningMessage;
}