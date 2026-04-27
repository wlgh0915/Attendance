package com.company.attendancemanagement.mapper;

import com.company.attendancemanagement.dto.request.ApprovalLineDto;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface ApprovalMapper {

    void insertApproval(ApprovalLineDto dto);

    List<ApprovalLineDto> findByRequestId(@Param("requestId") String requestId);

    void deleteByRequestId(@Param("requestId") String requestId);

    int countApprovedByApprover(@Param("requestId") String requestId);

    // 부서장 + 상위부서 코드 조회 (결재선 생성용)
    Map<String, String> findDeptApprovalInfo(@Param("company") String company, @Param("deptCode") String deptCode);
}