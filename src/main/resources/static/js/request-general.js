const WORK_CODES = {
    OVERTIME: [{value:'연장',label:'연장근로'},{value:'조출연장',label:'조출연장'}],
    HOLIDAY:  [{value:'휴일근무',label:'휴일근무'}],
    LEAVE:    [{value:'조퇴',label:'조퇴'},{value:'외근',label:'외근'},{value:'외출',label:'외출'},{value:'오전반차',label:'오전반차'},{value:'오후반차',label:'오후반차'}]
};

let currentCategory = 'OVERTIME';
let tableData = [];

// 시프트 코드별 시간 정보 (hidden div에서 파싱)
const shiftCodesData = Array.from(document.querySelectorAll('#shiftTimesData span')).map(sp => ({
    shiftCode: sp.dataset.code,
    shiftName: sp.dataset.name,
    workOnHhmm:  sp.dataset.on  || '',
    workOffHhmm: sp.dataset.off || '',
    workMinutes: sp.dataset.workMin ? parseInt(sp.dataset.workMin, 10) : 0
}));

function switchCategory(el) {
    document.querySelectorAll('.category-tab').forEach(t => t.classList.remove('active'));
    el.classList.add('active');
    currentCategory = el.dataset.cat;
    doSearch();
}

function buildTimeOptions(selected) {
    let opts = '<option value="">--</option>';
    for (let h = 0; h < 24; h++) {
        for (let m of [0, 30]) {
            const hh = String(h).padStart(2,'0'), mm = String(m).padStart(2,'0');
            const val = hh+':'+mm;
            opts += '<option value="'+val+'"'+(val===selected?' selected':'')+'>'+val+'</option>';
        }
    }
    return opts;
}

function buildDayTypeOptions(selected) {
    const v = selected || 'N0';
    return '<option value="N0"'+(v==='N0'?' selected':'')+'>당일</option>'
         + '<option value="N1"'+(v==='N1'?' selected':'')+'>익일</option>';
}

function buildWorkCodeOptions(category, selected) {
    let opts = '<option value="">-- 선택 --</option>';
    (WORK_CODES[category]||[]).forEach(c => {
        opts += '<option value="'+c.value+'"'+(c.value===selected?' selected':'')+'>'+c.label+'</option>';
    });
    return opts;
}

function statusBadge(status) {
    const map = {DRAFT:'badge-draft',SUBMITTED:'badge-submit',APPROVED:'badge-approved',REJECTED:'badge-rejected',CANCELED:'badge-rejected'};
    const lbl = {DRAFT:'미상신',SUBMITTED:'승인중',APPROVED:'승인완료',REJECTED:'반려'};
    const s = status||'DRAFT';
    if (s === 'CANCELED') return '<span class="badge badge-rejected">취소</span>';
    return '<span class="badge '+(map[s]||'badge-draft')+'">'+(lbl[s]||s)+'</span>';
}

function existingRequestFor(row, workCode) {
    return (row.requestsByWorkCode && workCode) ? row.requestsByWorkCode[workCode] : null;
}

function currentRequestForRow(tr, row) {
    const workCode = tr.querySelector('[data-field="requestWorkCode"]').value;
    return existingRequestFor(row, workCode);
}

function isEndAfterStart(startType, startTime, endType, endTime) {
    if (!startTime || !endTime) return true;
    const toMin = t => { const [h, m] = t.split(':').map(Number); return h * 60 + m; };
    const startMin = (startType === 'N1' ? 1440 : 0) + toMin(startTime);
    const endMin   = (endType   === 'N1' ? 1440 : 0) + toMin(endTime);
    return endMin > startMin;
}

function isSameDay(timeType) {
    return !timeType || timeType === 'N0';
}

function validateCheckIn(row) {
    if (!row.checkIn) {
        showToast('출근 기록이 없으면 일반 근태를 신청할 수 없습니다.', 'error');
        return false;
    }
    return true;
}

function formatWorkMin(min) {
    if (min == null || min === 0) return '-';
    const h = Math.floor(min / 60), m = min % 60;
    if (h > 0 && m > 0) return h + '시간 ' + m + '분';
    if (h > 0) return h + '시간';
    return m + '분';
}

// 근무코드에 따른 시간 필드 규칙 계산
function minutesBetween(startType, startTime, endType, endTime) {
    if (!startTime || !endTime) return 0;
    const toMin = t => { const [h, m] = t.split(':').map(Number); return h * 60 + m; };
    const startMin = (startType === 'N1' ? 1440 : 0) + toMin(startTime);
    const endMin = (endType === 'N1' ? 1440 : 0) + toMin(endTime);
    return Math.max(endMin - startMin, 0);
}

function absoluteMinute(timeType, time) {
    if (!time) return null;
    const [h, m] = time.split(':').map(Number);
    return (timeType === 'N1' ? 1440 : 0) + h * 60 + m;
}

function selectedWorkMin(tr, state) {
    if (state && state.requestWorkMin != null && state.status !== 'DRAFT') return state.requestWorkMin;
    return minutesBetween(
        tr.querySelector('[data-field="startTimeType"]').value,
        tr.querySelector('[data-field="startTime"]').value,
        tr.querySelector('[data-field="endTimeType"]').value,
        tr.querySelector('[data-field="endTime"]').value
    );
}

function isActiveRequest(state) {
    return state && ['DRAFT', 'SUBMITTED', 'APPROVED'].includes(state.status);
}

function hasActiveRequest(row, ...codes) {
    return codes.some(code => isActiveRequest(row.requestsByWorkCode && row.requestsByWorkCode[code]));
}

function hasOverlappingRequest(dto, row) {
    const start = absoluteMinute(dto.startTimeType, dto.startTime);
    const end = absoluteMinute(dto.endTimeType, dto.endTime);
    if (start == null || end == null) return false;
    return Object.values(row.requestsByWorkCode || {}).some(req => {
        if (!isActiveRequest(req) || req.requestWorkCode === dto.requestWorkCode) return false;
        if (requestEffectSign(dto.requestWorkCode) !== requestEffectSign(req.requestWorkCode)) return false;
        const reqStart = absoluteMinute(req.startTimeType || 'N0', req.startTime);
        const reqEnd = absoluteMinute(req.endTimeType || 'N0', req.endTime);
        if (reqStart == null || reqEnd == null) return false;
        return reqStart < end && start < reqEnd;
    });
}

function checkOvertimeLeaveConflict(dto, row) {
    return true;
}

function requestEffectSign(workCode) {
    if (workCode === '연장' || workCode === '조출연장' || workCode === '휴일근무') return 1;
    if (['조퇴', '외근', '외출', '전반차', '후반차', '오전반차', '오후반차'].includes(workCode)) return -1;
    return 0;
}

function isBoundedLeaveCode(workCode) {
    return ['조퇴', '외출', '전반차', '후반차', '오전반차', '오후반차'].includes(workCode);
}

function effectiveWorkRange(row) {
    const baseStart = absoluteMinute('N0', row.shiftOnTime);
    const baseEndTime = absoluteMinute('N0', row.shiftOffTime);
    if (baseStart == null || baseEndTime == null) return null;
    let start = baseStart;
    let end = baseEndTime <= baseStart ? baseEndTime + 1440 : baseEndTime;
    Object.values(row.requestsByWorkCode || {}).forEach(req => {
        if (!isActiveRequest(req)) return;
        if (req.requestWorkCode === '조출연장') {
            const reqStart = absoluteMinute(req.startTimeType || 'N0', req.startTime);
            if (reqStart != null && reqStart < start) start = reqStart;
        }
        if (req.requestWorkCode === '연장') {
            const reqEnd = absoluteMinute(req.endTimeType || 'N0', req.endTime);
            if (reqEnd != null && reqEnd > end) end = reqEnd;
        }
    });
    return {start, end};
}

function validateWithinEffectiveWorkTime(dto, row) {
    if (!isBoundedLeaveCode(dto.requestWorkCode)) return true;
    const range = effectiveWorkRange(row);
    if (!range) return true;
    const start = absoluteMinute(dto.startTimeType, dto.startTime);
    const end = absoluteMinute(dto.endTimeType, dto.endTime);
    if (start < range.start) {
        showToast('시작 시간이 유효 근무 시작 시간 이전입니다.', 'error');
        return false;
    }
    if (end > range.end) {
        showToast('종료 시간이 유효 근무 종료 시간 이후입니다.', 'error');
        return false;
    }
    return true;
}

function categoryOfRequest(state) {
    if (!state || state.existingRequestGroup === 'OTHER') return 'OTHER';
    if (state.requestWorkCode === '연장' || state.requestWorkCode === '조출연장') return 'OVERTIME';
    if (state.requestWorkCode === '휴일근무') return 'HOLIDAY';
    return 'LEAVE';
}

function requestEffectMin(category, min) {
    if (category === 'OVERTIME' || category === 'HOLIDAY') return min;
    if (category === 'LEAVE') return -min;
    return 0;
}

function cumulativeEstimatedWorkMin(row, tr, currentState) {
    const selectedCode = tr.querySelector('[data-field="requestWorkCode"]').value;
    let total = row.shiftWorkMin || 0;
    Object.entries(row.requestsByWorkCode || {}).forEach(([code, state]) => {
        if (!isActiveRequest(state) || code === selectedCode) return;
        total += requestEffectMin(categoryOfRequest(state), state.requestWorkMin || 0);
    });

    const selectedMin = selectedWorkMin(tr, currentState);
    total += requestEffectMin(currentCategory, selectedMin);
    return Math.max(total, 0);
}

function refreshEstimatedWorkMin(tr, row, state) {
    const cell = tr.querySelector('[data-field="shiftWorkMin"]');
    if (cell) cell.textContent = formatWorkMin(cumulativeEstimatedWorkMin(row, tr, state));
}

function validateWeeklyWorkLimit(tr, row, state) {
    const total = cumulativeEstimatedWorkMin(row, tr, state);
    if ((currentCategory === 'OVERTIME' || currentCategory === 'HOLIDAY') && total > 3120) {
        const over = total - 3120;
        showToast('주 52시간을 초과하여 신청할 수 없습니다. 초과 시간: '
            + Math.floor(over / 60) + '시간 ' + (over % 60) + '분', 'error');
        return false;
    }
    return true;
}

function savedEstimatedWorkMin(row, state, selectedWorkCode) {
    let total = row.shiftWorkMin || 0;
    Object.entries(row.requestsByWorkCode || {}).forEach(([code, req]) => {
        if (!isActiveRequest(req) || code === selectedWorkCode) return;
        total += requestEffectMin(categoryOfRequest(req), req.requestWorkMin || 0);
    });
    if (isActiveRequest(state)) {
        total += requestEffectMin(categoryOfRequest(state), state.requestWorkMin || 0);
    }
    return Math.max(total, 0);
}

function computeTimeState(category, code, r) {
    let startTypeDis = false, startDis = false, endTypeDis = false, endDis = false;
    let startType = r.startTimeType || 'N0', endType = r.endTimeType || 'N0';
    let startTime = r.startTime || '', endTime = r.endTime || '';

    if (!code) return {startTypeDis, startDis, endTypeDis, endDis, startType, endType, startTime, endTime};

    if (category === 'OVERTIME') {
        if (code === '연장' && r.shiftOffTime) {
            startType = 'N0';
            startTime = r.shiftOffTime;
            startTypeDis = startDis = true;
        } else if (code === '조출연장' && r.shiftOnTime) {
            endType = 'N0';
            endTime = r.shiftOnTime;
            endTypeDis = endDis = true;
        }
    } else if (category === 'LEAVE') {
        if (code === '오전반차' || code === '오후반차') {
            const shift = shiftCodesData.find(s => s.shiftCode === code || s.shiftName === code);
            if (shift && (shift.workOnHhmm || shift.workOffHhmm)) {
                startType = 'N0'; startTime = shift.workOnHhmm || startTime;
                endType   = 'N0'; endTime   = shift.workOffHhmm || endTime;
            }
            startTypeDis = startDis = endTypeDis = endDis = true;
        }
    }

    return {startTypeDis, startDis, endTypeDis, endDis, startType, endType, startTime, endTime};
}

function renderTable(rows) {
    const checkAll = document.getElementById('checkAll');
    if (checkAll) checkAll.checked = false;
    const tbody = document.getElementById('reqTableBody');
    if (!rows || rows.length === 0) {
        tbody.innerHTML = '<tr><td colspan="13" class="no-data">조회된 인원이 없습니다.</td></tr>';
        tableData = [];
        return;
    }
    tableData = rows;
    tbody.innerHTML = rows.map((r, idx) => {
        const selectedWorkCode = r.requestWorkCode || '';
        const existing = existingRequestFor(r, selectedWorkCode) || r;
        const locked = (existing.status === 'SUBMITTED' || existing.status === 'APPROVED');
        const disFull = locked ? 'disabled' : '';

        // 잠긴 행은 저장된 값 그대로, 아닌 경우 근무코드 규칙 적용
        const ts = locked
            ? { startTypeDis:false, startDis:false, endTypeDis:false, endDis:false,
                startType: existing.startTimeType||'N0', endType: existing.endTimeType||'N0',
                startTime: existing.startTime||'', endTime: existing.endTime||'' }
            : computeTimeState(currentCategory, selectedWorkCode, {...r, ...existing});

        const startTypeDis = locked || ts.startTypeDis ? 'disabled' : '';
        const startDis     = locked || ts.startDis     ? 'disabled' : '';
        const endTypeDis   = locked || ts.endTypeDis   ? 'disabled' : '';
        const endDis       = locked || ts.endDis       ? 'disabled' : '';

        const reasonVal       = (existing.reason||'').replace(/"/g,'&quot;');
        const reasonDetailVal = (existing.reasonDetail||'').replace(/"/g,'&quot;');

        return '<tr data-idx="'+idx+'">'
            + '<td class="td-check" onclick="clickCheckCell(this)"><input type="checkbox" onclick="event.stopPropagation();toggleCheck(this,'+idx+')"></td>'
            + '<td>'+(r.empCode||'')+'</td>'
            + '<td>'+(r.empName||'')+'</td>'
            + '<td>'+(r.deptName||'')+'</td>'
            + '<td>'+(r.workPlanName||'-')+'</td>'
            + '<td data-field="shiftWorkMin">'+formatWorkMin(savedEstimatedWorkMin(r, existing, selectedWorkCode))+'</td>'
            + '<td><select data-field="requestWorkCode" onchange="onWorkCodeChange(this,'+idx+')">'+buildWorkCodeOptions(currentCategory,selectedWorkCode)+'</select></td>'
            + '<td><input type="text" data-field="reason" value="'+reasonVal+'" placeholder="사유" '+disFull+'></td>'
            + '<td><input type="text" data-field="reasonDetail" value="'+reasonDetailVal+'" placeholder="사유 상세 입력" '+disFull+'></td>'
            + '<td><div style="display:flex;gap:3px;">'
            + '<select data-field="startTimeType" style="width:52px;" '+startTypeDis+' onchange="onTimeChange(this,'+idx+')">'+buildDayTypeOptions(ts.startType)+'</select>'
            + '<select data-field="startTime" style="flex:1;" '+startDis+' onchange="onTimeChange(this,'+idx+')">'+buildTimeOptions(ts.startTime)+'</select>'
            + '</div></td>'
            + '<td><div style="display:flex;gap:3px;">'
            + '<select data-field="endTimeType" style="width:52px;" '+endTypeDis+' onchange="onTimeChange(this,'+idx+')">'+buildDayTypeOptions(ts.endType)+'</select>'
            + '<select data-field="endTime" style="flex:1;" '+endDis+' onchange="onTimeChange(this,'+idx+')">'+buildTimeOptions(ts.endTime)+'</select>'
            + '</div></td>'
            + '<td data-field="status">'+statusBadge(existing.status)+'</td>'
            + '<td data-field="requesterName">'+(existing.requesterName||'')+'</td>'
            + '</tr>';
    }).join('');
}

// 근무코드 변경 시 시간 필드 잠금/해제
function onWorkCodeChange(select, idx) {
    const tr = select.closest('tr');
    const r  = tableData[idx];
    applyRequestState(tr, r, select.value);
}

function applyRequestState(tr, r, workCode) {
    const existing = existingRequestFor(r, workCode);
    const state = existing || {};
    const locked = (state.status === 'SUBMITTED' || state.status === 'APPROVED');
    const ts = locked
        ? { startTypeDis:false, startDis:false, endTypeDis:false, endDis:false,
            startType: state.startTimeType||'N0', endType: state.endTimeType||'N0',
            startTime: state.startTime||'', endTime: state.endTime||'' }
        : computeTimeState(currentCategory, workCode, {...r, ...state});

    const startTypeEl = tr.querySelector('[data-field="startTimeType"]');
    const startEl     = tr.querySelector('[data-field="startTime"]');
    const endTypeEl   = tr.querySelector('[data-field="endTimeType"]');
    const endEl       = tr.querySelector('[data-field="endTime"]');
    const reasonEl    = tr.querySelector('[data-field="reason"]');
    const detailEl    = tr.querySelector('[data-field="reasonDetail"]');

    reasonEl.value = state.reason || '';
    detailEl.value = state.reasonDetail || '';
    reasonEl.disabled = locked;
    detailEl.disabled = locked;
    startTypeEl.disabled = locked || ts.startTypeDis;
    startEl.disabled     = locked || ts.startDis;
    endTypeEl.disabled   = locked || ts.endTypeDis;
    endEl.disabled       = locked || ts.endDis;

    startTypeEl.value = ts.startType;
    startEl.value     = ts.startTime;
    endTypeEl.value   = ts.endType;
    endEl.value       = ts.endTime;
    tr.querySelector('[data-field="status"]').innerHTML = statusBadge(state.status);
    tr.querySelector('[data-field="requesterName"]').textContent = state.requesterName || '';
    refreshEstimatedWorkMin(tr, r, state);
}

function onTimeChange(select, idx) {
    const tr = select.closest('tr');
    const row = tableData[idx];
    const state = currentRequestForRow(tr, row) || null;
    refreshEstimatedWorkMin(tr, row, state);
}

function clickCheckAllCell() {
    const cb = document.getElementById('checkAll');
    if (!cb) return;
    cb.checked = !cb.checked;
    toggleAll(cb);
}

function toggleAll(cb) {
    document.querySelectorAll('#reqTableBody tr[data-idx]').forEach(tr => {
        const rowCb = tr.querySelector('input[type="checkbox"]');
        if (!rowCb || rowCb.disabled) return;
        rowCb.checked = cb.checked;
        tr.classList.toggle('selected', cb.checked);
    });
}

function clickCheckCell(td) {
    const cb = td.querySelector('input[type="checkbox"]');
    if (!cb || cb.disabled) return;
    cb.checked = !cb.checked;
    toggleCheck(cb, parseInt(td.closest('tr').dataset.idx));
}

function toggleCheck(cb, idx) {
    cb.closest('tr').classList.toggle('selected', cb.checked);
    syncCheckAll();
}

function syncCheckAll() {
    const checkAll = document.getElementById('checkAll');
    if (!checkAll) return;
    const enabled = Array.from(document.querySelectorAll('#reqTableBody tr[data-idx] input[type="checkbox"]:not([disabled])'));
    checkAll.checked = enabled.length > 0 && enabled.every(c => c.checked);
}

function getSelectedRows() {
    return Array.from(document.querySelectorAll('#reqTableBody tr.selected'));
}

function rowToDto(tr) {
    const idx = parseInt(tr.dataset.idx);
    const d = tableData[idx];
    const requestWorkCode = tr.querySelector('[data-field="requestWorkCode"]').value;
    const existing = currentRequestForRow(tr, d);
    return {
        requestId:       existing && existing.status !== 'REJECTED' ? existing.requestId : null,
        empCode:         d.empCode,
        deptCode:        d.deptCode,
        workDate:        document.getElementById('workDate').value,
        requestCategory: currentCategory,
        requestWorkCode: requestWorkCode,
        reason:          tr.querySelector('[data-field="reason"]').value,
        reasonDetail:    tr.querySelector('[data-field="reasonDetail"]').value,
        startTimeType:   tr.querySelector('[data-field="startTimeType"]').value,
        startTime:       tr.querySelector('[data-field="startTime"]').value,
        endTimeType:     tr.querySelector('[data-field="endTimeType"]').value,
        endTime:         tr.querySelector('[data-field="endTime"]').value
    };
}

async function doSearch() {
    const workDate = document.getElementById('workDate').value;
    const deptEl   = document.getElementById('deptCode');
    const empEl    = document.getElementById('empCodeFilter');
    const deptCode = deptEl ? deptEl.value : '';
    const empCode  = empEl  ? empEl.value  : '';
    if (!workDate) { showToast('근무일을 선택하세요.','error'); return; }
    if (!deptCode) { showToast('부서를 선택하세요.','error'); return; }

    const params = new URLSearchParams({requestCategory:currentCategory, workDate, deptCode, empCode});
    const res = await fetch('/attendance/request/general/search?'+params);
    if (!res.ok) { showToast('조회 중 오류가 발생했습니다.','error'); return; }
    renderTable(await res.json());
}

async function doSave() {
    const selected = getSelectedRows();
    if (selected.length === 0) { showToast('선택된 행이 없습니다.','error'); return; }
    for (const tr of selected) {
        const dto = rowToDto(tr);
        if (!dto.requestWorkCode) { showToast('신청근무를 선택하세요.','error'); return; }
        if (!validateCheckIn(tableData[parseInt(tr.dataset.idx)])) return;
        if (!dto.startTime || !dto.endTime) { showToast('시작/종료 시간을 선택하세요.','error'); return; }
        if (dto.requestWorkCode === '조출연장' && dto.startTime >= '09:00') {
            showToast('조출연장은 시작시간이 09:00 이전이어야 합니다.','error'); return;
        }
        if (dto.requestWorkCode === '연장' && isSameDay(dto.endTimeType) && dto.endTime <= '18:00') {
            showToast('연장근무는 종료시간이 18:00 이후여야 합니다.','error'); return;
        }
        if (!validateWithinEffectiveWorkTime(dto, tableData[parseInt(tr.dataset.idx)])) return;
        if (!checkOvertimeLeaveConflict(dto, tableData[parseInt(tr.dataset.idx)])) return;
        if (hasOverlappingRequest(dto, tableData[parseInt(tr.dataset.idx)])) {
            showToast('같은 시간대에 이미 다른 근태 신청이 있습니다.', 'error'); return;
        }
        if (!validateWeeklyWorkLimit(tr, tableData[parseInt(tr.dataset.idx)], currentRequestForRow(tr, tableData[parseInt(tr.dataset.idx)]))) return;
        if (!isEndAfterStart(dto.startTimeType, dto.startTime, dto.endTimeType, dto.endTime)) {
            showToast('종료 시간이 시작 시간보다 앞설 수 없습니다.','error'); return;
        }
        const res = await fetch('/attendance/request/save', {
            method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(dto)
        });
        const json = await res.json();
        if (!json.success) { showToast(json.message||'저장 실패','error'); return; }
        tableData[parseInt(tr.dataset.idx)].requestId = json.requestId;
    }
    showToast('저장되었습니다.','success');
    doSearch();
}

async function doDelete() {
    const selected = getSelectedRows();
    if (selected.length === 0) { showToast('선택된 행이 없습니다.','error'); return; }
    if (!confirm('선택한 근태신청을 삭제하시겠습니까?')) return;
    for (const tr of selected) {
        const requestId = rowToDto(tr).requestId;
        if (!requestId) { showToast('저장되지 않은 행은 삭제할 수 없습니다.','error'); return; }
        const res = await fetch('/attendance/request/delete', {
            method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({requestId})
        });
        const json = await res.json();
        if (!json.success) { showToast(json.message||'삭제 실패','error'); return; }
    }
    showToast('삭제되었습니다.','success');
    doSearch();
}

async function doSubmit() {
    const selected = getSelectedRows();
    if (selected.length === 0) { showToast('선택된 행이 없습니다.','error'); return; }
    for (const tr of selected) {
        const idx = parseInt(tr.dataset.idx);
        const dto = rowToDto(tr);
        let requestId = dto.requestId;
        const existing = currentRequestForRow(tr, tableData[idx]);
        if (!dto.requestWorkCode) { showToast('신청근무를 선택하세요.','error'); return; }
        if (!validateCheckIn(tableData[idx])) return;
        if (!dto.startTime || !dto.endTime) { showToast('시작/종료 시간을 선택하세요.','error'); return; }
        if (dto.requestWorkCode === '조출연장' && dto.startTime >= '09:00') {
            showToast('조출연장은 시작시간이 09:00 이전이어야 합니다.','error'); return;
        }
        if (dto.requestWorkCode === '연장' && isSameDay(dto.endTimeType) && dto.endTime <= '18:00') {
            showToast('연장근무는 종료시간이 18:00 이후여야 합니다.','error'); return;
        }
        if (!validateWithinEffectiveWorkTime(dto, tableData[parseInt(tr.dataset.idx)])) return;
        if (!checkOvertimeLeaveConflict(dto, tableData[parseInt(tr.dataset.idx)])) return;
        if (hasOverlappingRequest(dto, tableData[parseInt(tr.dataset.idx)])) {
            showToast('같은 시간대에 이미 다른 근태 신청이 있습니다.', 'error'); return;
        }
        if (!validateWeeklyWorkLimit(tr, tableData[parseInt(tr.dataset.idx)], currentRequestForRow(tr, tableData[parseInt(tr.dataset.idx)]))) return;
        if (!isEndAfterStart(dto.startTimeType, dto.startTime, dto.endTimeType, dto.endTime)) {
            showToast('종료 시간이 시작 시간보다 앞설 수 없습니다.','error'); return;
        }
        if (!requestId || !existing || existing.status === 'DRAFT' || existing.status === 'REJECTED') {
            const sr = await fetch('/attendance/request/save', {
                method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(dto)
            });
            const sj = await sr.json();
            if (!sj.success) { showToast(sj.message||'저장 실패','error'); return; }
            requestId = sj.requestId;
            tableData[idx].requestId = requestId;
        }
        const res = await fetch('/attendance/request/submit', {
            method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({requestId})
        });
        const json = await res.json();
        if (!json.success) { showToast(json.message||'상신 실패','error'); return; }
    }
    showToast('상신되었습니다.','success');
    doSearch();
}

async function doCancelSubmit() {
    const selected = getSelectedRows();
    if (selected.length === 0) { showToast('선택된 행이 없습니다.','error'); return; }
    if (!confirm('상신을 취소하시겠습니까?')) return;
    for (const tr of selected) {
        const requestId = rowToDto(tr).requestId;
        if (!requestId) { showToast('저장된 신청건만 상신취소할 수 있습니다.','error'); return; }
        const res = await fetch('/attendance/request/cancel-submit', {
            method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({requestId})
        });
        const json = await res.json();
        if (!json.success) { showToast(json.message||'상신취소 실패','error'); return; }
    }
    showToast('상신취소되었습니다.','success');
    doSearch();
}

function showToast(msg, type) {
    const t = document.getElementById('toast');
    t.textContent = msg; t.className = type; t.style.display = 'block';
    setTimeout(() => { t.style.display = 'none'; }, 3000);
}

document.addEventListener('DOMContentLoaded', doSearch);
