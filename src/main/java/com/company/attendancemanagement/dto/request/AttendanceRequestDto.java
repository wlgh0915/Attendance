package com.company.attendancemanagement.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttendanceRequestDto {
    private String company;
    private String requestId;         // REQ_ID (VARCHAR 20)
    private String empCode;           // TARGET_EMP_CODE
    private String deptCode;          // TARGET_DEPT_CODE
    private String workDate;          // TARGET_DATE
    private String requestCategory;   // OVERTIME / HOLIDAY / LEAVE → REQ_GROUP=GENERAL; OTHER → REQ_GROUP=OTHER
    private String requestWorkCode;   // REQ_TYPE (GENERAL) or CHANGE_SHIFT_CODE (OTHER)
    private String reason;
    private String reasonDetail;      // REASON_DETAIL
    private String startTime;         // HH:MM (일반근태만)
    private String startTimeType;     // N0: 당일 / N1: 익일
    private String endTime;           // HH:MM (일반근태만)
    private String endTimeType;       // N0: 당일 / N1: 익일
    private Integer requestWorkMin;
    private String status;            // DRAFT / SUBMITTED / APPROVED / REJECTED
    private String requesterCode;     // REQUESTER_EMP_CODE
    private String requesterDeptCode; // REQUESTER_DEPT_CODE
}
