package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.record.AttendanceRecordDto;

import java.util.List;
import java.util.Map;

public interface AttendanceRecordService {
    List<AttendanceRecordDto> findByMonth(String company, String empCode, String startYmd, String endYmd);
    Map<String, Object> getPlannedShift(String company, String empCode, String yyyymmdd);
    int calculateWorkMin(AttendanceRecordDto dto);
    void upsert(AttendanceRecordDto dto);
    void delete(String company, String empCode, String yyyymmdd);
}
