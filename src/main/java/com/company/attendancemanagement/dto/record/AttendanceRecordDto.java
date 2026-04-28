package com.company.attendancemanagement.dto.record;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttendanceRecordDto {
    // DB 컬럼
    private String  company;
    private String  empCode;
    private String  yyyymmdd;
    private String  deptCode;
    private String  shiftCode;
    private String  actualShiftCode;
    private String  checkIn;        // HH:mm
    private String  checkOut;       // HH:mm
    private Integer workMin;
    private String  overnightYn;

    // 화면 표시용 (컨트롤러에서 세팅)
    private String  dateDisplay;    // "2026-04-28"
    private String  dayLabel;       // "월","화",...,"일"
    private boolean weekend;        // 토·일 여부
    private boolean hasRecord;      // DB 레코드 존재 여부
}