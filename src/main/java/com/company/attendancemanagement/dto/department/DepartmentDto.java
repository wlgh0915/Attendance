package com.company.attendancemanagement.dto.department;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DepartmentDto {
    private String company;
    private String deptCode;
    private String deptName;
    private String parentDept;
    private String parentDeptName;
    private String deptLeader;
    private String deptLeaderName;
    private String deptCategory;
    private String workPatternCode;
    private String useYn;
    private String startDate;
    private String endDate;
}