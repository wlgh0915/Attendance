package com.company.attendancemanagement.dto.approval;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApprovalItemDto {
    private String  requestId;
    private String  targetDate;
    private String  reqType;
    private String  reqGroup;
    private String  reason;
    private String  reasonDetail;
    private String  requestStatus;
    private String  targetEmpCode;
    private String  targetEmpName;
    private String  targetDeptName;
    private String  startTime;
    private String  startTimeType;   // N0: 당일 / N1: 익일
    private String  endTime;
    private String  endTimeType;     // N0: 당일 / N1: 익일
    private String  changeShiftCode;
    private String  changeShiftName;
    private Integer myStepNo;
    private String  myStatus;
    private String  myDecisionAt;
    private String  rejectReason;
}
