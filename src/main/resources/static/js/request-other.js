let tableData = [];

const shiftCodesData = Array.from(document.querySelectorAll('#shiftOptionsData span')).map(sp => ({
    shiftCode: sp.dataset.code,
    shiftName: sp.dataset.name,
    workMinutes: sp.dataset.workMin ? parseInt(sp.dataset.workMin, 10) : 0
}));

function buildShiftOptions(selected) {
    const spans = document.querySelectorAll('#shiftOptionsData span');
    let opts = '<option value="">-- 선택 --</option>';
    spans.forEach(sp => {
        const code = sp.dataset.code, name = sp.dataset.name;
        opts += '<option value="'+code+'"'+(code===selected?' selected':'')+'>'+name+'</option>';
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

function formatWorkMin(min) {
    if (min == null || min === 0) return '-';
    const h = Math.floor(min / 60), m = min % 60;
    if (h > 0 && m > 0) return h + '시간 ' + m + '분';
    if (h > 0) return h + '시간';
    return m + '분';
}

function formatDay(day) {
    if (day == null) return '-';
    const n = Number(day);
    if (!Number.isFinite(n)) return '-';
    return n.toFixed(5).replace(/\.?0+$/, '') + '일';
}

function absoluteMinute(timeType, time) {
    if (!time) return null;
    const [h, m] = time.split(':').map(Number);
    return (timeType === 'N1' ? 1440 : 0) + h * 60 + m;
}

function breakOverlapMin(row, startMin, endMin) {
    const overlap = (breakStart, breakEnd) => {
        if (!breakStart || !breakEnd) return 0;
        let bs = absoluteMinute('N0', breakStart);
        let be = absoluteMinute('N0', breakEnd);
        if (be < bs) be += 1440;
        if (endMin > 1440 && bs < startMin) {
            bs += 1440;
            be += 1440;
        }
        return Math.max(0, Math.min(endMin, be) - Math.max(startMin, bs));
    };
    return overlap(row.break1StartHhmm, row.break1EndHhmm)
        + overlap(row.break2StartHhmm, row.break2EndHhmm);
}

function isActiveRequest(state) {
    return state && ['DRAFT', 'SUBMITTED', 'APPROVED'].includes(state.status);
}

function isApprovedRequest(state) {
    return state && state.status === 'APPROVED';
}

function hasActiveOtherRequest(row) {
    return Object.values(row.requestsByWorkCode || {}).some(req =>
        req.existingRequestGroup === 'OTHER' && isActiveRequest(req)
    );
}

function categoryOfRequest(state) {
    if (!state || state.existingRequestGroup === 'OTHER') return 'OTHER';
    if (state.requestWorkCode === '연장' || state.requestWorkCode === '조출연장') return 'OVERTIME';
    if (state.requestWorkCode === '휴일근무') return 'HOLIDAY';
    return 'LEAVE';
}

function requestEffectMin(state) {
    const category = categoryOfRequest(state);
    const min = state.requestWorkMin || 0;
    if (category === 'OVERTIME' || category === 'HOLIDAY') return min;
    if (category === 'LEAVE') return -min;
    return 0;
}

function requestMatches(a, b) {
    if (!a || !b) return false;
    if (a.requestId != null && b.requestId != null) return String(a.requestId) === String(b.requestId);
    return a.requestWorkCode && a.requestWorkCode === b.requestWorkCode;
}

function activeGeneralRequests(row) {
    return Object.values((row && row.requestsByWorkCode) || {})
        .filter(req => isActiveRequest(req) && categoryOfRequest(req) !== 'OTHER');
}

function approvedGeneralRequests(row) {
    return Object.values((row && row.requestsByWorkCode) || {})
        .filter(req => isApprovedRequest(req) && categoryOfRequest(req) !== 'OTHER');
}

function requestRange(req) {
    const start = absoluteMinute(req.startTimeType || 'N0', req.startTime);
    let end = absoluteMinute(req.endTimeType || 'N0', req.endTime);
    if (start == null || end == null) return null;
    if (end < start) end += 1440;
    return {start, end};
}

function plannedRange(row) {
    const start = absoluteMinute('N0', row && row.shiftOnTime);
    let end = absoluteMinute('N0', row && row.shiftOffTime);
    if (start == null || end == null) return null;
    if (end <= start) end += 1440;
    return {start, end};
}

function recognizedActualWorkMin(row) {
    if (!row) return 0;

    const plan = plannedRange(row);
    const hasCheckIn = !!row.checkIn;
    const actualStart = hasCheckIn ? absoluteMinute('N0', row.checkIn) : null;
    let actualEnd = null;
    if (hasCheckIn && row.checkOut) {
        actualEnd = absoluteMinute('N0', row.checkOut);
        if (row.overnightYn === 'Y' || actualEnd < actualStart) actualEnd += 1440;
    } else if (hasCheckIn && plan) {
        actualEnd = plan.end;
    }

    let total = hasCheckIn ? 0 : (row.actualWorkMin || 0);
    if (hasCheckIn && actualStart != null && actualEnd != null && plan && (row.plannedWorkMin || 0) > 0) {
        const baseStart = Math.max(actualStart, plan.start);
        const baseEnd = Math.min(actualEnd, plan.end);
        if (baseEnd > baseStart) {
            total += Math.max(0, baseEnd - baseStart - breakOverlapMin(row, baseStart, baseEnd));
        }
    }

    const actual = hasCheckIn && row.checkOut && actualStart != null && actualEnd != null
        ? {start: actualStart, end: actualEnd}
        : null;
    approvedGeneralRequests(row).forEach(req => {
        const category = categoryOfRequest(req);
        if (category === 'OVERTIME' || category === 'HOLIDAY') {
            if (!actual) return;
            const reqRange = requestRange(req);
            if (!reqRange) return;
            let overlap = Math.max(0, Math.min(actual.end, reqRange.end) - Math.max(actual.start, reqRange.start));
            if ((req.requestWorkMin || 0) > 0) overlap = Math.min(overlap, req.requestWorkMin);
            total += overlap;
        } else if (category === 'LEAVE') {
            total -= (req.requestWorkMin || row.plannedWorkMin || 0);
        }
    });

    return Math.max(0, total);
}

function cumulativeEstimatedWorkMin(row) {
    return Math.max((row.shiftWorkMin || 0) + (row.activeWeeklyRequestEffectMin || 0), 0);
}

function renderTable(rows) {
    const checkAll = document.getElementById('checkAll');
    if (checkAll) checkAll.checked = false;
    const tbody = document.getElementById('reqTableBody');
    const workDate = document.getElementById('workDate').value;
    if (!rows || rows.length === 0) {
        tbody.innerHTML = '<tr><td colspan="16" class="no-data">조회된 인원이 없습니다.</td></tr>';
        tableData = [];
        return;
    }
    tableData = rows;
    tbody.innerHTML = rows.map((r, idx) => {
        const selectedWorkCode = r.requestWorkCode || '';
        const existing = existingRequestFor(r, selectedWorkCode) || r;
        const locked = (existing.status === 'SUBMITTED' || existing.status === 'APPROVED');
        const activeOtherRequest = existing.existingRequestGroup === 'OTHER' && isActiveRequest(existing);
        const blockedByOtherRequest = hasActiveOtherRequest(r) && !activeOtherRequest;
        const dis = (locked || blockedByOtherRequest) ? 'disabled' : '';
        const checkDis = blockedByOtherRequest ? 'disabled' : '';
        const reasonVal = (existing.reason||'').replace(/"/g,'&quot;');
        const reasonDetailVal = (existing.reasonDetail||'').replace(/"/g,'&quot;');
        const endDateVal = existing.endDate || r.endDate || workDate;
        const statusHtml = blockedByOtherRequest
            ? '<span class="badge badge-submitted">신청있음</span>'
            : statusBadge(existing.status);
        return '<tr data-idx="'+idx+'">'
            + '<td class="td-check" onclick="clickCheckCell(this)"><input type="checkbox" '+checkDis+' onclick="event.stopPropagation();toggleCheck(this)"></td>'
            + '<td>'+(r.empCode||'')+'</td>'
            + '<td>'+(r.empName||'')+'</td>'
            + '<td>'+(r.deptName||'')+'</td>'
            + '<td>'+(r.workPlanName||'-')+'</td>'
            + '<td>'+formatWorkMin(r.plannedWorkMin || 0)+'</td>'
            + '<td>'+(r.actualWorkName || r.actualWorkCode || '-')+'</td>'
            + '<td data-field="shiftWorkMin">'+formatWorkMin(cumulativeEstimatedWorkMin(r))+'</td>'
            + '<td>'+formatWorkMin(recognizedActualWorkMin(r))+'</td>'
            + '<td>'+formatDay(r.annualBalanceDay)+'</td>'
            + '<td><input type="date" data-field="endDate" min="'+workDate+'" value="'+endDateVal+'" '+dis+'></td>'
            + '<td><select data-field="requestWorkCode" '+dis+' onchange="onWorkCodeChange(this,'+idx+')">'+buildShiftOptions(selectedWorkCode)+'</select></td>'
            + '<td><input type="text" data-field="reason" value="'+reasonVal+'" placeholder="사유" '+dis+'></td>'
            + '<td><input type="text" data-field="reasonDetail" value="'+reasonDetailVal+'" placeholder="사유 상세 입력" '+dis+'></td>'
            + '<td data-field="status">'+statusHtml+'</td>'
            + '<td data-field="requesterName">'+(existing.requesterName||'')+'</td>'
            + '</tr>';
    }).join('');
}

function onWorkCodeChange(select, idx) {
    const r = tableData[idx];
    const existing = existingRequestFor(r, select.value);
    const state = existing || {};
    const locked = (state.status === 'SUBMITTED' || state.status === 'APPROVED');
    const tr = select.closest('tr');
    const reasonEl = tr.querySelector('[data-field="reason"]');
    const detailEl = tr.querySelector('[data-field="reasonDetail"]');
    const endDateEl = tr.querySelector('[data-field="endDate"]');
    reasonEl.value = state.reason || '';
    detailEl.value = state.reasonDetail || '';
    endDateEl.value = state.endDate || document.getElementById('workDate').value;
    reasonEl.disabled = locked;
    detailEl.disabled = locked;
    endDateEl.disabled = locked;
    tr.querySelector('[data-field="shiftWorkMin"]').textContent = formatWorkMin(cumulativeEstimatedWorkMin(r));
    tr.querySelector('[data-field="status"]').innerHTML = statusBadge(state.status);
    tr.querySelector('[data-field="requesterName"]').textContent = state.requesterName || '';
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
    toggleCheck(cb);
}

function toggleCheck(cb) {
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
        endDate:         tr.querySelector('[data-field="endDate"]').value || document.getElementById('workDate').value,
        requestCategory: 'OTHER',
        requestWorkCode: requestWorkCode,
        reason:          tr.querySelector('[data-field="reason"]').value,
        reasonDetail:    tr.querySelector('[data-field="reasonDetail"]').value,
        startTime:       null,
        endTime:         null
    };
}

async function confirmNonWorkDaysIncluded(dto) {
    const res = await fetch('/attendance/request/other/range-non-work-days', {
        method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(dto)
    });
    if (!res.ok) {
        const err = await res.json().catch(() => null);
        showToast((err && err.message) || '기간 확인 중 오류가 발생했습니다.', 'error');
        return false;
    }
    const json = await res.json();
    if (json.hasNonWorkDays) {
        showToast('기타근태 기간에는 휴무일/휴일을 포함할 수 없습니다.', 'error');
        return false;
    }
    return true;
}

async function doSearch() {
    const workDate      = document.getElementById('workDate').value;
    const deptEl        = document.getElementById('deptCode');
    const empEl         = document.getElementById('empCodeFilter');
    const workPlanEl    = document.getElementById('workPlanFilter');
    const deptCode      = deptEl ? deptEl.value : '';
    const empCode       = empEl  ? empEl.value  : '';
    const workPlanFilter= workPlanEl ? workPlanEl.value : '';
    if (!workDate) { showToast('근무일을 선택하세요.','error'); return; }
    const params = new URLSearchParams({workDate, deptCode, empCode, workPlanFilter});
    const res = await fetch('/attendance/request/other/search?'+params);
    if (!res.ok) { showToast('조회 중 오류가 발생했습니다.','error'); return; }
    renderTable(await res.json());
}

async function doSave() {
    const selected = getSelectedRows();
    if (selected.length === 0) { showToast('선택된 행이 없습니다.','error'); return; }
    for (const tr of selected) {
        const dto = rowToDto(tr);
        if (!dto.requestWorkCode) { showToast('변경 근무계획을 선택하세요.','error'); return; }
        if (!dto.endDate) { showToast('종료 날짜를 선택하세요.','error'); return; }
        if (dto.endDate < dto.workDate) { showToast('종료 날짜는 근무일보다 빠를 수 없습니다.','error'); return; }
        if (!await confirmNonWorkDaysIncluded(dto)) return;
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
        if (!requestId || !existing || existing.status === 'DRAFT' || existing.status === 'REJECTED') {
            if (!dto.requestWorkCode) { showToast('변경 근무계획을 선택하세요.','error'); return; }
            if (!dto.endDate) { showToast('종료 날짜를 선택하세요.','error'); return; }
            if (dto.endDate < dto.workDate) { showToast('종료 날짜는 근무일보다 빠를 수 없습니다.','error'); return; }
            if (!await confirmNonWorkDaysIncluded(dto)) return;
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
