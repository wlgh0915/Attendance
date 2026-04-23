package com.company.attendancemanagement.dto.pattern;

import lombok.Data;
import java.util.List;

@Data
public class WorkPatternSaveRequest {

    private WorkPatternMasterDto master;

    private List<WorkPatternDetailDto> details;
}