const today = new Date().toISOString().slice(0, 10);

const WORK_CODES = {
    OVERTIME: [{v:'연장',    l:'연장근로'},   {v:'조출연장', l:'조출연장'}],
    HOLIDAY:  [{v:'휴일근무', l:'휴일근무'}],
    LEAVE:    [{v:'조퇴',    l:'조퇴'},        {v:'외출',    l:'외출'},
               {v:'외근',    l:'외근'},         {v:'전반차',  l:'전반차'}, {v:'후반차', l:'후반차'}],
    OTHER:    [{v:'근무변경', l:'근무변경'}]
};

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

function earlyMin(day) {
    if (!day || !day.record || !day.record.checkIn || !day.workOnHhmm) return 0;
    const actual = toMinute(day.record.checkIn);
    const planned = toMinute(day.workOnHhmm);
    if (actual == null || planned == null) return 0;
    return Math.max(0, planned - actual);
}

function hasShiftMismatch(day) {
    return day && day.record && day.record.shiftCode && day.shiftCode && day.record.shiftCode !== day.shiftCode;
}

function weekTotalCell(weekMin) {
    if (weekMin <= 0) return '<td class="week-total">-</td>';
    const h    = Math.floor(weekMin / 60);
    const m    = weekMin % 60;
    const over = weekMin > 3120;
    const txt  = h + '시간' + (m > 0 ? ' ' + m + '분' : '');
    return `<td class="week-total${over ? ' over' : ''}">${txt}</td>`;
}

function renderCalendar() {
    if (!DAYS.length) return;
    const firstCol = dowToCol(DAYS[0].dayOfWeekNum);
    const tbody = document.getElementById('calBody');
    let html = '', col = 0, weekMin = 0;

    const openRow = () => { html += '<tr>'; col = 0; weekMin = 0; };
    const endRow  = () => {
        while (col < 7) { html += '<td class="empty"></td>'; col++; }
        html += weekTotalCell(weekMin) + '</tr>';
    };

    openRow();
    for (let i = 0; i < firstCol; i++) { html += '<td class="empty"></td>'; col++; }

    DAYS.forEach(day => {
        if (col === 7) { endRow(); openRow(); }
        if (day.record && day.record.workMin) weekMin += day.record.workMin;
        const c      = dowToCol(day.dayOfWeekNum);
        const colCls = c === 0 ? 'col-sun' : c === 6 ? 'col-sat' : '';
        const todCls = day.workDate === today ? 'today' : '';
        const monthCls = day.inCurrentMonth ? '' : 'out-month';
        const dn     = parseInt(day.workDate.slice(8));

        let inner = `<div class="date-num">${dn}</div>`;

        // 1. 계획 근태코드 (항상 표시)
        if (day.shiftName) {
            const timePart = (day.workDayType === 'WORK' && day.workOnHhmm && day.workOffHhmm)
                ? `<span class="shift-time"> ${day.workOnHhmm}~${day.workOffHhmm}</span>`
                : '';
            inner += `<span class="shift-badge"><span class="${shiftCls(day.workDayType)}">${day.shiftName}</span>${timePart}</span>`;
        }

        // 2. 실 출퇴근 시간
        if (day.record && (day.record.checkIn || day.record.checkOut)) {
            const ci = day.record.checkIn  || '--:--';
            const co = day.record.checkOut || '--:--';
            inner += `<span class="rec-badge">출 ${ci} / 퇴 ${co}</span>`;
        }

        // 3. 지각
        if (day.record && day.record.lateYn === 'Y') {
            inner += `<span class="late-badge">지각 ${day.record.lateMin}분</span>`;
        }

        // 4. 결근 (WORK일이고 과거 날짜이며 출근 기록 없고 승인된 휴가도 없는 경우)
        const early = earlyMin(day);
        if (early > 0) {
            inner += `<span class="early-badge">조출 ${early}분</span>`;
        }
        if (hasShiftMismatch(day)) {
            inner += `<span class="mismatch-badge">근태코드 불일치</span>`;
        }

        const hasApprovedLeave = (day.requests || []).some(
            r => r.requestCategory === 'LEAVE' && r.status === 'APPROVED'
        );
        if (day.workDayType === 'WORK' &&
                (!day.record || !day.record.checkIn) && !hasApprovedLeave) {
            inner += `<span class="absent-badge">결근</span>`;
        }

        // 5. 근태신청 (연장, 연차, 반차 등)
        (day.requests || []).forEach(r => {
            const typeLabel = r.requestWorkCode || catLabel(r.requestCategory);
            let timeStr = '';
            if (r.startTime || r.endTime) {
                const st = r.startTime || '--:--';
                const et = r.endTime   || '--:--';
                timeStr = `<span class="shift-time"> ${st}~${et}</span>`;
            }
            inner += `<span class="req-badge"><span class="${reqCls(r.status)}">${typeLabel} ${statusLabel(r.status)}</span>${timeStr}</span>`;
        });

        html += `<td class="${colCls} ${todCls} ${monthCls}" onclick="openModal('${day.workDate}')">${inner}</td>`;
        col++;
    });

    endRow();
    tbody.innerHTML = html;

    const totalMin = DAYS
        .filter(d => d.inCurrentMonth)
        .reduce((sum, d) => sum + (d.record && d.record.workMin ? d.record.workMin : 0), 0);
    const th = Math.floor(totalMin / 60), tm = totalMin % 60;
    const totalTxt = totalMin > 0
        ? th + '시간' + (tm > 0 ? ' ' + tm + '분' : '')
        : '-';
    document.getElementById('monthTotal').textContent = '월 총 근무시간: ' + totalTxt;
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

/* ───────── 근태 등록 모달 ───────── */
let modalDate = '';

function buildTimeOpts(selected) {
    let opts = '<option value="">--</option>';
    for (let h = 0; h < 24; h++) {
        for (let m of [0, 30]) {
            const v = String(h).padStart(2,'0') + ':' + String(m).padStart(2,'0');
            opts += `<option value="${v}"${v === selected ? ' selected' : ''}>${v}</option>`;
        }
    }
    return opts;
}

function buildWorkCodeOpts(cat) {
    const codes = WORK_CODES[cat] || [];
    return '<option value="">-- 선택 --</option>'
        + codes.map(c => `<option value="${c.v}">${c.l}</option>`).join('');
}

function onCategoryChange() {
    const cat = document.getElementById('mCategory').value;
    document.getElementById('mWorkCode').innerHTML = buildWorkCodeOpts(cat);
}

function openModal(date) {
    modalDate = date;
    document.getElementById('mDate').value      = date;
    document.getElementById('mReason').value    = '';
    document.getElementById('mStart').innerHTML = buildTimeOpts('');
    document.getElementById('mEnd').innerHTML   = buildTimeOpts('');
    onCategoryChange();
    document.getElementById('modalBg').classList.add('open');
}

function closeModal(e) {
    if (e.target === document.getElementById('modalBg')) closeModalDirect();
}
function closeModalDirect() {
    document.getElementById('modalBg').classList.remove('open');
}

async function doModalSave() {
    const category = document.getElementById('mCategory').value;
    const workCode = document.getElementById('mWorkCode').value;
    const start    = document.getElementById('mStart').value;
    const end      = document.getElementById('mEnd').value;
    const reason   = document.getElementById('mReason').value;

    if (!workCode) { showToast('신청근무를 선택하세요.', 'error'); return; }

    if (category === 'HOLIDAY') {
        const dayInfo = DAYS.find(d => d.workDate === modalDate);
        if (!dayInfo || (dayInfo.workDayType !== 'OFF' && dayInfo.workDayType !== 'HOLIDAY')) {
            showToast('휴일근무는 휴일(OFF/HOLIDAY)에만 신청할 수 있습니다.', 'error');
            return;
        }
    }
    if (workCode === '조출연장' && start && start >= '09:00') {
        showToast('조출연장은 시작시간이 09:00 이전이어야 합니다.', 'error'); return;
    }
    if (workCode === '연장' && end && end <= '18:00') {
        showToast('연장근무는 종료시간이 18:00 이후여야 합니다.', 'error'); return;
    }
    if (workCode === '조퇴' || workCode === '외출') {
        const dayInfo = DAYS.find(d => d.workDate === modalDate);
        if (dayInfo) {
            if (dayInfo.workOnHhmm && start && start < dayInfo.workOnHhmm) {
                showToast('시작 시간이 근무 시작 시간(' + dayInfo.workOnHhmm + ') 이전입니다.', 'error'); return;
            }
            if (dayInfo.workOffHhmm && end && end > dayInfo.workOffHhmm) {
                showToast('종료 시간이 근무 종료 시간(' + dayInfo.workOffHhmm + ') 이후입니다.', 'error'); return;
            }
        }
    }
    const activeReqs = (DAYS.find(d => d.workDate === modalDate)?.requests || [])
        .filter(r => ['DRAFT', 'SUBMITTED', 'APPROVED'].includes(r.status));
    if ((workCode === '연장' || workCode === '조출연장') &&
            activeReqs.some(r => r.requestWorkCode === '조퇴')) {
        showToast('조퇴 신청이 있는 날에는 연장근무를 신청할 수 없습니다.', 'error'); return;
    }
    if (workCode === '조퇴' &&
            activeReqs.some(r => r.requestWorkCode === '연장' || r.requestWorkCode === '조출연장')) {
        showToast('연장근무 신청이 있는 날에는 조퇴를 신청할 수 없습니다.', 'error'); return;
    }

    const dto = {
        empCode:         TARGET_EMP,
        workDate:        modalDate,
        requestCategory: category,
        requestWorkCode: workCode,
        startTime:       start || null,
        endTime:         end   || null,
        reason:          reason
    };

    const res  = await fetch('/attendance/request/save', {
        method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(dto)
    });
    const json = await res.json();
    if (!json.success) { showToast(json.message || '저장 실패', 'error'); return; }

    showToast('저장되었습니다.', 'success');
    closeModalDirect();
    setTimeout(() => location.reload(), 800);
}

function showToast(msg, type) {
    const t = document.getElementById('toast');
    t.textContent = msg; t.className = type; t.style.display = 'block';
    setTimeout(() => { t.style.display = 'none'; }, 3000);
}

initRecordGenerateDate();
renderCalendar();
