package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.pattern.ShiftCodeDto;
import com.company.attendancemanagement.dto.record.AttendanceRecordDto;
import com.company.attendancemanagement.mapper.AttendanceRecordMapper;
import com.company.attendancemanagement.mapper.pattern.WorkPatternMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendanceRecordServiceImpl implements AttendanceRecordService {

    private final AttendanceRecordMapper recordMapper;
    private final WorkPatternMapper      workPatternMapper;

    @Override
    public List<AttendanceRecordDto> findByMonth(String company, String empCode,
                                                  String startYmd, String endYmd) {
        return recordMapper.findByMonth(company, empCode, startYmd, endYmd);
    }

    @Override
    public void upsert(AttendanceRecordDto dto) {
        calculateLate(dto);
        recordMapper.upsert(dto);
    }

    @Override
    public void delete(String company, String empCode, String yyyymmdd) {
        recordMapper.delete(company, empCode, yyyymmdd);
    }

    private void calculateLate(AttendanceRecordDto dto) {
        if (dto.getCheckIn() == null || dto.getShiftCode() == null) {
            dto.setLateYn("N");
            dto.setLateMin(0);
            return;
        }

        ShiftCodeDto shift = workPatternMapper.findShiftByCode(dto.getCompany(), dto.getShiftCode());
        if (shift == null || shift.getWorkOnHhmm() == null
                || "OFF".equals(shift.getWorkDayType())
                || "HOLIDAY".equals(shift.getWorkDayType())) {
            dto.setLateYn("N");
            dto.setLateMin(0);
            return;
        }

        int scheduled = toMinutes(shift.getWorkOnHhmm());
        int actual    = toMinutes(dto.getCheckIn());

        if (actual > scheduled) {
            dto.setLateYn("Y");
            dto.setLateMin(actual - scheduled);
        } else {
            dto.setLateYn("N");
            dto.setLateMin(0);
        }
    }

    private int toMinutes(String hhmm) {
        String[] parts = hhmm.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }
}