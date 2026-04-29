package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.record.AttendanceRecordDto;

import java.util.List;

public interface AttendanceRecordService {
    List<AttendanceRecordDto> findByMonth(String company, String empCode, String startYmd, String endYmd);
    void upsert(AttendanceRecordDto dto);
    void delete(String company, String empCode, String yyyymmdd);
}