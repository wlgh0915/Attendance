package com.company.attendancemanagement.mapper;

import com.company.attendancemanagement.dto.user.UserCreateDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    int countByEmpCode(@Param("company") String company,
                       @Param("empCode") String empCode);

    int insertUser(UserCreateDto dto);
}