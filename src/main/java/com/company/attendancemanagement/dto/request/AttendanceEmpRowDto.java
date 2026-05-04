package com.company.attendancemanagement.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class AttendanceEmpRowDto {
    private String empCode;
    private String empName;
    private String deptCode;
    private String deptName;

    private String workPlanCode;
    private String workPlanName;
    private String workDayType;
    private String shiftOnTime;
    private String shiftOffTime;
    private String break1StartHhmm;
    private String break1EndHhmm;
    private String break2StartHhmm;
    private String break2EndHhmm;
    private Integer plannedWorkMin;
    private Integer actualWorkMin;
    private Integer shiftWorkMin;
    private String checkIn;

    private String requestId;
    private String existingRequestGroup;
    private String requestWorkCode;
    private String reason;
    private String reasonDetail;
    private String startTime;
    private String startTimeType;
    private String endTime;
    private String endTimeType;
    private Integer requestWorkMin;
    private String status;
    private String requesterCode;
    private String requesterName;
    private Map<String, AttendanceEmpRowDto> requestsByWorkCode;
}
