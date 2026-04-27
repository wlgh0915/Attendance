package com.company.attendancemanagement.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApprovalLineDto {
    private String  requestId;       // REQ_ID
    private Integer stepNo;          // STEP_NO
    private String  company;
    private String  approverEmpCode; // APPROVER_EMP_CODE
    private String  approverName;    // 조회용 (DB 컬럼 아님)
    private String  stepType;        // SUBMIT / APPROVE / CC
    private String  isFinal;         // Y / N
    private String  status;          // PENDING / APPROVED / REJECTED
    private String  decisionAt;      // DECISION_AT
    private String  rejectReason;    // REJECT_REASON
}