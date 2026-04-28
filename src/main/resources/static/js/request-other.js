let tableData = [];

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
    const map = {DRAFT:'badge-draft',SUBMITTED:'badge-submit',APPROVED:'badge-approved',REJECTED:'badge-rejected'};
    const lbl = {DRAFT:'미상신',SUBMITTED:'승인중',APPROVED:'승인완료',REJECTED:'반려'};
    const s = status||'DRAFT';
    return '<span class="badge '+(map[s]||'badge-draft')+'">'+(lbl[s]||s)+'</span>';
}

function renderTable(rows) {
    const checkAll = document.getElementById('checkAll');
    if (checkAll) checkAll.checked = false;
    const tbody = document.getElementById('reqTableBody');
    if (!rows || rows.length === 0) {
        tbody.innerHTML = '<tr><td colspan="10" class="no-data">조회된 인원이 없습니다.</td></tr>';
        tableData = [];
        return;
    }
    tableData = rows;
    tbody.innerHTML = rows.map((r, idx) => {
        const locked = (r.status === 'SUBMITTED' || r.status === 'APPROVED');
        const dis = locked ? 'disabled' : '';
        const reasonVal = (r.reason||'').replace(/"/g,'&quot;');
        const reasonDetailVal = (r.reasonDetail||'').replace(/"/g,'&quot;');
        return '<tr data-idx="'+idx+'">'
            + '<td class="td-check" onclick="clickCheckCell(this)"><input type="checkbox" onclick="event.stopPropagation();toggleCheck(this)"></td>'
            + '<td>'+(r.empCode||'')+'</td>'
            + '<td>'+(r.empName||'')+'</td>'
            + '<td>'+(r.deptName||'')+'</td>'
            + '<td>'+(r.workPlanName||'-')+'</td>'
            + '<td><select data-field="requestWorkCode" '+dis+'>'+buildShiftOptions(r.requestWorkCode)+'</select></td>'
            + '<td><input type="text" data-field="reason" value="'+reasonVal+'" placeholder="사유" '+dis+'></td>'
            + '<td><input type="text" data-field="reasonDetail" value="'+reasonDetailVal+'" placeholder="사유 상세 입력" '+dis+'></td>'
            + '<td>'+statusBadge(r.status)+'</td>'
            + '<td>'+(r.requesterName||'')+'</td>'
            + '</tr>';
    }).join('');
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
    return {
        requestId:       d.requestId||null,
        empCode:         d.empCode,
        deptCode:        d.deptCode,
        workDate:        document.getElementById('workDate').value,
        requestCategory: 'OTHER',
        requestWorkCode: tr.querySelector('[data-field="requestWorkCode"]').value,
        reason:          tr.querySelector('[data-field="reason"]').value,
        reasonDetail:    tr.querySelector('[data-field="reasonDetail"]').value,
        startTime:       null,
        endTime:         null
    };
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
    if (!deptCode) { showToast('부서를 선택하세요.','error'); return; }

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
        const requestId = tableData[parseInt(tr.dataset.idx)].requestId;
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
        let requestId = tableData[idx].requestId;
        if (!requestId) {
            const dto = rowToDto(tr);
            if (!dto.requestWorkCode) { showToast('변경 근무계획을 선택하세요.','error'); return; }
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
        const requestId = tableData[parseInt(tr.dataset.idx)].requestId;
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