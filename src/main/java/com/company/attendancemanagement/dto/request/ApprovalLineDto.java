package com.company.attendancemanagement.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApprovalLineDto {
    private String requestId;
    private Integer seq;
    private String company;
    private String approverCode;
    private String approverName;
    private String approvalStatus;  // SUBMITTED / PENDING / APPROVED / REJECTED
    private String approvedAt;
}