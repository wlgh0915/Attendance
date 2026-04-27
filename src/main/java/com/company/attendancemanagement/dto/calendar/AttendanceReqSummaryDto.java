package com.company.attendancemanagement.dto.calendar;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttendanceReqSummaryDto {
    private String workDate;
    private Long   requestId;
    private String requestCategory;   // OVERTIME / HOLIDAY / LEAVE / OTHER
    private String requestWorkCode;
    private String startTime;
    private String endTime;
    private String status;            // DRAFT / SUBMITTED / APPROVED / REJECTED
}
