package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.login.CompanyOptionDto;
import com.company.attendancemanagement.dto.login.LoginRequestDto;
import com.company.attendancemanagement.dto.login.LoginUserDto;
import com.company.attendancemanagement.mapper.LoginMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LoginService {

    private final LoginMapper loginMapper;

    public LoginUserDto login(LoginRequestDto dto) {
        return loginMapper.findLoginUser(
                dto.getCompany(),
                dto.getEmpCode(),
                dto.getPassword()
        );
    }

    public List<CompanyOptionDto> findActiveCompanies() {
        return loginMapper.findActiveCompanies();
    }
}
