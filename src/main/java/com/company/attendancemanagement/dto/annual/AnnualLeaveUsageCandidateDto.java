package com.company.attendancemanagement.dto.annual;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnnualLeaveUsageCandidateDto {
    private String requestId;
    private String requestCategory;
    private String requestWorkCode;
    private String workDate;
    private String endDate;
    private Integer requestWorkMin;
}
