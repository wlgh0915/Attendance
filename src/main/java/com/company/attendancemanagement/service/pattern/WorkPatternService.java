package com.company.attendancemanagement.service.pattern;

import com.company.attendancemanagement.dto.pattern.ShiftCodeDto;
import com.company.attendancemanagement.dto.pattern.WorkPatternSaveRequest;
import com.company.attendancemanagement.dto.pattern.WorkPatternMasterDto;

import java.util.List;

public interface WorkPatternService {

    List<WorkPatternMasterDto> getPatternList(String company);

    WorkPatternSaveRequest getPatternDetail(String company, String workPatternCode);

    List<ShiftCodeDto> getShiftCodes(String company);

    void savePattern(WorkPatternSaveRequest request);
}