package com.company.attendancemanagement.dto.department;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DepartmentEmployeeDto {
    private String company;
    private String companyName;
    private String empCode;
    private String empName;
    private String deptCode;
    private String deptName;
    private String roleCode;
    private String roleName;
}
// 나중에 직급, 직책 추가
