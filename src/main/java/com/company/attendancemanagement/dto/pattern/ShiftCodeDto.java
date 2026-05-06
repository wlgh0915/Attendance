package com.company.attendancemanagement.dto.pattern;

import lombok.Data;

@Data
public class ShiftCodeDto {

    private String company;

    private String shiftCode;
    private String shiftName;

    private String workTypeCode;
    private String workDayType;

    private String workOnDayType;
    private String workOffDayType;

    private String workOnHhmm;
    private String workOffHhmm;
    private String break1StartHhmm;
    private String break1EndHhmm;
    private String break2StartHhmm;
    private String break2EndHhmm;

    private String useYn;

    private Integer workMinutes;
}
