package com.company.attendancemanagement.dto.pattern;

import lombok.Data;
import java.time.LocalDate;

@Data
public class WorkPatternMasterDto {

    private String company;

    // 패턴코드
    private String workPatternCode;

    // 패턴명
    private String workPatternName;

    private String patternType;
    private String cycleUnit;
    private Integer cycleCount;

    private LocalDate startDate;
    private LocalDate endDate;

    // 사용여부
    private String useYn;
}