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
let calcSeq = 0;

async function autoCalcWorkMin() {
    const seq = ++calcSeq;
    const ci = document.getElementById('mCheckIn').value;
    const co = document.getElementById('mCheckOut').value;
    if (!ci || !co) {
        document.getElementById('mWorkMin').value = '';
        return;
    }
    const overnightYn = document.getElementById('mOvernightYn').value;
    if (overnightYn !== 'Y' && co < ci) {
        document.getElementById('mWorkMin').value = '';
        return;
    }

    const dto = {
        empCode: TARGET_EMP,
        yyyymmdd: currentYmd,
        deptCode: SELECTED_DEPT,
        shiftCode: document.getElementById('mShiftCode').value.trim() || null,
        checkIn: ci,
        checkOut: co,
        overnightYn: overnightYn
    };
    try {
        const res = await fetch('/attendance/record/calculate', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(dto)
        });
        const json = await res.json();
        if (seq !== calcSeq) return;
        document.getElementById('mWorkMin').value = json.success ? json.workMin : '';
    } catch (e) {
        if (seq === calcSeq) document.getElementById('mWorkMin').value = '';
    }
}

/* ───────── 모달 열기 ───────── */
let currentYmd = '';

async function openModal(btn) {
    const d = btn.dataset;
    currentYmd = d.ymd;
    document.getElementById('modalTitle').textContent = d.has === 'true' ? '출퇴근 실적 수정' : '출퇴근 실적 등록';
    document.getElementById('mDate').value            = d.ymd.slice(0,4) + '-' + d.ymd.slice(4,6) + '-' + d.ymd.slice(6,8);
    document.getElementById('mCheckIn').value         = d.ci !== 'null' ? (d.ci  || '') : '';
    document.getElementById('mCheckOut').value        = d.co !== 'null' ? (d.co  || '') : '';
    document.getElementById('mOvernightYn').value     = d.on !== 'null' ? (d.on  || 'N') : 'N';
    document.getElementById('mWorkMin').value         = d.wm !== 'null' ? (d.wm  || '') : '';

    // 계획 근태코드: DB에 저장된 값이 있으면 사용, 없으면 부서 근무패턴에서 자동 조회
    if (d.shift && d.shift !== 'null') {
        document.getElementById('mShiftCode').value = d.shift;
    } else {
        try {
            const res     = await fetch(`/attendance/record/planned-shift?empCode=${TARGET_EMP}&yyyymmdd=${d.ymd}`);
            const planned = await res.json();
            document.getElementById('mShiftCode').value = planned.shiftCode || '';
        } catch (e) {
            document.getElementById('mShiftCode').value = '';
        }
    }

    autoCalcWorkMin();
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
    const checkIn = document.getElementById('mCheckIn').value;
    const checkOut = document.getElementById('mCheckOut').value;
    const overnightYn = document.getElementById('mOvernightYn').value;
    if (checkIn && checkOut && overnightYn !== 'Y' && checkOut < checkIn) {
        showToast('퇴근 시간이 출근 시간보다 빠르면 익일여부를 Y로 선택해야 합니다.', 'error');
        return;
    }
    const dto = {
        empCode:         TARGET_EMP,
        yyyymmdd:        currentYmd,
        deptCode:        SELECTED_DEPT,
        shiftCode:       document.getElementById('mShiftCode').value.trim()       || null,
        checkIn:         checkIn                                                  || null,
        checkOut:        checkOut                                                 || null,
        workMin:         wm ? parseInt(wm) : null,
        overnightYn:     overnightYn
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
