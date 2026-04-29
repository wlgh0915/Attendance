package com.company.attendancemanagement.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

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
    private String shiftOnTime;     // 근무 시작 시각 HH:mm
    private String shiftOffTime;    // 근무 종료 시각 HH:mm
    private Integer shiftWorkMin;   // 예상 근무 분
    // 기존 근태신청 (없으면 null)
    private String requestId;
    private String existingRequestGroup;
    private String requestWorkCode;
    private String reason;
    private String reasonDetail;
    private String startTime;
    private String startTimeType;    // N0: 당일 / N1: 익일
    private String endTime;
    private String endTimeType;      // N0: 당일 / N1: 익일
    private Integer requestWorkMin;
    private String status;          // DRAFT / SUBMITTED / APPROVED / REJECTED
    private String requesterCode;
    private String requesterName;
    private Map<String, AttendanceEmpRowDto> requestsByWorkCode;
}
