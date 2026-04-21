package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.department.DepartmentCreateDto;
import com.company.attendancemanagement.dto.department.DepartmentDto;
import com.company.attendancemanagement.mapper.DepartmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.company.attendancemanagement.dto.department.DepartmentEmployeeDto;
import com.company.attendancemanagement.dto.department.DepartmentUpdateDto;
import java.util.List;

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

    public DepartmentDto findByDeptCode(String company, String deptCode) {
        return departmentMapper.findByDeptCode(company, deptCode);
    }

    public List<DepartmentEmployeeDto> findEmployeesByDept(String company, String deptCode) {
        return departmentMapper.findEmployeesByDept(company, deptCode);
    }

    public boolean updateDepartment(DepartmentUpdateDto dto) {
        int result = departmentMapper.updateDepartment(dto);
        return result > 0;
    }
}