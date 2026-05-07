package com.company.attendancemanagement.mapper;

import com.company.attendancemanagement.dto.record.AttendanceRecordDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AttendanceRecordMapper {

    List<AttendanceRecordDto> findByMonth(@Param("company")  String company,
                                          @Param("empCode")  String empCode,
                                          @Param("startYmd") String startYmd,
                                          @Param("endYmd")   String endYmd);

    int upsert(AttendanceRecordDto dto);

    int delete(@Param("company")  String company,
               @Param("empCode")  String empCode,
               @Param("yyyymmdd") String yyyymmdd);

    /** 해당 날짜의 계획 근무 시프트 조회 (부서 이동 이력 기반) */
    Map<String, Object> findPlannedShift(@Param("company")  String company,
                                         @Param("empCode")  String empCode,
                                         @Param("yyyymmdd") String yyyymmdd);

    /** 해당 날짜의 승인된 연장근무 신청 조회 (조출연장 포함) */
    List<Map<String, Object>> findApprovedOvertimeRequests(@Param("company")  String company,
                                                           @Param("empCode")  String empCode,
                                                           @Param("yyyymmdd") String yyyymmdd);

    /** 해당 주 총 근무 분 합산 (특정 날짜 제외) */
    int sumWeekWorkMin(@Param("company")    String company,
                       @Param("empCode")    String empCode,
                       @Param("weekStart")  String weekStart,
                       @Param("weekEnd")    String weekEnd,
                       @Param("excludeYmd") String excludeYmd);
}