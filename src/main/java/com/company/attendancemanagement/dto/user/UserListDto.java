package com.company.attendancemanagement.dto.user;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserListDto {
    private String empCode;
    private String empName;
    private String deptName;
    private String positionName;
    private String dutyName;
    private String roleCode;
    private String roleName;
    private String hireDate;
    private String retireDate;
}
