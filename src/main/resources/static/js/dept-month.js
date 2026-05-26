const today = new Date().toISOString().slice(0, 10);

function toMinute(hhmm) {
    if (!hhmm) return null;
    const [h, m] = hhmm.split(':').map(Number);
    return h * 60 + m;
}

function plannedShiftMin(day) {
    if (day.workDayType === 'OFF' || day.workDayType === 'HOLIDAY') return 0;
    const approvedOther = (day.requests || []).find(
        r => r.requestCategory === 'OTHER' && r.status === 'APPROVED'
    );
    if (approvedOther) {
        if (!approvedOther.changeShiftOnHhmm) return day.shiftWorkMin || 0;
        return approvedOther.changeShiftWorkMin || 0;
    }
    return day.shiftWorkMin || 0;
}

function fmtHM(min) {
    if (!min || min <= 0) return '-';
    const h = Math.floor(min / 60), m = min % 60;
    return h + 'h' + (m > 0 ? ' ' + m + 'm' : '');
}

function fmtWorkMin(min) {
    if (!min || min <= 0) return '';
    const h = Math.floor(min / 60), m = min % 60;
    return h + 'h' + (m > 0 ? m + 'm' : '');
}

/* ── 날짜 셀 하나 렌더링 ── */
function renderDayCell(day, dateStr) {
    const dow = new Date(dateStr + 'T00:00:00').getDay(); // 0=일, 6=토
    let cls = 'dm-day-cell';
    if (dow === 0) cls += ' col-sun';
    if (dow === 6) cls += ' col-sat';
    if (dateStr === today) cls += ' today';

    const activeOtherReq = (day.requests || []).find(
        r => r.requestCategory === 'OTHER' && ['SUBMITTED', 'APPROVED'].includes(r.status)
    );
    const isLeaveDay = activeOtherReq != null && !activeOtherReq.changeShiftOnHhmm;
    const isBizTrip  = activeOtherReq != null && activeOtherReq.changeShiftName &&
                       activeOtherReq.changeShiftName.includes('출장');
    const hasApprovedLeave = isLeaveDay || isBizTrip ||
        (day.requests || []).some(r => r.requestCategory === 'LEAVE' && r.status === 'APPROVED');
    const isAbsent = day.workDayType === 'WORK' &&
        (!day.record || !day.record.checkIn) && !hasApprovedLeave;
    const isOff = day.workDayType === 'OFF' || day.workDayType === 'HOLIDAY';

    if (isAbsent) cls += ' absent-day';
    else if (isOff && !activeOtherReq) cls += ' off-day';

    let inner = '';

    if (isLeaveDay) {
        const lbl = activeOtherReq.changeShiftName || '기타';
        inner = `<span class="dm-leave">${lbl}</span>`;
    } else if (isAbsent) {
        inner = '<span class="dm-absent">결근</span>';
    } else if (isOff) {
        const holidayReq = (day.requests || []).find(
            r => r.requestWorkCode === '휴일근무' && r.status === 'APPROVED'
        );
        if (holidayReq && day.record && day.record.workMin) {
            inner  = `<span class="dm-shift">휴일근무</span>`;
            inner += `<span class="dm-workhour">${fmtWorkMin(day.record.workMin)}</span>`;
        } else {
            inner = '<span class="dm-off">-</span>';
        }
    } else if (isBizTrip) {
        const shiftLbl = activeOtherReq.changeShiftName || '출장';
        inner = `<span class="dm-bizt">${shiftLbl}</span>`;
        if (day.record && day.record.workMin) {
            inner += `<span class="dm-workhour">${fmtWorkMin(day.record.workMin)}</span>`;
        }
    } else if (day.record && day.record.checkIn) {
        const shiftLbl = (activeOtherReq && activeOtherReq.changeShiftName)
            ? activeOtherReq.changeShiftName : (day.shiftName || '');
        if (shiftLbl) inner += `<span class="dm-shift">${shiftLbl}</span>`;
        if (day.record.workMin) {
            inner += `<span class="dm-workhour">${fmtWorkMin(day.record.workMin)}</span>`;
        }
        if (day.record.lateYn === 'Y') {
            inner += `<span class="dm-late">지각</span>`;
        }
        const approvedReqs = (day.requests || []).filter(
            r => r.requestCategory !== 'OTHER' && r.status === 'APPROVED'
        );
        approvedReqs.forEach(r => {
            inner += `<span class="dm-req-tag">${r.requestWorkCode}</span>`;
        });
    } else if (hasApprovedLeave) {
        const leaveReq = (day.requests || []).find(
            r => r.requestCategory === 'LEAVE' && r.status === 'APPROVED'
        );
        inner = `<span class="dm-leave">${leaveReq ? leaveReq.requestWorkCode : '휴가'}</span>`;
    } else {
        inner = '<span class="dm-off">-</span>';
    }

    return `<td class="${cls}">${inner}</td>`;
}

/* ── 메인 렌더 ── */
function renderMonth() {
    const tbody = document.getElementById('monthBody');
    if (!MONTH_DATA || !MONTH_DATA.length) {
        tbody.innerHTML = `<tr><td colspan="${DAY_DATES.length + 2}" class="no-emp">조회된 사원이 없습니다.</td></tr>`;
        return;
    }

    let html = '';
    MONTH_DATA.forEach(emp => {
        const actualMin  = (emp.days || []).reduce((s, d) =>
            s + ((d.record && d.record.workMin) || 0), 0);
        const plannedMin = (emp.days || []).reduce((s, d) => s + plannedShiftMin(d), 0);

        // 결근 / 지각 집계
        let absentCount = 0, lateCount = 0;
        (emp.days || []).forEach(d => {
            const aOther = (d.requests || []).find(
                r => r.requestCategory === 'OTHER' && ['SUBMITTED', 'APPROVED'].includes(r.status)
            );
            const isLv   = aOther != null && !aOther.changeShiftOnHhmm;
            const isBt   = aOther != null && aOther.changeShiftName && aOther.changeShiftName.includes('출장');
            const hasLv  = isLv || isBt || (d.requests || []).some(
                r => r.requestCategory === 'LEAVE' && r.status === 'APPROVED'
            );
            if (d.workDayType === 'WORK' && (!d.record || !d.record.checkIn) && !hasLv) absentCount++;
            if (!isLv && d.record && d.record.lateYn === 'Y') lateCount++;
        });

        // 날짜별 day 맵
        const dayMap = {};
        (emp.days || []).forEach(d => { dayMap[d.workDate] = d; });

        html += '<tr>';
        html += `<td class="dm-emp-cell">
            <div class="emp-name">${emp.empName}</div>
            <div class="emp-code">${emp.empCode}</div>
        </td>`;
        html += `<td class="dm-work-cell">
            <div class="wc-actual">실 ${fmtHM(actualMin)}</div>
            <div class="wc-planned">계획 ${fmtHM(plannedMin)}</div>
            ${absentCount > 0 ? `<div class="wc-absent">결근 ${absentCount}</div>` : ''}
            ${lateCount   > 0 ? `<div class="wc-late">지각 ${lateCount}</div>`   : ''}
        </td>`;

        DAY_DATES.forEach(dateStr => {
            const day = dayMap[dateStr];
            if (!day) {
                const dow = new Date(dateStr + 'T00:00:00').getDay();
                const c = dow === 0 ? 'col-sun' : (dow === 6 ? 'col-sat' : '');
                html += `<td class="dm-day-cell off-day ${c}"><span class="dm-off">-</span></td>`;
            } else {
                html += renderDayCell(day, dateStr);
            }
        });

        html += '</tr>';
    });

    tbody.innerHTML = html;
}

function onDeptChange(val) {
    location.href = `/attendance/department/month?ym=${CURRENT_YM}&deptCode=${val}`;
}

renderMonth();