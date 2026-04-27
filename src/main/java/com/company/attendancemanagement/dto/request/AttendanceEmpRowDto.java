package com.company.attendancemanagement.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttendanceEmpRowDto {
    // 사원 정보
    private String empCode;
    private String empName;
    private String deptCode;
    private String deptName;
    // 근무계획 (패턴 계산)
    private String workPlanCode;
    private String workPlanName;
    private String workDayType;     // WORK / OFF / HOLIDAY
    // 기존 근태신청 (없으면 null)
    private String requestId;
    private String requestWorkCode;
    private String reason;
    private String reasonDetail;
    private String startTime;
    private String endTime;
    private String status;          // DRAFT / SUBMITTED / APPROVED / REJECTED
    private String requesterCode;
    private String requesterName;
}