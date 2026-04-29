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

        return mergeRequestsByEmployee(requestMapper.searchEmployees(search));
    }

    private List<AttendanceEmpRowDto> mergeRequestsByEmployee(List<AttendanceEmpRowDto> rows) {
        Map<String, AttendanceEmpRowDto> merged = new LinkedHashMap<>();
        for (AttendanceEmpRowDto row : rows) {
            AttendanceEmpRowDto base = merged.computeIfAbsent(row.getEmpCode(), empCode -> {
                row.setRequestsByWorkCode(new LinkedHashMap<>());
                return row;
            });
            if (row.getRequestWorkCode() != null && !row.getRequestWorkCode().isBlank()) {
                base.getRequestsByWorkCode().put(row.getRequestWorkCode(), copyRequestInfo(row));
            }
        }
        return new ArrayList<>(merged.values());
    }

    private AttendanceEmpRowDto copyRequestInfo(AttendanceEmpRowDto source) {
        AttendanceEmpRowDto copy = new AttendanceEmpRowDto();
        copy.setRequestId(source.getRequestId());
        copy.setRequestWorkCode(source.getRequestWorkCode());
        copy.setReason(source.getReason());
        copy.setReasonDetail(source.getReasonDetail());
        copy.setStartTime(source.getStartTime());
        copy.setStartTimeType(source.getStartTimeType());
        copy.setEndTime(source.getEndTime());
        copy.setEndTimeType(source.getEndTimeType());
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
        if (!loginUser.getEmpCode().equals(existing.getRequesterCode())) {
            throw new IllegalArgumentException("신청자만 취소할 수 있습니다.");
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
}
