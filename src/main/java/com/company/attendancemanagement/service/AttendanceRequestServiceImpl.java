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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AttendanceRequestServiceImpl implements AttendanceRequestService {

    private final AttendanceRequestMapper requestMapper;
    private final ApprovalMapper approvalMapper;

    private static final DateTimeFormatter REQ_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    @Override
    public Map<String, Object> getFormData(LoginUserDto loginUser) {
        Map<String, Object> data = new HashMap<>();
        List<DepartmentDto> depts = requestMapper.findDeptListForDropdown(loginUser.getCompany());
        List<ShiftCodeDto> shiftCodes = requestMapper.findShiftCodes(loginUser.getCompany());
        String deptLeader = requestMapper.findDeptLeader(loginUser.getCompany(), loginUser.getDeptCode());
        boolean isDeptLeader = loginUser.getEmpCode().equals(deptLeader);
        boolean isAdmin      = "ADMIN".equals(loginUser.getRoleCode());
        boolean canViewAll   = isAdmin || isDeptLeader;

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
        }

        return requestMapper.searchEmployees(search);
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
            requestMapper.insertRequestHeader(dto);
            if (isOther) {
                requestMapper.insertOtherDetail(dto);
            } else {
                requestMapper.insertGeneralDetail(dto);
            }
        }
        return dto;
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

        String deptLeader = requestMapper.findDeptLeader(loginUser.getCompany(), loginUser.getDeptCode());
        boolean isDeptLeader = loginUser.getEmpCode().equals(deptLeader);

        approvalMapper.deleteByRequestId(requestId);

        if (isDeptLeader) {
            requestMapper.updateStatus(requestId, "APPROVED");
            ApprovalLineDto line = new ApprovalLineDto();
            line.setRequestId(requestId);
            line.setSeq(1);
            line.setCompany(loginUser.getCompany());
            line.setApproverCode(loginUser.getEmpCode());
            line.setApprovalStatus("APPROVED");
            approvalMapper.insertApproval(line);
        } else {
            requestMapper.updateStatus(requestId, "SUBMITTED");
            ApprovalLineDto line1 = new ApprovalLineDto();
            line1.setRequestId(requestId);
            line1.setSeq(1);
            line1.setCompany(loginUser.getCompany());
            line1.setApproverCode(loginUser.getEmpCode());
            line1.setApprovalStatus("SUBMITTED");
            approvalMapper.insertApproval(line1);
            if (deptLeader != null && !deptLeader.isBlank()) {
                ApprovalLineDto line2 = new ApprovalLineDto();
                line2.setRequestId(requestId);
                line2.setSeq(2);
                line2.setCompany(loginUser.getCompany());
                line2.setApproverCode(deptLeader);
                line2.setApprovalStatus("PENDING");
                approvalMapper.insertApproval(line2);
            }
        }
    }

    @Override
    @Transactional
    public void cancelSubmit(String requestId, LoginUserDto loginUser) {
        AttendanceRequestDto existing = requestMapper.findByRequestId(requestId);
        if (existing == null) throw new IllegalArgumentException("존재하지 않는 근태신청입니다.");
        if (!"SUBMITTED".equals(existing.getStatus())) {
            throw new IllegalArgumentException("상신 상태의 신청만 취소할 수 있습니다.");
        }
        if (!loginUser.getEmpCode().equals(existing.getRequesterCode())) {
            throw new IllegalArgumentException("상신자만 상신취소할 수 있습니다.");
        }
        int approved = approvalMapper.countApproved(requestId);
        if (approved > 0) {
            throw new IllegalArgumentException("이미 승인된 건은 상신취소할 수 없습니다.");
        }
        approvalMapper.deleteByRequestId(requestId);
        requestMapper.updateStatus(requestId, "DRAFT");
    }
}