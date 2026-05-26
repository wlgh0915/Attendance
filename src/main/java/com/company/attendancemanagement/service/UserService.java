package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.user.UserCreateDto;
import com.company.attendancemanagement.dto.user.DutyOptionDto;
import com.company.attendancemanagement.dto.user.PositionOptionDto;
import com.company.attendancemanagement.dto.user.RoleOptionDto;
import com.company.attendancemanagement.dto.user.UserUpdateDto;
import com.company.attendancemanagement.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    public boolean createUser(UserCreateDto dto) {
        int count = userMapper.countByEmpCode(dto.getCompany(), dto.getEmpCode());

        if (count > 0) {
            return false;
        }

        userMapper.insertUser(dto);
        return true;
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
            dto.setOriginalPositionCode(dto.getPositionCode());
            dto.setOriginalDutyCode(dto.getDutyCode());
            dto.setOriginalPositionDate(dto.getPositionDate());
            dto.setOriginalDutyDate(dto.getDutyDate());
        }
        return dto;
    }

    public boolean updateUser(UserUpdateDto dto) {
        if (isChanged(dto.getOriginalPositionCode(), dto.getPositionCode())
                && !isChanged(dto.getOriginalPositionDate(), dto.getPositionDate())) {
            dto.setPositionDate(LocalDate.now().toString());
        }
        if (isChanged(dto.getOriginalDutyCode(), dto.getDutyCode())
                && !isChanged(dto.getOriginalDutyDate(), dto.getDutyDate())) {
            dto.setDutyDate(LocalDate.now().toString());
        }
        return userMapper.updateUser(dto) > 0;
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
}
