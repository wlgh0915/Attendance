package com.company.attendancemanagement.dto.record;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DailyAttendanceDto {
    private String  yyyymmdd;
    private String  shiftCode;
    private String  actualShiftCode;
    private String  checkIn;       // HH:mm
    private String  checkOut;      // HH:mm
    private Integer workMin;
    private String  overnightYn;
    private String  lateYn;
    private Integer lateMin;
}
