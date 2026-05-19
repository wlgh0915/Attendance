package com.company.attendancemanagement.dto.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserUpdateDto {

    @NotBlank(message = "회사코드는 필수입니다.")
    private String company;

    @NotBlank(message = "사번은 필수입니다.")
    private String empCode;

    @NotBlank(message = "이름은 필수입니다.")
    private String empName;

    private String password;

    private String deptCode;

    private String positionCode;

    private String originalPositionCode;

    private String positionDate;

    private String originalPositionDate;

    private String dutyCode;

    private String originalDutyCode;

    private String dutyDate;

    private String originalDutyDate;

    @NotBlank(message = "권한코드는 필수입니다.")
    private String roleCode;

    private String returnDeptCode;
}
