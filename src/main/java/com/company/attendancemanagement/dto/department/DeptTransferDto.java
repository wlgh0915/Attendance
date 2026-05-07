package com.company.attendancemanagement.dto.department;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeptTransferDto {
    private String company;
    private String empCode;
    private String deptCode;
    private String startDate;  // yyyy-MM-dd
    private String endDate;    // yyyy-MM-dd, null = 현재
}