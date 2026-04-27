package com.company.attendancemanagement.service;

import com.company.attendancemanagement.dto.approval.ApprovalDetailDto;
import com.company.attendancemanagement.dto.approval.ApprovalItemDto;
import com.company.attendancemanagement.dto.approval.ApprovalSearchDto;
import com.company.attendancemanagement.dto.login.LoginUserDto;
import com.company.attendancemanagement.dto.request.AttendanceRequestDto;
import com.company.attendancemanagement.dto.request.ApprovalLineDto;
import com.company.attendancemanagement.mapper.ApprovalMapper;
import com.company.attendancemanagement.mapper.AttendanceApprovalMapper;
import com.company.attendancemanagement.mapper.AttendanceRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendanceApprovalServiceImpl implements AttendanceApprovalService {

    private final AttendanceApprovalMapper approvalMapper;
    private final ApprovalMapper           baseApprovalMapper;
    private final AttendanceRequestMapper  requestMapper;

    @Override
    public List<ApprovalItemDto> searchApprovals(ApprovalSearchDto search, LoginUserDto loginUser) {
        search.setCompany(loginUser.getCompany());
        search.setApproverEmpCode(loginUser.getEmpCode());
        return approvalMapper.searchApprovals(search);
    }

    @Override
    public ApprovalDetailDto getDetail(String requestId, LoginUserDto loginUser) {
        ApprovalDetailDto detail = approvalMapper.findDetail(requestId, loginUser.getCompany(), loginUser.getEmpCode());
        if (detail == null) throw new IllegalArgumentException("조회할 수 없는 근태신청입니다.");
        List<ApprovalLineDto> chain = baseApprovalMapper.findByRequestId(requestId);
        detail.setApprovalChain(chain);
        return detail;
    }

    @Override
    @Transactional
    public void approveRequest(String requestId, LoginUserDto loginUser) {
        validateSubmittedRequest(requestId);
        ApprovalLineDto myStep = approvalMapper.findMyStep(requestId, loginUser.getCompany(), loginUser.getEmpCode());
        if (myStep == null) throw new IllegalArgumentException("결재 권한이 없는 신청입니다.");
        if (!"PENDING".equals(myStep.getStatus())) {
            throw new IllegalArgumentException("이미 처리된 결재건입니다.");
        }
        validatePreviousSteps(requestId, loginUser.getCompany(), myStep.getStepNo());
        approvalMapper.updateStepApproved(requestId, myStep.getStepNo());

        if ("Y".equals(myStep.getIsFinal())) {
            requestMapper.updateStatus(requestId, "APPROVED");
        }
    }

    @Override
    @Transactional
    public void rejectRequest(String requestId, String rejectReason, LoginUserDto loginUser) {
        validateSubmittedRequest(requestId);
        ApprovalLineDto myStep = approvalMapper.findMyStep(requestId, loginUser.getCompany(), loginUser.getEmpCode());
        if (myStep == null) throw new IllegalArgumentException("결재 권한이 없는 신청입니다.");
        if (!"PENDING".equals(myStep.getStatus())) {
            throw new IllegalArgumentException("이미 처리된 결재건입니다.");
        }
        validatePreviousSteps(requestId, loginUser.getCompany(), myStep.getStepNo());
        approvalMapper.updateStepRejected(requestId, myStep.getStepNo(), rejectReason);
        requestMapper.updateStatus(requestId, "REJECTED");
    }

    private void validateSubmittedRequest(String requestId) {
        AttendanceRequestDto request = requestMapper.findByRequestId(requestId);
        if (request == null) throw new IllegalArgumentException("존재하지 않는 근태신청입니다.");
        if (!"SUBMITTED".equals(request.getStatus())) {
            throw new IllegalArgumentException("현재 결재 처리할 수 없는 신청입니다.");
        }
    }

    private void validatePreviousSteps(String requestId, String company, int stepNo) {
        int previousUnapproved = approvalMapper.countPreviousUnapprovedSteps(requestId, company, stepNo);
        if (previousUnapproved > 0) {
            throw new IllegalArgumentException("이전 결재 단계가 완료되지 않았습니다.");
        }
    }
}
