package com.company.attendancemanagement.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserUpdateDto {

    private String company;

    @NotBlank(message = "사번은 필수입니다.")
    private String empCode;

    @NotBlank(message = "이름은 필수입니다.")
    private String empName;

    private String password;

    private String hireDate;

    private String retireDate;

    @Pattern(regexp = "^$|[1-6]", message = "퇴사 사유는 1부터 6까지의 값만 선택할 수 있습니다.")
    private String retireReason;

    private String deptCode;

    private String originalDeptCode;

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
