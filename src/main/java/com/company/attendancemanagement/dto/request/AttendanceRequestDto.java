package com.company.attendancemanagement.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttendanceRequestDto {
    private String company;
    private Long requestId;
    private String empCode;
    private String workDate;
    private String requestCategory;   // OVERTIME / HOLIDAY / LEAVE / OTHER
    private String requestWorkCode;
    private String reason;
    private String startTime;         // HH:MM
    private String endTime;           // HH:MM
    private String status;            // DRAFT / SUBMITTED / APPROVED / REJECTED
    private String requesterCode;
}