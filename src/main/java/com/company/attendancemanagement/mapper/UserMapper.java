package com.company.attendancemanagement.mapper;

import com.company.attendancemanagement.dto.user.UserCreateDto;
import com.company.attendancemanagement.dto.user.DutyOptionDto;
import com.company.attendancemanagement.dto.user.PositionOptionDto;
import com.company.attendancemanagement.dto.user.UserUpdateDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

    int countByEmpCode(@Param("company") String company,
                       @Param("empCode") String empCode);

    int insertUser(UserCreateDto dto);

    UserUpdateDto findUserForEdit(@Param("company") String company,
                                  @Param("empCode") String empCode);

    int updateUser(UserUpdateDto dto);

    List<PositionOptionDto> findActivePositions(@Param("company") String company);

    List<DutyOptionDto> findActiveDuties(@Param("company") String company);
}
