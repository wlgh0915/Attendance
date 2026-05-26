package com.company.attendancemanagement.mapper;

import com.company.attendancemanagement.dto.login.CompanyOptionDto;
import com.company.attendancemanagement.dto.login.LoginUserDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LoginMapper {

    LoginUserDto findLoginUser(@Param("company") String company,
                               @Param("empCode") String empCode,
                               @Param("password") String password);

    List<CompanyOptionDto> findActiveCompanies();
}
