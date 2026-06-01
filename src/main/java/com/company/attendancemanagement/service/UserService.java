package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.department.DeptTransferDto;
import com.company.attendancemanagement.dto.user.UserCreateDto;
import com.company.attendancemanagement.dto.user.DutyOptionDto;
import com.company.attendancemanagement.dto.user.PositionOptionDto;
import com.company.attendancemanagement.dto.user.RoleOptionDto;
import com.company.attendancemanagement.dto.user.UserListDto;
import com.company.attendancemanagement.dto.user.UserUpdateDto;
import com.company.attendancemanagement.mapper.DepartmentMapper;
import com.company.attendancemanagement.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;

    public String generateNextEmpCode(String company) {
        int maxEmpNumber = userMapper.findMaxEmpNumber(company);
        int nextEmpNumber = maxEmpNumber + 1;

        if (nextEmpNumber > 999) {
            throw new IllegalStateException("Employee code range EMP001-EMP999 is full.");
        }

        return formatEmpCode(nextEmpNumber);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public boolean createUser(UserCreateDto dto) {
        int nextEmpNumber = userMapper.findMaxEmpNumber(dto.getCompany()) + 1;

        while (nextEmpNumber <= 999) {
            String empCode = formatEmpCode(nextEmpNumber);
            int count = userMapper.countByEmpCode(dto.getCompany(), empCode);

            if (count == 0) {
                dto.setEmpCode(empCode);
                userMapper.insertUser(dto);
                return true;
            }

            nextEmpNumber++;
        }

        return false;
    }

    public List<UserListDto> findAllUsers(String company) {
        return userMapper.findAllUsers(company);
    }

    public List<PositionOptionDto> findActivePositions(String company) {
        return userMapper.findActivePositions(company);
    }

    public List<DutyOptionDto> findActiveDuties(String company) {
        return userMapper.findActiveDuties(company);
    }

    public List<RoleOptionDto> findActiveRoles(String company) {
        return userMapper.findActiveRoles(company);
    }

    public UserUpdateDto findUserForEdit(String company, String empCode) {
        UserUpdateDto dto = userMapper.findUserForEdit(company, empCode);
        if (dto != null) {
            dto.setOriginalDeptCode(dto.getDeptCode());
            dto.setOriginalPositionCode(dto.getPositionCode());
            dto.setOriginalDutyCode(dto.getDutyCode());
            dto.setOriginalPositionDate(dto.getPositionDate());
            dto.setOriginalDutyDate(dto.getDutyDate());
        }
        return dto;
    }

    @Transactional
    public boolean updateUser(UserUpdateDto dto) {
        if (isChanged(dto.getOriginalPositionCode(), dto.getPositionCode())
                && !isChanged(dto.getOriginalPositionDate(), dto.getPositionDate())) {
            dto.setPositionDate(LocalDate.now().toString());
        }
        if (isChanged(dto.getOriginalDutyCode(), dto.getDutyCode())
                && !isChanged(dto.getOriginalDutyDate(), dto.getDutyDate())) {
            dto.setDutyDate(LocalDate.now().toString());
        }
        boolean updated = userMapper.updateUser(dto) > 0;
        if (updated && isChanged(dto.getOriginalDeptCode(), dto.getDeptCode())
                && !isBlank(dto.getDeptCode())) {
            String startDate;
            if (isBlank(dto.getOriginalDeptCode())) {
                startDate = !isBlank(dto.getHireDate())
                        ? dto.getHireDate()
                        : LocalDate.now().toString();
            } else {
                startDate = LocalDate.now().toString();
            }
            String endDate = LocalDate.parse(startDate).minusDays(1).toString();
            departmentMapper.closeCurrentTransfer(dto.getCompany(), dto.getEmpCode(), endDate);
            DeptTransferDto transfer = new DeptTransferDto();
            transfer.setCompany(dto.getCompany());
            transfer.setEmpCode(dto.getEmpCode());
            transfer.setDeptCode(dto.getDeptCode());
            transfer.setStartDate(startDate);
            departmentMapper.insertTransferHistory(transfer);
        }
        return updated;
    }

    private boolean isChanged(String before, String after) {
        return !Objects.equals(normalize(before), normalize(after));
    }

    private String normalize(String value) {
        return isBlank(value) ? null : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String formatEmpCode(int empNumber) {
        return String.format("EMP%03d", empNumber);
    }
}
