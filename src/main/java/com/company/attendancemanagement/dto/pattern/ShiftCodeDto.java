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

    private String useYn;

    private Integer workMinutes;
}