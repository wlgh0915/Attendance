package com.company.attendancemanagement.mapper;

import com.company.attendancemanagement.dto.approval.ApprovalDetailDto;
import com.company.attendancemanagement.dto.approval.ApprovalItemDto;
import com.company.attendancemanagement.dto.approval.ApprovalSearchDto;
import com.company.attendancemanagement.dto.request.ApprovalLineDto;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AttendanceApprovalMapper {

    List<ApprovalItemDto> searchApprovals(ApprovalSearchDto search);

    ApprovalDetailDto findDetail(@Param("requestId") String requestId,
                                 @Param("company") String company,
                                 @Param("approverEmpCode") String approverEmpCode);

    ApprovalLineDto findMyStep(@Param("requestId") String requestId,
                               @Param("company") String company,
                               @Param("approverEmpCode") String approverEmpCode);

    int countPreviousUnapprovedSteps(@Param("requestId") String requestId,
                                     @Param("company") String company,
                                     @Param("stepNo") int stepNo);

    int updateStepApproved(@Param("requestId") String requestId,
                           @Param("stepNo") int stepNo);

    int updateStepRejected(@Param("requestId") String requestId,
                           @Param("stepNo") int stepNo,
                           @Param("rejectReason") String rejectReason);
}
