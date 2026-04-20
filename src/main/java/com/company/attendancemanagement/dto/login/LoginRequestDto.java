package com.company.attendancemanagement.dto.login;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequestDto {

    @NotBlank(message = "회사코드는 필수입니다.")
    private String company;

    @NotBlank(message = "사원코드는 필수입니다.")
    private String empCode;

    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;
}