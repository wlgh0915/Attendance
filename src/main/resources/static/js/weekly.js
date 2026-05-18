const today = new Date().toISOString().slice(0, 10);

/* ── 배지 헬퍼 (calendar.js 와 동일) ── */
function shiftCls(wdt) {
    if (wdt === 'OFF')     return 'badge b-off';
    if (wdt === 'HOLIDAY') return 'badge b-holiday';
    return wdt ? 'badge b-work' : '';
}
function reqCls(s) {
    const m = {DRAFT:'b-draft', SUBMITTED:'b-submit', APPROVED:'b-approved', REJECTED:'b-rejected', CANCELED:'b-rejected'};
    return 'badge ' + (m[s] || 'b-draft');
}
function catLabel(c) {
    return {OVERTIME:'연장', HOLIDAY:'휴일', LEAVE:'휴가/조퇴', OTHER:'기타'}[c] || c;
}
function statusLabel(s) {
    return {DRAFT:'미상신', SUBMITTED:'승인중', APPROVED:'승인', REJECTED:'반려', CANCELED:'취소'}[s] || s;
}
function toMinute(hhmm) {
    if (!hhmm) return null;
    const [h, m] = hhmm.split(':').map(Number);
    return h * 60 + m;
}
function fmtMin(min) {
    if (min == null || min < 0) return '-';
    const h = Math.floor(min / 60), m = min % 60;
    return h + 'h' + (m > 0 ? ' ' + m + 'm' : '');
}

/* ── 계획 근무분 ── */
function plannedShiftMin(day) {
    if (day.workDayType === 'OFF' || day.workDayType === 'HOLIDAY') return 0;
    const approvedOther = (day.requests || []).find(r => r.requestCategory === 'OTHER' && r.status === 'APPROVED');
    if (approvedOther) {
        if (!approvedOther.changeShiftOnHhmm) return day.shiftWorkMin || 0;
        return approvedOther.changeShiftWorkMin || 0;
    }
    return day.shiftWorkMin || 0;
}

/* ── 신청 가감 ── */
function requestAdjustMin(day) {
    let adj = 0;
    (day.requests || [])
        .filter(r => r.status === 'APPROVED' && r.startTime && r.endTime)
        .forEach(r => {
            const s = toMinute(r.startTime), e = toMinute(r.endTime);
            const min = e < s ? (1440 - s + e) : (e - s);
            if (['연장', '조출연장', '휴일근무'].includes(r.requestWorkCode)) adj += min;
            else if (['조퇴', '외출', '외근', '오전반차', '오후반차'].includes(r.requestWorkCode)) adj -= min;
        });
    return adj;
}

/* ── 날짜 셀 하나 렌더링 ── */
function renderDayCell(day, colIdx) {
    const colCls = colIdx === 0 ? 'col-sun' : colIdx === 6 ? 'col-sat' : '';
    const todCls = day.workDate === today ? 'today' : '';

    const activeOtherReq = (day.requests || []).find(
        r => r.requestCategory === 'OTHER' && ['DRAFT', 'SUBMITTED', 'APPROVED'].includes(r.status)
    );
    const isLeaveDay   = activeOtherReq != null && !activeOtherReq.changeShiftOnHhmm;
    const isBizTripDay = activeOtherReq != null && activeOtherReq.changeShiftName && activeOtherReq.changeShiftName.includes('출장');

    const hasApprovedLeave = isLeaveDay || isBizTripDay || (day.requests || []).some(
        r => r.requestCategory === 'LEAVE' && r.status === 'APPROVED'
    );
    const isAbsent = day.workDayType === 'WORK' &&
        (!day.record || !day.record.checkIn) && !hasApprovedLeave;

    let inner = '';

    // 1. 시프트 배지
    const dispShiftName   = (activeOtherReq && activeOtherReq.changeShiftName) ? activeOtherReq.changeShiftName : day.shiftName;
    const dispOnHhmm      = activeOtherReq ? activeOtherReq.changeShiftOnHhmm  : day.workOnHhmm;
    const dispOffHhmm     = activeOtherReq ? activeOtherReq.changeShiftOffHhmm : day.workOffHhmm;
    const dispWorkDayType = activeOtherReq ? (dispOnHhmm ? 'WORK' : 'OFF') : day.workDayType;

    const hasActiveHolidayReq = (day.requests || []).some(
        r => r.requestWorkCode === '휴일근무' && ['DRAFT', 'SUBMITTED', 'APPROVED'].includes(r.status)
    );
    const isOffOrHoliday = dispWorkDayType === 'OFF' || dispWorkDayType === 'HOLIDAY';

    if (dispShiftName && !isAbsent && !(hasActiveHolidayReq && isOffOrHoliday)) {
        const timePart = (dispWorkDayType === 'WORK' && dispOnHhmm && dispOffHhmm)
            ? `<span class="shift-time"> ${dispOnHhmm}~${dispOffHhmm}</span>` : '';
        inner += `<span class="shift-badge"><span class="${shiftCls(dispWorkDayType)}">${dispShiftName}</span>${timePart}</span>`;
    }

    // 2. 실 출퇴근
    if (!isLeaveDay && day.record && (day.record.checkIn || day.record.checkOut)) {
        const ci = day.record.checkIn  || '--:--';
        const co = day.record.checkOut || '--:--';
        inner += `<span class="rec-badge">출 ${ci} / 퇴 ${co}</span>`;
    }

    // 3. 지각
    if (!isLeaveDay && day.record && day.record.lateYn === 'Y') {
        inner += `<span class="late-badge">지각 ${day.record.lateMin}분</span>`;
    }

    // 4. 결근
    if (isAbsent) {
        inner += `<span class="absent-badge">결근</span>`;
    }

    // 5. 근태신청 배지
    (day.requests || []).filter(r => r.status !== 'CANCELED' && r.status !== 'REJECTED').forEach(r => {
        const typeLabel = (r.requestCategory === 'OTHER' && r.changeShiftName)
            ? r.changeShiftName : (r.requestWorkCode || catLabel(r.requestCategory));
        let timeStr = '';
        if (r.startTime || r.endTime) {
            timeStr = `<span class="shift-time"> ${r.startTime||'--:--'}~${r.endTime||'--:--'}</span>`;
        }
        inner += `<span class="req-badge"><span class="${reqCls(r.status)}">${typeLabel} ${statusLabel(r.status)}</span>${timeStr}</span>`;
    });

    if (!inner) inner = '<span style="color:#ccc;font-size:11px;">-</span>';

    return `<td class="day-cell ${colCls} ${todCls}">${inner}</td>`;
}

/* ── 메인 렌더 ── */
function renderWeekly() {
    const tbody = document.getElementById('weekBody');
    if (!WEEKLY_DATA || !WEEKLY_DATA.length) {
        tbody.innerHTML = `<tr><td colspan="9" class="no-emp">조회된 사원이 없습니다.</td></tr>`;
        return;
    }

    let html = '';
    WEEKLY_DATA.forEach(emp => {
        const actualMin  = (emp.days || []).reduce((sum, d) => sum + ((d.record && d.record.workMin) || 0), 0);
        const plannedMin = (emp.days || []).reduce((sum, d) => sum + plannedShiftMin(d), 0);
        const fmtHM = min => {
            if (!min || min <= 0) return '-';
            const h = Math.floor(min / 60), m = min % 60;
            return h + '시간' + (m > 0 ? ' ' + m + '분' : '');
        };
        const pendingCount = (emp.days || []).reduce((cnt, d) =>
            cnt + (d.requests || []).filter(r => r.status === 'SUBMITTED').length, 0);
        const actualStr = actualMin > 0 ? fmtHM(actualMin) : '0시간';
        const workCellHtml = `<div class="wc-actual">실 ${actualStr}</div>`
                           + `<div class="wc-planned">계획 ${fmtHM(plannedMin)}</div>`;
        const pendingBadge = pendingCount > 0
            ? `<span class="pending-badge">미승인 ${pendingCount}건</span>` : '';
        html += `<tr${pendingCount > 0 ? ' class="has-pending"' : ''}>`;
        html += `<td class="emp-cell"><div class="emp-name">${emp.empName}${pendingBadge}</div><div class="emp-dept">${emp.deptName || ''}</div><div class="emp-code">${emp.empCode}</div></td>`;
        html += `<td class="work-cell">${workCellHtml}</td>`;
        (emp.days || []).forEach((day, idx) => {
            html += renderDayCell(day, idx);
        });
        html += '</tr>';
    });

    html += buildSummaryRow(WEEKLY_DATA);
    tbody.innerHTML = html;
}

/* ── 요약 행 ── */
function buildSummaryRow(data) {
    const empCount = data.length;
    if (!empCount) return '';

    const totalActual  = data.reduce((s, e) => s + (e.days || []).reduce((a, d) => a + ((d.record && d.record.workMin) || 0), 0), 0);
    const totalPlanned = data.reduce((s, e) => s + (e.days || []).reduce((a, d) => a + plannedShiftMin(d), 0), 0);
    const avgActual    = Math.round(totalActual  / empCount);
    const avgPlanned   = Math.round(totalPlanned / empCount);
    const fmtHM = min => {
        if (!min || min <= 0) return '-';
        const h = Math.floor(min / 60), m = min % 60;
        return h + '시간' + (m > 0 ? ' ' + m + '분' : '');
    };

    // 날짜별(0~6) 결근·지각 인원 및 실근무시간 집계
    const daySummary = Array.from({length: 7}, () => ({ absent: 0, late: 0, workMin: 0 }));
    data.forEach(emp => {
        (emp.days || []).forEach((day, idx) => {
            const activeOtherReq = (day.requests || []).find(
                r => r.requestCategory === 'OTHER' && ['DRAFT','SUBMITTED','APPROVED'].includes(r.status)
            );
            const isLeaveDay = activeOtherReq != null && !activeOtherReq.changeShiftOnHhmm;
            const isBizTripDay = activeOtherReq != null && activeOtherReq.changeShiftName && activeOtherReq.changeShiftName.includes('출장');
            const hasApprovedLeave = isLeaveDay || isBizTripDay ||
                (day.requests || []).some(r => r.requestCategory === 'LEAVE' && r.status === 'APPROVED');
            if (day.workDayType === 'WORK' && (!day.record || !day.record.checkIn) && !hasApprovedLeave)
                daySummary[idx].absent++;
            if (!isLeaveDay && day.record && day.record.lateYn === 'Y')
                daySummary[idx].late++;
            daySummary[idx].workMin += (day.record && day.record.workMin) || 0;
        });
    });

    let row = '<tr class="summary-row">';
    row += `<td class="summary-emp"><div class="sum-title">부서 요약</div><div class="sum-sub">총 ${empCount}명</div></td>`;
    row += `<td class="summary-work"><div class="wc-actual">평균 실 ${fmtHM(avgActual)}</div><div class="wc-planned">평균 계획 ${fmtHM(avgPlanned)}</div></td>`;
    daySummary.forEach((s, idx) => {
        const colCls = idx === 0 ? 'col-sun' : idx === 6 ? 'col-sat' : '';
        let inner = `<span class="sum-hours">${fmtHM(s.workMin)}</span>`;
        if (s.absent > 0) inner += `<span class="sum-absent">결근 ${s.absent}명</span>`;
        if (s.late   > 0) inner += `<span class="sum-late">지각 ${s.late}명</span>`;
        row += `<td class="summary-day ${colCls}">${inner}</td>`;
    });
    row += '</tr>';
    return row;
}

/* ── 부서 변경 ── */
function onDeptChange(val) {
    location.href = `/attendance/department/week?workDate=${WORK_DATE}&deptCode=${val}`;
}

/* ── 날짜 변경 ── */
function onDateChange(val) {
    if (!val) return;
    location.href = `/attendance/department/week?workDate=${val}&deptCode=${SELECTED_DEPT}`;
}

/* ── 이번 주 ── */
function goThisWeek() {
    location.href = `/attendance/department/week?deptCode=${SELECTED_DEPT}`;
}

renderWeekly();
