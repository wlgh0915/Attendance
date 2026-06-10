package com.company.attendancemanagement.mapper;

import com.company.attendancemanagement.dto.department.DepartmentCreateDto;
import com.company.attendancemanagement.dto.department.DepartmentDto;
import com.company.attendancemanagement.dto.department.DeptTransferDto;
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
    List<DepartmentDto> findAllForDropdown(@Param("company") String company);
    List<DepartmentEmployeeDto> findActiveEmployees(@Param("company") String company);
    List<DepartmentEmployeeDto> findEmployeesByDept(@Param("company") String company,
                                                    @Param("deptCode") String deptCode);
    List<DepartmentEmployeeDto> findUnassignedEmployees(@Param("company") String company);
    String findCompanyName(@Param("company") String company);
    DepartmentDto findByDeptCode(String company, String deptCode);

    int countLeaderInOtherDept(@Param("company") String company,
                               @Param("empCode") String empCode,
                               @Param("deptCode") String deptCode);

    int countDeptLedByEmp(@Param("company") String company,
                          @Param("empCode") String empCode);

    String findDeptLedByEmp(@Param("company") String company,
                            @Param("empCode") String empCode);

    String findEmployeeDeptCode(@Param("company") String company,
                                @Param("empCode") String empCode);

    String findEmployeeRetireDate(@Param("company") String company,
                                  @Param("empCode") String empCode);

    int countEmployeesByDept(@Param("company") String company,
                             @Param("deptCode") String deptCode);

    int updateDepartment(DepartmentUpdateDto dto);
    int updateEmployeesDept(@Param("company") String company,
                            @Param("empCodes") List<String> empCodes,
                            @Param("deptCode") String deptCode);

    void insertTransferHistory(DeptTransferDto dto);

    int countTransferHistoryByStartDate(@Param("company") String company,
                                        @Param("empCode") String empCode,
                                        @Param("startDate") String startDate);

    int updateTransferHistoryByStartDate(DeptTransferDto dto);

    void closeCurrentTransfer(@Param("company")  String company,
                              @Param("empCode")  String empCode,
                              @Param("startDate") String startDate,
                              @Param("endDate")  String endDate);

    void closeCurrentTransferForRetirement(@Param("company") String company,
                                           @Param("empCode") String empCode,
                                           @Param("retireDate") String retireDate);

    int closeTransfersForRetiredEmployees();

    void deleteConflictingOpenTransfers(@Param("company")   String company,
                                        @Param("empCode")   String empCode,
                                        @Param("startDate") String startDate);

}
