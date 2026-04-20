package com.company.attendancemanagement.dto.login;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginUserDto {
    private String company;
    private String empCode;
    private String empName;
    private String deptCode;
    private String roleCode;
}