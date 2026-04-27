package com.company.attendancemanagement.dto.approval;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApprovalSearchDto {
    private String company;
    private String approverEmpCode;
    private String stepStatus;    // PENDING / APPROVED / REJECTED
    private String category;      // OVERTIME / HOLIDAY / LEAVE / OTHER
    private String fromDate;
    private String toDate;
    private String empCode;       // 대상자 사번 필터
}
