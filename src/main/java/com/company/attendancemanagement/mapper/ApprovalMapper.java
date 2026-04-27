package com.company.attendancemanagement.mapper;

import com.company.attendancemanagement.dto.request.ApprovalLineDto;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ApprovalMapper {

    void insertApproval(ApprovalLineDto dto);

    List<ApprovalLineDto> findByRequestId(@Param("requestId") Long requestId);

    void deleteByRequestId(@Param("requestId") Long requestId);

    int countApproved(@Param("requestId") Long requestId);
}