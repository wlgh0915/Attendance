package com.company.attendancemanagement.mapper;

import com.company.attendancemanagement.dto.department.DepartmentCreateDto;
import com.company.attendancemanagement.dto.department.DepartmentDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.company.attendancemanagement.dto.department.DepartmentEmployeeDto;
import com.company.attendancemanagement.dto.department.DepartmentUpdateDto;


import java.util.List;

@Mapper
public interface DepartmentMapper {

    int countByDeptCode(@Param("company") String company,
                        @Param("deptCode") String deptCode);

    int insertDepartment(DepartmentCreateDto dto);

    List<DepartmentDto> findAll(@Param("company") String company);
    List<DepartmentEmployeeDto> findEmployeesByDept(@Param("company") String company,
                                                    @Param("deptCode") String deptCode);
    DepartmentDto findByDeptCode(String company, String deptCode);

    int updateDepartment(DepartmentUpdateDto dto);
}