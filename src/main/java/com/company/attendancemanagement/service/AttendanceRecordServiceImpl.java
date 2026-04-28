package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.record.AttendanceRecordDto;
import com.company.attendancemanagement.mapper.AttendanceRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendanceRecordServiceImpl implements AttendanceRecordService {

    private final AttendanceRecordMapper recordMapper;

    @Override
    public List<AttendanceRecordDto> findByMonth(String company, String empCode,
                                                  String startYmd, String endYmd) {
        return recordMapper.findByMonth(company, empCode, startYmd, endYmd);
    }

    @Override
    public void upsert(AttendanceRecordDto dto) {
        recordMapper.upsert(dto);
    }

    @Override
    public void delete(String company, String empCode, String yyyymmdd) {
        recordMapper.delete(company, empCode, yyyymmdd);
    }
}