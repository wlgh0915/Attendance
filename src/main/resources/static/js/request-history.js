function escapeHtml(v) {
    return String(v ?? '').replace(/[&<>"']/g, c => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
    }[c]));
}

function dayTypeLabel(v) {
    return v === 'N1' ? '익일' : '당일';
}

function statusLabel(v) {
    return {
        DRAFT: '미상신',
        SUBMITTED: '승인중',
        APPROVED: '승인',
        REJECTED: '반려',
        CANCELED: '취소',
        PENDING: '대기'
    }[v] || v || '-';
}

function stepTypeLabel(v) {
    return v === 'AGREE' ? '합의' : '승인';
}

async function openHistoryDetail(btn) {
    const requestId = btn.dataset.requestId;
    const res = await fetch('/attendance/request/history/detail?requestId=' + encodeURIComponent(requestId));
    if (!res.ok) {
        alert('상세 조회 중 오류가 발생했습니다.');
        return;
    }
    const d = await res.json();
    if (d.message) {
        alert(d.message);
        return;
    }

    const groupName = d.reqGroup === 'OTHER' ? '기타근태' : '일반근태';
    let timeInfo = '';
    if (d.reqGroup === 'GENERAL') {
        const start = dayTypeLabel(d.startTimeType) + ' ' + (d.startTime || '-');
        const end = dayTypeLabel(d.endTimeType) + ' ' + (d.endTime || '-');
        timeInfo = '<div class="lbl">시간</div><div class="val">' + escapeHtml(start + ' ~ ' + end) + '</div>';
    } else {
        timeInfo = '<div class="lbl">변경근무</div><div class="val">'
            + escapeHtml(d.changeShiftName || d.changeShiftCode || '-') + '</div>';
    }

    const lateText = d.recordLateYn === 'Y'
        ? 'Y (' + (d.recordLateMin ?? 0) + '분)'
        : (d.recordLateYn || '-');

    document.getElementById('detailInfo').innerHTML =
        '<div class="lbl">신청번호</div><div class="val">' + escapeHtml(d.requestId) + '</div>'
        + '<div class="lbl">근무구분</div><div class="val">' + escapeHtml(groupName + ' / ' + (d.reqType || '-')) + '</div>'
        + '<div class="lbl">근무일</div><div class="val">' + escapeHtml(d.targetDate || '-') + '</div>'
        + '<div class="lbl">대상자</div><div class="val">' + escapeHtml((d.targetEmpName || '-') + ' (' + (d.targetEmpCode || '-') + ') / ' + (d.targetDeptName || '-')) + '</div>'
        + '<div class="lbl">신청자</div><div class="val">' + escapeHtml((d.requesterEmpName || '-') + ' (' + (d.requesterEmpCode || '-') + ')') + '</div>'
        + timeInfo
        + '<div class="lbl">출근</div><div class="val">' + escapeHtml(d.recordCheckIn || '-') + '</div>'
        + '<div class="lbl">퇴근</div><div class="val">' + escapeHtml(d.recordCheckOut || '-') + '</div>'
        + '<div class="lbl">실근무분</div><div class="val">' + escapeHtml(d.recordWorkMin ?? '-') + '</div>'
        + '<div class="lbl">익일퇴근</div><div class="val">' + escapeHtml(d.recordOvernightYn || '-') + '</div>'
        + '<div class="lbl">지각</div><div class="val">' + escapeHtml(lateText) + '</div>'
        + '<div class="lbl">사유</div><div class="val">' + escapeHtml(d.reason || '-') + '</div>'
        + '<div class="lbl">상세사유</div><div class="val">' + escapeHtml(d.reasonDetail || '-') + '</div>'
        + '<div class="lbl">상태</div><div class="val">' + escapeHtml(statusLabel(d.requestStatus)) + '</div>';

    const chain = d.approvalChain || [];
    document.getElementById('chainBody').innerHTML = chain.length === 0
        ? '<tr><td colspan="6">결재선 정보가 없습니다.</td></tr>'
        : chain.map(s =>
            '<tr>'
            + '<td>' + escapeHtml(s.stepNo) + '</td>'
            + '<td>' + escapeHtml(stepTypeLabel(s.stepType)) + '</td>'
            + '<td>' + escapeHtml(s.approverName || s.approverEmpCode || '-') + '</td>'
            + '<td>' + escapeHtml(statusLabel(s.status)) + '</td>'
            + '<td>' + escapeHtml(s.decisionAt || '-') + '</td>'
            + '<td style="text-align:left;">' + escapeHtml(s.rejectReason || '-') + '</td>'
            + '</tr>'
        ).join('');

    document.getElementById('detailModal').classList.add('open');
    document.body.classList.add('detail-drawer-open');
}

function closeHistoryDetail() {
    const modal = document.getElementById('detailModal');
    modal.classList.add('closing');
    document.body.classList.remove('detail-drawer-open');
    setTimeout(() => modal.classList.remove('open', 'closing'), 260);
}
