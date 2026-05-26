package com.company.attendancemanagement.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoleMenuMapper {

    List<String> findPermittedMenuUrls(@Param("company") String company,
                                       @Param("roleCode") String roleCode);
}
