package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.department.DepartmentCreateDto;
import com.company.attendancemanagement.dto.department.DepartmentDto;
import com.company.attendancemanagement.dto.department.DepartmentEmployeeDto;
import com.company.attendancemanagement.dto.department.DepartmentUpdateDto;
import com.company.attendancemanagement.mapper.DepartmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentMapper departmentMapper;

    public boolean createDepartment(DepartmentCreateDto dto) {
        int count = departmentMapper.countByDeptCode(dto.getCompany(), dto.getDeptCode());

        if (count > 0) {
            return false;
        }

        departmentMapper.insertDepartment(dto);
        return true;
    }

    public List<DepartmentDto> findAll(String company) {
        return departmentMapper.findAll(company);
    }

    public List<DepartmentDto> findAllForDropdown(String company) {
        return departmentMapper.findAllForDropdown(company);
    }

    public List<DepartmentEmployeeDto> findActiveEmployees(String company) {
        return departmentMapper.findActiveEmployees(company);
    }

    public DepartmentDto findByDeptCode(String company, String deptCode) {
        return departmentMapper.findByDeptCode(company, deptCode);
    }

    public List<DepartmentEmployeeDto> findEmployeesByDept(String company, String deptCode) {
        return departmentMapper.findEmployeesByDept(company, deptCode);
    }

    public List<DepartmentEmployeeDto> findUnassignedEmployees(String company) {
        return departmentMapper.findUnassignedEmployees(company);
    }

    public void moveEmployeesToDept(String company, List<String> empCodes, String deptCode) {
        if (empCodes != null && !empCodes.isEmpty()) {
            departmentMapper.updateEmployeesDept(company, empCodes, deptCode);
        }
    }

    public boolean updateDepartment(DepartmentUpdateDto dto) {
        // useYn이 N으로 변경되는 경우, 해당 부서에 직원이 있는지 확인
        DepartmentDto currentDept = departmentMapper.findByDeptCode(dto.getCompany(), dto.getDeptCode());

        if ("Y".equals(currentDept.getUseYn()) && "N".equals(dto.getUseYn())) {
            // Y에서 N으로 변경되는 경우, 직원이 없어야 함
            int employeeCount = departmentMapper.countEmployeesByDept(dto.getCompany(), dto.getDeptCode());
            if (employeeCount > 0) {
                return false;
            }
        }

        int result = departmentMapper.updateDepartment(dto);
        return result > 0;
    }
}