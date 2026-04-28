/* ───────── 근태코드 로드 ───────── */
let shiftCodes = [];

async function loadShiftCodes() {
    const res  = await fetch('/attendance/record/shift-codes');
    shiftCodes = await res.json();
    const opts = '<option value="">-- 선택 --</option>'
        + shiftCodes.map(s => `<option value="${s.shiftCode}">${s.shiftCode} ${s.shiftName}</option>`).join('');
    document.getElementById('mShiftCode').innerHTML       = opts;
    document.getElementById('mActualShiftCode').innerHTML = opts;
}

loadShiftCodes();

/* ───────── 필터 변경 ───────── */
function onDeptChange(v) {
    location.href = '/attendance/record?ym=' + CURRENT_YM + '&deptCode=' + v;
}
function onEmpChange(v) {
    if (!v) return;
    const dept = IS_ADMIN ? document.getElementById('deptSelect').value : SELECTED_DEPT;
    location.href = '/attendance/record?ym=' + CURRENT_YM + '&deptCode=' + dept + '&empCode=' + v;
}

/* ───────── 실근무분 자동계산 ───────── */
function autoCalcWorkMin() {
    const ci = document.getElementById('mCheckIn').value;
    const co = document.getElementById('mCheckOut').value;
    if (!ci || !co) return;
    const ciMin = parseInt(ci.split(':')[0]) * 60 + parseInt(ci.split(':')[1]);
    let   coMin = parseInt(co.split(':')[0]) * 60 + parseInt(co.split(':')[1]);
    if (document.getElementById('mOvernightYn').value === 'Y') coMin += 1440;
    const wm = coMin - ciMin - 60; // 점심 1시간 차감
    if (wm > 0) document.getElementById('mWorkMin').value = wm;
}

/* ───────── 모달 열기 ───────── */
let currentYmd = '';

function openModal(btn) {
    const d = btn.dataset;
    currentYmd = d.ymd;
    document.getElementById('modalTitle').textContent      = d.has === 'true' ? '출퇴근 실적 수정' : '출퇴근 실적 등록';
    document.getElementById('mDate').value                 = d.ymd.slice(0,4) + '-' + d.ymd.slice(4,6) + '-' + d.ymd.slice(6,8);
    document.getElementById('mShiftCode').value            = (d.shift  && d.shift  !== 'null') ? d.shift  : '';
    document.getElementById('mActualShiftCode').value      = (d.actual && d.actual !== 'null') ? d.actual : '';
    document.getElementById('mCheckIn').value              = d.ci !== 'null' ? (d.ci  || '') : '';
    document.getElementById('mCheckOut').value             = d.co !== 'null' ? (d.co  || '') : '';
    document.getElementById('mOvernightYn').value          = d.on !== 'null' ? (d.on  || 'N') : 'N';
    document.getElementById('mWorkMin').value              = d.wm !== 'null' ? (d.wm  || '') : '';
    document.getElementById('modalBg').classList.add('open');
}

function closeModal(e) {
    if (e.target === document.getElementById('modalBg')) closeModalDirect();
}
function closeModalDirect() {
    document.getElementById('modalBg').classList.remove('open');
}

/* ───────── 저장 ───────── */
async function doSave() {
    const wm  = document.getElementById('mWorkMin').value;
    const dto = {
        empCode:         TARGET_EMP,
        yyyymmdd:        currentYmd,
        deptCode:        SELECTED_DEPT,
        shiftCode:       document.getElementById('mShiftCode').value.trim()       || null,
        actualShiftCode: document.getElementById('mActualShiftCode').value.trim() || null,
        checkIn:         document.getElementById('mCheckIn').value                || null,
        checkOut:        document.getElementById('mCheckOut').value               || null,
        workMin:         wm ? parseInt(wm) : null,
        overnightYn:     document.getElementById('mOvernightYn').value
    };
    const res  = await fetch('/attendance/record/save', {
        method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(dto)
    });
    const json = await res.json();
    if (!json.success) { showToast(json.message || '저장 실패', 'error'); return; }
    showToast('저장되었습니다.', 'success');
    closeModalDirect();
    setTimeout(() => location.reload(), 800);
}

/* ───────── 삭제 ───────── */
async function doDelete(ymd) {
    if (!confirm('해당 날짜의 출퇴근 실적을 삭제하시겠습니까?')) return;
    const res  = await fetch('/attendance/record/delete?empCode=' + TARGET_EMP + '&yyyymmdd=' + ymd, {
        method: 'POST'
    });
    const json = await res.json();
    if (!json.success) { showToast(json.message || '삭제 실패', 'error'); return; }
    showToast('삭제되었습니다.', 'success');
    setTimeout(() => location.reload(), 800);
}

/* ───────── 토스트 ───────── */
function showToast(msg, type) {
    const t = document.getElementById('toast');
    t.textContent = msg; t.className = type; t.style.display = 'block';
    setTimeout(() => { t.style.display = 'none'; }, 3000);
}