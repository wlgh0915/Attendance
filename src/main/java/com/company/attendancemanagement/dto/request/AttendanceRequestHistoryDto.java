package com.company.attendancemanagement.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttendanceRequestHistoryDto {
    private String requestId;
    private String workDate;
    private String requestCategory;
    private String requestWorkCode;
    private String empCode;
    private String empName;
    private String deptCode;
    private String deptName;
    private String requesterCode;
    private String requesterName;
    private String reason;
    private String reasonDetail;
    private String startTime;
    private String startTimeType;
    private String endTime;
    private String endTimeType;
    private Integer requestWorkMin;
    private String status;
    private String requestedAt;
    private String updatedAt;
}
