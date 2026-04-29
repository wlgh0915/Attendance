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
    const m = {DRAFT:'b-draft', SUBMITTED:'b-submit', APPROVED:'b-approved', REJECTED:'b-rejected'};
    return 'badge ' + (m[s] || 'b-draft');
}
function catLabel(c) {
    return {OVERTIME:'연장', HOLIDAY:'휴일', LEAVE:'휴가/조퇴', OTHER:'기타'}[c] || c;
}
function statusLabel(s) {
    return {DRAFT:'미상신', SUBMITTED:'승인중', APPROVED:'승인', REJECTED:'반려'}[s] || s;
}

function renderCalendar() {
    if (!DAYS.length) return;
    const firstCol = dowToCol(DAYS[0].dayOfWeekNum);
    const tbody = document.getElementById('calBody');
    let html = '', col = 0;

    const openRow  = () => { html += '<tr>'; col = 0; };
    const closeRow = () => {
        while (col < 7) { html += '<td class="empty"></td>'; col++; }
        html += '</tr>';
    };

    openRow();
    for (let i = 0; i < firstCol; i++) { html += '<td class="empty"></td>'; col++; }

    DAYS.forEach(day => {
        if (col === 7) { html += '</tr>'; openRow(); }
        const c      = dowToCol(day.dayOfWeekNum);
        const colCls = c === 0 ? 'col-sun' : c === 6 ? 'col-sat' : '';
        const todCls = day.workDate === today ? 'today' : '';
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

        // 4. 근태신청 (연장, 연차, 반차 등)
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

        html += `<td class="${colCls} ${todCls}" onclick="openModal('${day.workDate}')">${inner}</td>`;
        col++;
    });

    closeRow();
    tbody.innerHTML = html;
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

renderCalendar();