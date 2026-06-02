package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.department.DepartmentCreateDto;
import com.company.attendancemanagement.dto.department.DepartmentDto;
import com.company.attendancemanagement.dto.department.DepartmentEmployeeDto;
import com.company.attendancemanagement.dto.department.DepartmentUpdateDto;
import com.company.attendancemanagement.dto.department.DeptTransferDto;
import com.company.attendancemanagement.mapper.DepartmentMapper;
import com.company.attendancemanagement.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final UserMapper userMapper;

    @Transactional
    public boolean createDepartment(DepartmentCreateDto dto) {
        int count = departmentMapper.countByDeptCode(dto.getCompany(), dto.getDeptCode());

        if (count > 0) {
            return false;
        }

        validateDeptLeaderAvailable(dto.getCompany(), dto.getDeptLeader(), dto.getDeptCode());
        departmentMapper.insertDepartment(dto);
        moveDeptLeaderToDept(dto.getCompany(), dto.getDeptLeader(), dto.getDeptCode());
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

    public String findCompanyName(String company) {
        return departmentMapper.findCompanyName(company);
    }

    @Transactional
    public void moveEmployeesToDept(String company, List<String> empCodes,
                                    String deptCode, String transferDate) {
        if (empCodes == null || empCodes.isEmpty()) return;

        String startDate = (transferDate != null && !transferDate.isBlank())
                ? transferDate
                : "2000-01-01";
        String endDate = LocalDate.parse(startDate).minusDays(1).toString();

        for (String empCode : empCodes) {
            validateDeptLeaderMove(company, empCode, deptCode);
            departmentMapper.closeCurrentTransfer(company, empCode, startDate, endDate);

            DeptTransferDto transfer = new DeptTransferDto();
            transfer.setCompany(company);
            transfer.setEmpCode(empCode);
            transfer.setDeptCode(deptCode);
            transfer.setStartDate(startDate);

            int sameDateHistory = departmentMapper.countTransferHistoryByStartDate(company, empCode, startDate);
            if (sameDateHistory > 0) {
                departmentMapper.updateTransferHistoryByStartDate(transfer);
            } else {
                departmentMapper.insertTransferHistory(transfer);
            }
        }

        departmentMapper.updateEmployeesDept(company, empCodes, deptCode);
    }

    @Transactional
    public boolean updateDepartment(DepartmentUpdateDto dto) {
        validateDeptLeaderAvailable(dto.getCompany(), dto.getDeptLeader(), dto.getDeptCode());

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
        if (result > 0) {
            moveDeptLeaderToDept(dto.getCompany(), dto.getDeptLeader(), dto.getDeptCode());
        }
        return result > 0;
    }

    private void validateDeptLeaderAvailable(String company, String deptLeader, String deptCode) {
        if (deptLeader == null || deptLeader.isBlank()) {
            return;
        }
        int leaderCount = departmentMapper.countLeaderInOtherDept(company, deptLeader, deptCode);
        if (leaderCount > 0) {
            throw new IllegalArgumentException("이미 다른 부서의 부서장으로 등록된 사원입니다.");
        }
    }

    private void validateDeptLeaderMove(String company, String empCode, String targetDeptCode) {
        String ledDeptCode = departmentMapper.findDeptLedByEmp(company, empCode);
        if (ledDeptCode != null && !ledDeptCode.isBlank()
                && !Objects.equals(normalize(ledDeptCode), normalize(targetDeptCode))) {
            throw new IllegalArgumentException("부서장으로 등록된 사원은 다른 부서로 이동할 수 없습니다.");
        }
    }

    private void moveDeptLeaderToDept(String company, String deptLeader, String deptCode) {
        if (deptLeader == null || deptLeader.isBlank()) {
            return;
        }

        String currentDeptCode = departmentMapper.findEmployeeDeptCode(company, deptLeader);
        if (Objects.equals(normalize(currentDeptCode), normalize(deptCode))) {
            promoteUserLeaderRole(company, deptLeader);
            return;
        }

        moveEmployeesToDept(company, List.of(deptLeader), deptCode, LocalDate.now().toString());
        promoteUserLeaderRole(company, deptLeader);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void promoteUserLeaderRole(String company, String empCode) {
        String roleCode = userMapper.findRoleCode(company, empCode);
        if ("User".equals(roleCode) || "USER".equals(roleCode)) {
            userMapper.updateRoleCode(company, empCode, "TEAM_LEADER");
        }
    }
}
