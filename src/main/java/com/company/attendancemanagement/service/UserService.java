package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.user.UserCreateDto;
import com.company.attendancemanagement.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}