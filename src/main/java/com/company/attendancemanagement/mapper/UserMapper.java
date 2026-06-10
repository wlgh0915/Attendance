package com.company.attendancemanagement.mapper;

import com.company.attendancemanagement.dto.user.UserCreateDto;
import com.company.attendancemanagement.dto.user.DutyOptionDto;
import com.company.attendancemanagement.dto.user.PositionOptionDto;
import com.company.attendancemanagement.dto.user.RoleOptionDto;
import com.company.attendancemanagement.dto.user.UserListDto;
import com.company.attendancemanagement.dto.user.UserUpdateDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

    int countByEmpCode(@Param("company") String company,
                       @Param("empCode") String empCode);

    Integer findMaxEmpNumber(@Param("company") String company);

    int insertUser(UserCreateDto dto);

    UserUpdateDto findUserForEdit(@Param("company") String company,
                                  @Param("empCode") String empCode);

    int updateUser(UserUpdateDto dto);

    int clearDepartmentsForRetiredEmployees();

    String findRoleCode(@Param("company") String company,
                        @Param("empCode") String empCode);

    int updateRoleCode(@Param("company") String company,
                       @Param("empCode") String empCode,
                       @Param("roleCode") String roleCode);

    List<UserListDto> findAllUsers(@Param("company") String company);

    List<PositionOptionDto> findActivePositions(@Param("company") String company);

    List<DutyOptionDto> findActiveDuties(@Param("company") String company);

    List<RoleOptionDto> findActiveRoles(@Param("company") String company);
}
