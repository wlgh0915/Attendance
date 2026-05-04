package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.department.DepartmentDto;
import com.company.attendancemanagement.dto.login.LoginUserDto;
import com.company.attendancemanagement.dto.pattern.ShiftCodeDto;
import com.company.attendancemanagement.dto.request.*;
import com.company.attendancemanagement.mapper.ApprovalMapper;
import com.company.attendancemanagement.mapper.AttendanceRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AttendanceRequestServiceImpl implements AttendanceRequestService {

    private final AttendanceRequestMapper requestMapper;
    private final ApprovalMapper approvalMapper;

    private static final DateTimeFormatter REQ_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final int MAX_WEEK_MIN = 3120;

    @Override
    public Map<String, Object> getFormData(LoginUserDto loginUser) {
        Map<String, Object> data = new HashMap<>();
        String deptLeader = requestMapper.findDeptLeader(loginUser.getCompany(), loginUser.getDeptCode());
        boolean isDeptLeader = loginUser.getEmpCode().equals(deptLeader);
        boolean isAdmin      = "ADMIN".equals(loginUser.getRoleCode());
        boolean canViewAll   = isAdmin || isDeptLeader;

        List<DepartmentDto> depts = isAdmin
                ? requestMapper.findAccessibleDepts(loginUser.getCompany(), loginUser.getDeptCode())
                : requestMapper.findDeptListForDropdown(loginUser.getCompany());
        List<ShiftCodeDto> shiftCodes = requestMapper.findShiftCodes(loginUser.getCompany());

        data.put("depts", depts);
        data.put("shiftCodes", shiftCodes);
        data.put("isDeptLeader", isDeptLeader);
        data.put("isAdmin", isAdmin);
        data.put("canViewAll", canViewAll);
        data.put("myDeptCode", loginUser.getDeptCode());
        return data;
    }

    @Override
    public List<AttendanceEmpRowDto> searchEmployees(AttendanceRequestSearchDto search, LoginUserDto loginUser) {
        String deptLeader = requestMapper.findDeptLeader(loginUser.getCompany(), loginUser.getDeptCode());
        boolean isDeptLeader = loginUser.getEmpCode().equals(deptLeader);
        boolean isAdmin      = "ADMIN".equals(loginUser.getRoleCode());
        boolean canViewAll   = isAdmin || isDeptLeader;

        search.setCompany(loginUser.getCompany());
        search.setDeptLeader(canViewAll);
        search.setLoginEmpCode(loginUser.getEmpCode());

        if (!canViewAll) {
            search.setDeptCode(loginUser.getDeptCode());
        } else if (isAdmin) {
            List<DepartmentDto> accessible = requestMapper.findAccessibleDepts(
                    loginUser.getCompany(), loginUser.getDeptCode());
            boolean ok = accessible.stream()
                    .anyMatch(d -> d.getDeptCode().equals(search.getDeptCode()));
            if (!ok) search.setDeptCode(loginUser.getDeptCode());
        }

        return mergeRequestsByEmployee(requestMapper.searchEmployees(search), search.getRequestCategory());
    }

    private List<AttendanceEmpRowDto> mergeRequestsByEmployee(List<AttendanceEmpRowDto> rows, String requestCategory) {
        Map<String, AttendanceEmpRowDto> merged = new LinkedHashMap<>();
        for (AttendanceEmpRowDto row : rows) {
            AttendanceEmpRowDto requestInfo = copyRequestInfo(row);
            String requestWorkCode = row.getRequestWorkCode();
            AttendanceEmpRowDto base = merged.computeIfAbsent(row.getEmpCode(), empCode -> {
                row.setRequestsByWorkCode(new LinkedHashMap<>());
                clearDisplayRequest(row);
                return row;
            });
            if (requestWorkCode != null && !requestWorkCode.isBlank()) {
                AttendanceEmpRowDto stored = base.getRequestsByWorkCode().get(requestWorkCode);
                if (stored == null || shouldReplaceRequestInfo(stored, requestInfo)) {
                    base.getRequestsByWorkCode().put(requestWorkCode, requestInfo);
                }
                if (matchesSearchCategory(requestCategory, requestInfo, requestWorkCode)) {
                    applyDisplayRequest(base, requestInfo);
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    private boolean shouldReplaceRequestInfo(AttendanceEmpRowDto stored, AttendanceEmpRowDto candidate) {
        boolean storedInactive = "REJECTED".equals(stored.getStatus()) || "CANCELED".equals(stored.getStatus());
        boolean candidateActive = "DRAFT".equals(candidate.getStatus())
                || "SUBMITTED".equals(candidate.getStatus())
                || "APPROVED".equals(candidate.getStatus());
        return storedInactive && candidateActive;
    }

    private void clearDisplayRequest(AttendanceEmpRowDto row) {
        row.setRequestId(null);
        row.setExistingRequestGroup(null);
        row.setRequestWorkCode(null);
        row.setReason(null);
        row.setReasonDetail(null);
        row.setStartTime(null);
        row.setStartTimeType(null);
        row.setEndTime(null);
        row.setEndTimeType(null);
        row.setRequestWorkMin(null);
        row.setStatus(null);
        row.setRequesterCode(null);
        row.setRequesterName(null);
    }

    private boolean matchesSearchCategory(String requestCategory, AttendanceEmpRowDto row, String requestWorkCode) {
        if ("OTHER".equals(requestCategory)) {
            return "OTHER".equals(row.getExistingRequestGroup());
        }
        if (!"GENERAL".equals(row.getExistingRequestGroup())) {
            return false;
        }
        if ("OVERTIME".equals(requestCategory)) {
            return "연장".equals(requestWorkCode) || "조출연장".equals(requestWorkCode);
        }
        if ("HOLIDAY".equals(requestCategory)) {
            return "휴일근무".equals(requestWorkCode);
        }
        if ("LEAVE".equals(requestCategory)) {
            return "조퇴".equals(requestWorkCode)
                    || "외근".equals(requestWorkCode)
                    || "외출".equals(requestWorkCode)
                    || "전반차".equals(requestWorkCode)
                    || "후반차".equals(requestWorkCode)
                    || "오전반차".equals(requestWorkCode)
                    || "오후반차".equals(requestWorkCode);
        }
        return false;
    }

    private void applyDisplayRequest(AttendanceEmpRowDto target, AttendanceEmpRowDto source) {
        if (target.getRequestId() != null
                && !target.getRequestId().equals(source.getRequestId())
                && !shouldReplaceRequestInfo(target, source)) {
            return;
        }
        target.setRequestId(source.getRequestId());
        target.setExistingRequestGroup(source.getExistingRequestGroup());
        target.setRequestWorkCode(source.getRequestWorkCode());
        target.setReason(source.getReason());
        target.setReasonDetail(source.getReasonDetail());
        target.setStartTime(source.getStartTime());
        target.setStartTimeType(source.getStartTimeType());
        target.setEndTime(source.getEndTime());
        target.setEndTimeType(source.getEndTimeType());
        target.setRequestWorkMin(source.getRequestWorkMin());
        target.setStatus(source.getStatus());
        target.setRequesterCode(source.getRequesterCode());
        target.setRequesterName(source.getRequesterName());
    }

    private AttendanceEmpRowDto copyRequestInfo(AttendanceEmpRowDto source) {
        AttendanceEmpRowDto copy = new AttendanceEmpRowDto();
        copy.setRequestId(source.getRequestId());
        copy.setExistingRequestGroup(source.getExistingRequestGroup());
        copy.setRequestWorkCode(source.getRequestWorkCode());
        copy.setReason(source.getReason());
        copy.setReasonDetail(source.getReasonDetail());
        copy.setStartTime(source.getStartTime());
        copy.setStartTimeType(source.getStartTimeType());
        copy.setEndTime(source.getEndTime());
        copy.setEndTimeType(source.getEndTimeType());
        copy.setRequestWorkMin(source.getRequestWorkMin());
        copy.setStatus(source.getStatus());
        copy.setRequesterCode(source.getRequesterCode());
        copy.setRequesterName(source.getRequesterName());
        return copy;
    }

    @Override
    @Transactional
    public AttendanceRequestDto saveRequest(AttendanceRequestDto dto, LoginUserDto loginUser) {
        dto.setCompany(loginUser.getCompany());
        if (dto.getRequesterCode() == null || dto.getRequesterCode().isBlank()) {
            dto.setRequesterCode(loginUser.getEmpCode());
        }
        dto.setRequesterDeptCode(loginUser.getDeptCode());

        boolean isOther = "OTHER".equals(dto.getRequestCategory());

        if (!isOther && requestMapper.countAttendanceCheckIn(
                dto.getCompany(), dto.getEmpCode(), dto.getWorkDate()) == 0) {
            throw new IllegalArgumentException("출근 기록이 없으면 일반 근태를 신청할 수 없습니다.");
        }

        int requestWorkMin = 0;
        if (!isOther) {
            requestWorkMin = validateRequestTimeRange(dto);
        }

        // 조출연장: 시작시간 09:00 이전, 연장: 종료시간 18:00 이후
        if ("조출연장".equals(dto.getRequestWorkCode())
                && absoluteMinute(dto.getStartTimeType(), dto.getStartTime()) >= toMinute("09:00")) {
            throw new IllegalArgumentException("조출연장은 시작시간이 09:00 이전이어야 합니다.");
        }
        if ("연장".equals(dto.getRequestWorkCode())
                && absoluteMinute(dto.getEndTimeType(), dto.getEndTime()) <= toMinute("18:00")) {
            throw new IllegalArgumentException("연장근무는 종료시간이 18:00 이후여야 합니다.");
        }

        if (isBoundedLeaveRequest(dto.getRequestWorkCode())) {
            validateWithinEffectiveWorkTime(dto);
        }

        // 휴일근무는 계획 근무유형이 OFF/HOLIDAY인 날에만 신청 가능
        if ("HOLIDAY".equals(dto.getRequestCategory())) {
            String wdt = requestMapper.findPlannedWorkDayType(
                    dto.getCompany(), dto.getEmpCode(), dto.getWorkDate());
            if (wdt == null || (!wdt.equals("OFF") && !wdt.equals("HOLIDAY"))) {
                throw new IllegalArgumentException("휴일근무는 휴일(OFF/HOLIDAY)에만 신청할 수 있습니다.");
            }
        }

        if (!isOther) {
            validateNoOverlappingRequest(dto);
            validateWeeklyWorkLimit(dto, requestWorkMin);
        }

        if (dto.getRequestId() != null && !dto.getRequestId().isBlank()) {
            AttendanceRequestDto existing = requestMapper.findByRequestId(dto.getRequestId());
            if (existing == null) throw new IllegalArgumentException("존재하지 않는 근태신청입니다.");
            if ("SUBMITTED".equals(existing.getStatus()) || "APPROVED".equals(existing.getStatus())) {
                throw new IllegalArgumentException("상신 또는 승인된 건은 수정할 수 없습니다.");
            }
            validateNoActiveSameWorkRequest(dto);
            dto.setStatus("DRAFT");
            requestMapper.updateRequestHeader(dto);
            if (isOther) {
                requestMapper.updateOtherDetail(dto);
            } else {
                requestMapper.updateGeneralDetail(dto);
            }
        } else {
            dto.setRequestId(LocalDateTime.now().format(REQ_ID_FORMAT));
            dto.setStatus("DRAFT");
            validateNoActiveSameWorkRequest(dto);
            requestMapper.insertRequestHeader(dto);
            if (isOther) {
                requestMapper.insertOtherDetail(dto);
            } else {
                requestMapper.insertGeneralDetail(dto);
            }
        }
        return dto;
    }

    private int validateRequestTimeRange(AttendanceRequestDto dto) {
        if (dto.getStartTime() == null || dto.getStartTime().isBlank()
                || dto.getEndTime() == null || dto.getEndTime().isBlank()) {
            throw new IllegalArgumentException("시작/종료 시간을 선택하세요.");
        }
        int start = absoluteMinute(dto.getStartTimeType(), dto.getStartTime());
        int end = absoluteMinute(dto.getEndTimeType(), dto.getEndTime());
        if (end <= start) {
            throw new IllegalArgumentException("종료 시간이 시작 시간보다 늦어야 합니다.");
        }
        return end - start;
    }

    private boolean isSameDay(String timeType) {
        return timeType == null || timeType.isBlank() || "N0".equals(timeType);
    }

    private int absoluteMinute(String timeType, String hhmm) {
        return ("N1".equals(timeType) ? 1440 : 0) + toMinute(hhmm);
    }

    private int toMinute(String hhmm) {
        LocalTime time = LocalTime.parse(hhmm);
        return time.getHour() * 60 + time.getMinute();
    }

    private void validateWeeklyWorkLimit(AttendanceRequestDto dto, int requestWorkMin) {
        int effectMin = requestEffectMin(dto.getRequestCategory(), dto.getRequestWorkCode(), requestWorkMin);
        if (effectMin <= 0) {
            return;
        }
        int plannedMin = requestMapper.findWeeklyPlannedWorkMin(
                dto.getCompany(), dto.getEmpCode(), dto.getWorkDate());
        int activeRequestMin = requestMapper.sumActiveWeeklyRequestEffectMin(dto);
        int totalMin = plannedMin + activeRequestMin + effectMin;
        if (totalMin > MAX_WEEK_MIN) {
            int overMin = totalMin - MAX_WEEK_MIN;
            throw new IllegalArgumentException(String.format(
                    "주 52시간을 초과하여 신청할 수 없습니다. 초과 시간: %d시간 %d분",
                    overMin / 60, overMin % 60));
        }
    }

    private int requestEffectMin(String category, String workCode, int workMin) {
        if ("OVERTIME".equals(category) || "HOLIDAY".equals(category)
                || "연장".equals(workCode) || "조출연장".equals(workCode) || "휴일근무".equals(workCode)) {
            return workMin;
        }
        if ("LEAVE".equals(category)) {
            return -workMin;
        }
        return 0;
    }

    private void validateNoOverlappingRequest(AttendanceRequestDto dto) {
        if (requestMapper.countActiveOverlappingRequest(dto) > 0) {
            throw new IllegalArgumentException("같은 시간대에 이미 다른 근태 신청이 있습니다.");
        }
    }

    private boolean isBoundedLeaveRequest(String workCode) {
        return "조퇴".equals(workCode)
                || "외출".equals(workCode)
                || "전반차".equals(workCode)
                || "후반차".equals(workCode)
                || "오전반차".equals(workCode)
                || "오후반차".equals(workCode);
    }

    private void validateWithinEffectiveWorkTime(AttendanceRequestDto dto) {
        Map<String, Object> timeInfo = requestMapper.findEffectiveWorkTimeInfo(dto);
        if (timeInfo == null
                || timeInfo.get("effectiveStartMin") == null
                || timeInfo.get("effectiveEndMin") == null) {
            return;
        }
        int start = absoluteMinute(dto.getStartTimeType(), dto.getStartTime());
        int end = absoluteMinute(dto.getEndTimeType(), dto.getEndTime());
        int effectiveStart = ((Number) timeInfo.get("effectiveStartMin")).intValue();
        int effectiveEnd = ((Number) timeInfo.get("effectiveEndMin")).intValue();

        if (start < effectiveStart) {
            throw new IllegalArgumentException("시작 시간이 유효 근무 시작 시간 이전입니다.");
        }
        if (end > effectiveEnd) {
            throw new IllegalArgumentException("종료 시간이 유효 근무 종료 시간 이후입니다.");
        }
    }

    private void validateNoActiveSameWorkRequest(AttendanceRequestDto dto) {
        if (dto.getRequestWorkCode() == null || dto.getRequestWorkCode().isBlank()) {
            return;
        }
        int duplicateCount = requestMapper.countActiveSameWorkRequest(dto);
        if (duplicateCount > 0) {
            throw new IllegalArgumentException("같은 일자에 같은 근무 신청이 이미 있습니다.");
        }
    }

    @Override
    @Transactional
    public void deleteRequest(String requestId, LoginUserDto loginUser) {
        AttendanceRequestDto existing = requestMapper.findByRequestId(requestId);
        if (existing == null) throw new IllegalArgumentException("존재하지 않는 근태신청입니다.");
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new IllegalArgumentException("상신된 근태신청은 삭제할 수 없습니다.");
        }
        requestMapper.deleteGeneralDetail(requestId);
        requestMapper.deleteOtherDetail(requestId);
        requestMapper.deleteRequestHeader(requestId);
    }

    @Override
    @Transactional
    public void submitRequest(String requestId, LoginUserDto loginUser) {
        AttendanceRequestDto existing = requestMapper.findByRequestId(requestId);
        if (existing == null) throw new IllegalArgumentException("존재하지 않는 근태신청입니다.");
        if ("APPROVED".equals(existing.getStatus())) {
            throw new IllegalArgumentException("이미 승인완료된 신청입니다.");
        }
        if ("SUBMITTED".equals(existing.getStatus())) {
            throw new IllegalArgumentException("이미 상신된 신청입니다.");
        }

        String company       = loginUser.getCompany();
        String requesterCode = loginUser.getEmpCode();

        approvalMapper.deleteByRequestId(requestId);

        List<ApprovalLineDto> chain = buildApprovalChain(requestId, company, requesterCode, loginUser.getDeptCode());

        for (ApprovalLineDto step : chain) {
            approvalMapper.insertApproval(step);
        }

        boolean allApproved = chain.stream()
                .filter(s -> "APPROVE".equals(s.getStepType()))
                .allMatch(s -> "APPROVED".equals(s.getStatus()));

        requestMapper.updateStatus(requestId, allApproved ? "APPROVED" : "SUBMITTED");
    }

    /**
     * 결재선 생성: SUBMIT(신청자) + 부서장 계층 순회 APPROVE 단계
     * 신청자가 곧 부서장이면 해당 APPROVE 단계를 자동 승인 처리
     */
    private List<ApprovalLineDto> buildApprovalChain(String requestId, String company,
                                                      String requesterCode, String deptCode) {
        List<ApprovalLineDto> chain = new ArrayList<>();

        // STEP 1: 신청자 (SUBMIT)
        ApprovalLineDto submitStep = new ApprovalLineDto();
        submitStep.setRequestId(requestId);
        submitStep.setStepNo(1);
        submitStep.setCompany(company);
        submitStep.setApproverEmpCode(requesterCode);
        submitStep.setStepType("SUBMIT");
        submitStep.setIsFinal("N");
        submitStep.setStatus("APPROVED");
        chain.add(submitStep);

        // 부서 계층을 순회하며 APPROVE 단계 추가
        Set<String> visitedDepts = new HashSet<>();
        String currentDeptCode = deptCode;

        while (currentDeptCode != null && !currentDeptCode.isBlank()
                && visitedDepts.add(currentDeptCode)) {
            Map<String, String> deptInfo = approvalMapper.findDeptApprovalInfo(company, currentDeptCode);
            if (deptInfo == null) break;

            String leader    = deptInfo.get("deptLeader");
            String parentDept = deptInfo.get("parentDept");

            if (leader != null && !leader.isBlank()) {
                ApprovalLineDto approveStep = new ApprovalLineDto();
                approveStep.setRequestId(requestId);
                approveStep.setStepNo(chain.size() + 1);
                approveStep.setCompany(company);
                approveStep.setApproverEmpCode(leader);
                approveStep.setStepType("APPROVE");
                approveStep.setIsFinal("N");
                // 신청자가 해당 부서장이면 자동 승인
                approveStep.setStatus(requesterCode.equals(leader) ? "APPROVED" : "PENDING");
                chain.add(approveStep);
            }

            currentDeptCode = parentDept;
        }

        // 마지막 단계에 IS_FINAL='Y' 설정
        if (!chain.isEmpty()) {
            chain.get(chain.size() - 1).setIsFinal("Y");
        }

        return chain;
    }

    @Override
    @Transactional
    public void cancelSubmit(String requestId, LoginUserDto loginUser) {
        AttendanceRequestDto existing = requestMapper.findByRequestId(requestId);
        if (existing == null) throw new IllegalArgumentException("존재하지 않는 근태신청입니다.");
        if (!canCancelRequest(existing, loginUser)) {
            throw new IllegalArgumentException("신청자 또는 부서장만 취소할 수 있습니다.");
        }
        if ("SUBMITTED".equals(existing.getStatus()) || "APPROVED".equals(existing.getStatus())) {
            int approvedCount = approvalMapper.countApprovedByApprover(requestId);
            if ("SUBMITTED".equals(existing.getStatus()) && approvedCount == 0) {
                approvalMapper.deleteByRequestId(requestId);
                requestMapper.updateStatus(requestId, "DRAFT");
                return;
            }
            requestMapper.updateStatus(requestId, "CANCELED");
            return;
        }
        throw new IllegalArgumentException("승인중 또는 승인완료 상태의 신청만 취소할 수 있습니다.");
    }

    private boolean canCancelRequest(AttendanceRequestDto request, LoginUserDto loginUser) {
        if (loginUser.getEmpCode().equals(request.getRequesterCode())) {
            return true;
        }
        String deptLeader = requestMapper.findDeptLeader(loginUser.getCompany(), request.getDeptCode());
        return loginUser.getEmpCode().equals(deptLeader);
    }
}
