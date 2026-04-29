package com.company.attendancemanagement.mapper;

import com.company.attendancemanagement.dto.record.AttendanceRecordDto;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AttendanceRecordMapper {

    List<AttendanceRecordDto> findByMonth(@Param("company")  String company,
                                          @Param("empCode")  String empCode,
                                          @Param("startYmd") String startYmd,
                                          @Param("endYmd")   String endYmd);

    int upsert(AttendanceRecordDto dto);

    int delete(@Param("company")  String company,
               @Param("empCode")  String empCode,
               @Param("yyyymmdd") String yyyymmdd);
}