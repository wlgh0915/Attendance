package com.company.attendancemanagement.dto.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserCreateDto {

    private String company;

    private String empCode;

    @NotBlank(message = "이름은 필수입니다.")
    private String empName;

    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;

    private String hireDate;

    private String deptCode;

    private String positionCode;

    private String positionDate;

    private String dutyCode;

    private String dutyDate;

    @NotBlank(message = "권한코드는 필수입니다.")
    private String roleCode;
}
