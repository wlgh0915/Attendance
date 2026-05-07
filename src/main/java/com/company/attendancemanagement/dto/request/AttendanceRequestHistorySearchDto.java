package com.company.attendancemanagement.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AttendanceRequestHistorySearchDto {
    private String company;
    private String fromDate;
    private String toDate;
    private String deptCode;
    private String empCode;
    private String status;
    private String requestCategory;
    private boolean canViewAll;
    private String loginEmpCode;
    private List<String> accessibleDeptCodes;
}
