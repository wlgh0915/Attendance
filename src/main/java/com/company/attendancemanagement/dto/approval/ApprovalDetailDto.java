package com.company.attendancemanagement.dto.approval;

import com.company.attendancemanagement.dto.request.ApprovalLineDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ApprovalDetailDto {
    private String requestId;
    private String targetDate;
    private String reqType;
    private String reqGroup;
    private String reason;
    private String reasonDetail;
    private String requestStatus;
    private String targetEmpCode;
    private String targetEmpName;
    private String targetDeptName;
    private String requesterEmpCode;
    private String requesterEmpName;
    private String startTime;
    private String endTime;
    private String changeShiftCode;
    private String changeShiftName;
    private List<ApprovalLineDto> approvalChain;
}