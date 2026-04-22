package com.company.attendancemanagement.dto.department;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DepartmentUpdateDto {

    @NotBlank(message = "회사코드는 필수입니다.")
    private String company;

    @NotBlank(message = "부서코드는 필수입니다.")
    private String deptCode;

    @NotBlank(message = "부서명은 필수입니다.")
    private String deptName;

    private String parentDept;
    private String deptLeader;
    private String deptCategory;
    private String workPatternCode;

    private String useYn = "Y";

    private String startDate;
    private String endDate;
}
