package com.company.attendancemanagement.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttendanceRequestSearchDto {
    private String company;
    private String requestCategory;   // OVERTIME / HOLIDAY / LEAVE / OTHER
    private String workDate;
    private String today;
    private String deptCode;
    private String empCode;           // 선택 필터 (없으면 null)
    private String workPlanFilter;    // OTHER용: 전체=null, 특정 shift code
    private boolean deptLeader;       // 컨트롤러에서 세팅
    private String loginEmpCode;      // 비부서장일 때 본인만 조회
}
