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

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendanceApprovalServiceImpl implements AttendanceApprovalService {

    private final AttendanceApprovalMapper approvalMapper;
    private final ApprovalMapper           baseApprovalMapper;
    private final AttendanceRequestMapper  requestMapper;
    private final AnnualLeaveService       annualLeaveService;
    private final AttendanceRecordService  recordService;

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
            AttendanceRequestDto request = requestMapper.findByRequestId(requestId);
            if (request != null && "OTHER".equals(request.getRequestCategory())) {
                validateOtherRequestAvailable(request);
            }
            if (request != null) {
                annualLeaveService.validateAvailableForApproval(request);
            }
            requestMapper.updateStatus(requestId, "APPROVED");
            requestMapper.applyApprovedOtherRequestToAttendance(requestId);
            requestMapper.applyApprovedHolidayRequestToAttendance(requestId);
            if (request != null) {
                annualLeaveService.refreshApprovedUsage(request);
            }
            if (request != null && ("연장".equals(request.getRequestWorkCode())
                    || "조출연장".equals(request.getRequestWorkCode())
                    || "휴일근무".equals(request.getRequestWorkCode()))) {
                String yyyymmdd = request.getWorkDate().replace("-", "");
                recordService.recalculateIfRecordExists(
                        request.getCompany(), request.getEmpCode(), yyyymmdd);
                recordService.autoSetCheckoutIfLater(
                        request.getCompany(), request.getEmpCode(), yyyymmdd,
                        request.getEndTime(), request.getEndTimeType());
            }
            // 근무변경(OTHER) 승인 시: 해당 기간 전체 날짜 실적 재계산
            if (request != null && "OTHER".equals(request.getRequestCategory())
                    && request.getWorkDate() != null && request.getEndDate() != null) {
                LocalDate start = LocalDate.parse(request.getWorkDate());
                LocalDate end   = LocalDate.parse(request.getEndDate());
                for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                    recordService.recalculateIfRecordExists(
                            request.getCompany(), request.getEmpCode(),
                            d.toString().replace("-", ""));
                }
            }
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
        if ("OTHER".equals(request.getRequestCategory())) {
            validateOtherRequestDateRange(request);
        }
    }

    private void validateOtherRequestDateRange(AttendanceRequestDto request) {
        if (request.getWorkDate() == null || request.getWorkDate().isBlank()
                || request.getEndDate() == null || request.getEndDate().isBlank()) {
            throw new IllegalArgumentException("기타 근태 신청 기간이 올바르지 않습니다.");
        }
        if (LocalDate.parse(request.getEndDate()).isBefore(LocalDate.parse(request.getWorkDate()))) {
            throw new IllegalArgumentException("종료 날짜는 근무일보다 빠를 수 없습니다.");
        }
    }

    private void validateOtherRequestAvailable(AttendanceRequestDto request) {
        validateOtherRequestDateRange(request);
        if (requestMapper.countOtherRangeNonWorkDays(request) > 0) {
            throw new IllegalArgumentException("기타 근태 기간에는 휴무일/휴일을 포함할 수 없습니다.");
        }
        if (requestMapper.countActiveGeneralRequestInOtherRange(request) > 0) {
            throw new IllegalArgumentException("기타 근태 기간에 이미 일반 근태 신청이 있습니다.");
        }
        if (requestMapper.countActiveSameWorkRequest(request) > 0) {
            throw new IllegalArgumentException("기타 근태 기간에 이미 기타 근태 신청이 있습니다.");
        }
    }

    private void validatePreviousSteps(String requestId, String company, int stepNo) {
        int previousUnapproved = approvalMapper.countPreviousUnapprovedSteps(requestId, company, stepNo);
        if (previousUnapproved > 0) {
            throw new IllegalArgumentException("이전 결재 단계가 완료되지 않았습니다.");
        }
    }
}
