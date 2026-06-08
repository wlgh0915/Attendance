const today = new Date().toISOString().slice(0, 10);

/* ───────── 캘린더 렌더링 ───────── */
function dowToCol(num) { return num - 1; } // MSSQL: 1=일~7=토

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

function getNextDate(dateStr) {
    const [y, m, d] = dateStr.split('-').map(Number);
    const next = new Date(y, m - 1, d + 1);
    return next.getFullYear() + '-'
        + String(next.getMonth() + 1).padStart(2, '0') + '-'
        + String(next.getDate()).padStart(2, '0');
}

function earlyMin(day) {
    if (!day || !day.record || !day.record.checkIn || !day.workOnHhmm) return 0;
    const hasEarlyApproval = (day.requests || []).some(
        r => r.requestWorkCode === '조출연장' && r.status === 'APPROVED'
    );
    if (!hasEarlyApproval) return 0;
    const actual = toMinute(day.record.checkIn);
    const planned = toMinute(day.workOnHhmm);
    if (actual == null || planned == null) return 0;
    return Math.max(0, planned - actual);
}

function hasShiftMismatch(day) {
    if (!day || !day.record || !day.record.shiftCode) return false;
    const approvedOther = (day.requests || []).find(
        r => r.requestCategory === 'OTHER' && r.status === 'APPROVED' && r.changeShiftCode
    );
    if ((day.workDayType === 'OFF' || day.workDayType === 'HOLIDAY') && !approvedOther) return false;
    const effectiveShiftCode = approvedOther ? approvedOther.changeShiftCode : day.shiftCode;
    if (!effectiveShiftCode) return false;
    const recordCode = day.record.actualShiftCode || day.record.shiftCode;
    return recordCode !== effectiveShiftCode;
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

function requestAdjustMin(day) {
    let adj = 0;
    (day.requests || [])
        .filter(r => r.status === 'APPROVED' && r.startTime && r.endTime)
        .forEach(r => {
            const s = toMinute(r.startTime);
            const e = toMinute(r.endTime);
            const min = e < s ? (1440 - s + e) : (e - s);
            if (['연장', '조출연장', '휴일근무'].includes(r.requestWorkCode)) {
                adj += min;
            } else if (['조퇴', '외출', '외근', '오전반차', '오후반차'].includes(r.requestWorkCode)) {
                adj -= min;
            }
        });
    return adj;
}

function fmtMin(min) {
    if (min < 0) return '-';
    const h = Math.floor(min / 60), m = min % 60;
    return h + '시간' + (m > 0 ? ' ' + m + '분' : '');
}

function renderCalendar() {
    if (!DAYS.length) return;
    const firstCol = dowToCol(DAYS[0].dayOfWeekNum);
    const grid = document.getElementById('calGrid');
    let html = '';

    // 자정을 넘는 연장근무: 신청한 당일에 표시
    const overnightMap = {};
    DAYS.forEach(day => {
        (day.requests || []).forEach(r => {
            if (['연장', '조출연장'].includes(r.requestWorkCode) && r.startTime && r.endTime &&
                    toMinute(r.endTime) < toMinute(r.startTime) &&
                    ['DRAFT', 'SUBMITTED', 'APPROVED'].includes(r.status)) {
                if (!overnightMap[day.workDate]) overnightMap[day.workDate] = [];
                overnightMap[day.workDate].push(r);
            }
        });
    });

    // 앞쪽 빈 셀
    for (let i = 0; i < firstCol; i++) {
        html += '<div class="cal-cell empty"></div>';
    }

    let totalMin = 0, totalActualMin = 0;

    DAYS.forEach(day => {
        const activeOtherReq = (day.requests || []).find(
            r => r.requestCategory === 'OTHER' && ['DRAFT', 'SUBMITTED', 'APPROVED'].includes(r.status)
        );
        const hasActiveHolidayReq = (day.requests || []).some(
            r => r.requestWorkCode === '휴일근무' && ['SUBMITTED', 'APPROVED'].includes(r.status)
        );
        const isLeaveDay = activeOtherReq != null && !activeOtherReq.changeShiftOnHhmm;
        const isBizTripDay = activeOtherReq != null &&
            activeOtherReq.changeShiftName && activeOtherReq.changeShiftName.includes('출장');

        const hasApprovedLeave = isLeaveDay || isBizTripDay || (day.requests || []).some(
            r => r.requestCategory === 'LEAVE' && r.status === 'APPROVED'
        );
        const isAbsent = day.workDayType === 'WORK' &&
            (!day.record || !day.record.checkIn) && !hasApprovedLeave;

        // 월 신청 기준 합산
        if (day.inCurrentMonth && !isAbsent) {
            totalMin += plannedShiftMin(day);
            totalMin += requestAdjustMin(day);
        }

        // 월 급여 산정 기준 합산
        const hasLeaveApproval = (day.requests || []).some(r => r.requestCategory === 'LEAVE' && r.status === 'APPROVED');
        const approvedBizTrip = (day.requests || []).some(
            r => r.requestCategory === 'OTHER' && r.status === 'APPROVED' &&
                 r.changeShiftName && r.changeShiftName.includes('출장')
        );
        if (day.inCurrentMonth) {
            if (day.record && day.record.workMin != null && day.record.checkIn) {
                totalActualMin += day.record.workMin;
            } else if (hasLeaveApproval || approvedBizTrip || isLeaveDay) {
                totalActualMin += plannedShiftMin(day);
            }
        }

        const c       = dowToCol(day.dayOfWeekNum);
        const colCls  = c === 0 ? 'col-sun' : c === 6 ? 'col-sat' : '';
        const todCls  = day.workDate === today ? 'today' : '';
        const monthCls = day.inCurrentMonth ? '' : 'out-month';
        const dn      = parseInt(day.workDate.slice(8));

        let inner = `<div class="date-num">${dn}</div>`;

        // 1. 계획/변경 근태코드
        const dispShiftName  = (activeOtherReq && activeOtherReq.changeShiftName)
            ? activeOtherReq.changeShiftName : day.shiftName;
        const dispOnHhmm  = activeOtherReq ? activeOtherReq.changeShiftOnHhmm  : day.workOnHhmm;
        const dispOffHhmm = activeOtherReq ? activeOtherReq.changeShiftOffHhmm : day.workOffHhmm;
        const dispWorkDayType = activeOtherReq
            ? (dispOnHhmm ? 'WORK' : 'OFF')
            : day.workDayType;

        const isOffOrHolidayShift = dispWorkDayType === 'OFF' || dispWorkDayType === 'HOLIDAY';
        if (dispShiftName && !isAbsent && !(hasActiveHolidayReq && isOffOrHolidayShift)) {
            const timePart = (dispWorkDayType === 'WORK' && dispOnHhmm && dispOffHhmm)
                ? `<span class="shift-time"> ${dispOnHhmm}~${dispOffHhmm}</span>`
                : '';
            inner += `<span class="shift-badge"><span class="${shiftCls(dispWorkDayType)}">${dispShiftName}</span>${timePart}</span>`;
        }

        // 2. 실 출퇴근 시간
        if (!isLeaveDay && day.record && (day.record.checkIn || day.record.checkOut)) {
            const ci = day.record.checkIn  || '--:--';
            const co = day.record.checkOut || dispOffHhmm || '--:--';
            inner += `<span class="rec-badge">출 ${ci} / 퇴 ${co}</span>`;
        }

        // 3. 지각
        if (!isLeaveDay && day.record && day.record.lateYn === 'Y') {
            inner += `<span class="late-badge">지각 ${day.record.lateMin}분</span>`;
        }

        if (hasShiftMismatch(day)) {
            inner += `<span class="mismatch-badge">근태코드 불일치</span>`;
        }

        if (isAbsent) {
            inner += `<span class="absent-badge">결근</span>`;
        }

        // 4. 근태신청 (미상신/취소/반려 제외)
        (day.requests || []).filter(r => r.status !== 'DRAFT' && r.status !== 'CANCELED' && r.status !== 'REJECTED').forEach(r => {
            const typeLabel = (r.requestCategory === 'OTHER' && r.changeShiftName)
                ? r.changeShiftName
                : (r.requestWorkCode || catLabel(r.requestCategory));
            let timeStr = '';
            if (r.startTime || r.endTime) {
                const st = r.startTime || '--:--';
                const et = r.endTime   || '--:--';
                timeStr = `<span class="shift-time"> ${st}~${et}</span>`;
            }
            inner += `<span class="req-badge"><span class="${reqCls(r.status)}">${typeLabel} ${statusLabel(r.status)}</span>${timeStr}</span>`;
        });

        // 5. 익일 근무
        (overnightMap[day.workDate] || []).forEach(r => {
            inner += `<span class="overnight-badge">익일근무 ~${r.endTime}</span>`;
        });

        html += `<div class="cal-cell ${colCls} ${todCls} ${monthCls}">${inner}</div>`;
    });

    // 뒤쪽 빈 셀 (마지막 행 채우기)
    const totalCells = firstCol + DAYS.length;
    const rem = totalCells % 7;
    if (rem !== 0) {
        for (let i = 0; i < 7 - rem; i++) {
            html += '<div class="cal-cell empty"></div>';
        }
    }

    grid.innerHTML = html;

    // 하단 통계 바 업데이트
    document.getElementById('totalMinDisplay').textContent = fmtMin(totalMin);
    document.getElementById('totalActualMinDisplay').textContent = fmtMin(totalActualMin);
}

/* ───────── 부서/사원 변경 ───────── */
function onDeptChange(deptCode) {
    location.href = `/attendance/calendar?ym=${CURRENT_YM}&deptCode=${deptCode}`;
}

function onEmpChange(empCode) {
    if (!empCode) return;
    const dept = IS_ADMIN ? document.getElementById('deptSelect').value : SELECTED_DEPT;
    location.href = `/attendance/calendar?ym=${CURRENT_YM}&deptCode=${dept}&empCode=${empCode}`;
}

function initRecordGenerateDate() {
    const input = document.getElementById('recordGenerateDate');
    if (!input) return;
    input.value = today.startsWith(CURRENT_YM) ? today : CURRENT_YM + '-01';
}

async function generateDeptRecords() {
    const dateEl = document.getElementById('recordGenerateDate');
    const deptEl = document.getElementById('deptSelect');
    const workDate = dateEl ? dateEl.value : '';
    const deptCode = deptEl ? deptEl.value : SELECTED_DEPT;
    if (!workDate) {
        showToast('실적 반영일을 선택해주세요.', 'error');
        return;
    }
    if (!deptCode) {
        showToast('부서를 선택해주세요.', 'error');
        return;
    }
    if (!confirm('선택한 부서 직원들의 출퇴근 실적을 근무계획 기준으로 반영하시겠습니까?')) return;

    const res = await fetch('/attendance/calendar/generate-records?deptCode='
        + encodeURIComponent(deptCode) + '&workDate=' + encodeURIComponent(workDate), {
        method: 'POST'
    });
    const json = await res.json();
    if (!json.success) {
        showToast(json.message || '실적 반영에 실패했습니다.', 'error');
        return;
    }
    showToast(json.message || '반영되었습니다.', 'success');
    setTimeout(() => location.reload(), 800);
}

function showToast(msg, type) {
    const t = document.getElementById('toast');
    if (!t) return;
    if (type === 'error') console.error('[toast]', msg);
    if (t._toastTimer) clearTimeout(t._toastTimer);
    t.textContent = msg;
    t.className = type;
    t.style.display = 'block';
    t._toastTimer = setTimeout(() => {
        t.style.display = 'none';
        t._toastTimer = null;
    }, 3000);
}

initRecordGenerateDate();
renderCalendar();
