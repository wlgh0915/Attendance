package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.record.AttendanceRecordDto;

import java.util.List;
import java.util.Map;

public interface AttendanceRecordService {
    List<AttendanceRecordDto> findByMonth(String company, String empCode, String startYmd, String endYmd);
    AttendanceRecordDto findByDay(String company, String empCode, String yyyymmdd);
    Map<String, Object> getPlannedShift(String company, String empCode, String yyyymmdd);
    int calculateWorkMin(AttendanceRecordDto dto);
    void upsert(AttendanceRecordDto dto);
    void delete(String company, String empCode, String yyyymmdd);
    void recalculateIfRecordExists(String company, String empCode, String yyyymmdd);

    /** 연장/휴일근무 신청 승인 시 checkOut을 신청 종료시간으로 자동 갱신 (현재보다 늦을 때만) */
    void autoSetCheckoutIfLater(String company, String empCode, String yyyymmdd,
                                String endTime, String endTimeType);
}
