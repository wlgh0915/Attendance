let currentStatus = 'PENDING';
let currentCategory = 'OVERTIME';
let tableData = [];

function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, ch => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
    }[ch]));
}

function switchStatus(el) {
    document.querySelectorAll('.status-tab').forEach(t => t.classList.remove('active'));
    el.classList.add('active');
    currentStatus = el.dataset.status;
    toggleActionBar();
    doSearch();
}

function switchCategory(el) {
    document.querySelectorAll('.category-tab').forEach(t => t.classList.remove('active'));
    el.classList.add('active');
    currentCategory = el.dataset.cat;
    doSearch();
}

function toggleActionBar() {
    document.getElementById('actionBar').style.display = currentStatus === 'PENDING' ? 'flex' : 'none';
}

function reqStatusBadge(status) {
    const map = { DRAFT:'badge-draft', SUBMITTED:'badge-submitted', APPROVED:'badge-approved', REJECTED:'badge-rejected', CANCELED:'badge-rejected' };
    const lbl = { DRAFT:'미상신', SUBMITTED:'승인중', APPROVED:'승인완료', REJECTED:'반려', CANCELED:'취소' };
    const s = status || 'DRAFT';
    return '<span class="badge '+(map[s]||'badge-draft')+'">'+(lbl[s]||s)+'</span>';
}

function stepStatusBadge(status) {
    const map = { PENDING:'badge-pending', APPROVED:'badge-approved', REJECTED:'badge-rejected' };
    const lbl = { PENDING:'대기', APPROVED:'승인', REJECTED:'반려' };
    const s = status || 'PENDING';
    return '<span class="badge '+(map[s]||'badge-pending')+'">'+(lbl[s]||s)+'</span>';
}

function stepTypeName(type) {
    const map = { SUBMIT:'상신', APPROVE:'결재', CC:'참조' };
    return map[type] || type;
}

function requestTypeName(r) {
    if (r.reqGroup === 'OTHER') return '기타근태';
    return r.reqType || '';
}

function dayTypeLabel(type) {
    return type === 'N1' ? '익일' : '당일';
}

function startDisplay(r) {
    if (r.reqGroup === 'OTHER') return '-';
    if (!r.startTime) return '-';
    return dayTypeLabel(r.startTimeType) + ' ' + r.startTime;
}

function endDisplay(r) {
    if (r.reqGroup === 'OTHER') return r.changeShiftName || r.changeShiftCode || '-';
    if (!r.endTime) return '-';
    return dayTypeLabel(r.endTimeType) + ' ' + r.endTime;
}

function renderTable(rows) {
    const tbody = document.getElementById('apprTableBody');
    const checkAll = document.getElementById('checkAll');
    if (checkAll) {
        checkAll.checked = false;
        checkAll.disabled = currentStatus !== 'PENDING';
    }
    if (!rows || rows.length === 0) {
        tbody.innerHTML = '<tr><td colspan="13" class="no-data">조회된 내역이 없습니다.</td></tr>';
        tableData = [];
        return;
    }
    tableData = rows;
    tbody.innerHTML = rows.map((r, idx) => {
        const chk = currentStatus === 'PENDING'
            ? '<input type="checkbox" onclick="event.stopPropagation();toggleCheck(this)">'
            : '';
        return '<tr data-idx="'+idx+'">'
            + '<td class="td-check" onclick="clickCheckCell(this)">'+chk+'</td>'
            + '<td>'+escapeHtml(r.targetDate)+'</td>'
            + '<td>'+escapeHtml(r.targetEmpCode)+'</td>'
            + '<td>'+escapeHtml(r.targetEmpName)+'</td>'
            + '<td>'+escapeHtml(r.targetDeptName)+'</td>'
            + '<td>'+escapeHtml(requestTypeName(r))+'</td>'
            + '<td style="text-align:left">'+escapeHtml(r.reason)+'</td>'
            + '<td>'+escapeHtml(startDisplay(r))+'</td>'
            + '<td>'+escapeHtml(endDisplay(r))+'</td>'
            + '<td>'+reqStatusBadge(r.requestStatus)+'</td>'
            + '<td>'+escapeHtml(r.myDecisionAt || '-')+'</td>'
            + '<td style="text-align:left">'+escapeHtml(r.rejectReason || '-')+'</td>'
            + '<td><button class="btn-detail" data-request-id="'+escapeHtml(r.requestId)+'" onclick="event.stopPropagation();openDetail(this.dataset.requestId)">상세 보기</button></td>'
            + '</tr>';
    }).join('');
}

function clickCheckAllCell() {
    if (currentStatus !== 'PENDING') return;
    const cb = document.getElementById('checkAll');
    if (!cb) return;
    cb.checked = !cb.checked;
    toggleAll(cb);
}

function clickCheckCell(td) {
    if (currentStatus !== 'PENDING') return;
    const cb = td.querySelector('input[type="checkbox"]');
    if (!cb) return;
    cb.checked = !cb.checked;
    toggleCheck(cb);
    syncCheckAll();
}

function toggleCheck(cb) {
    cb.closest('tr').classList.toggle('selected', cb.checked);
    syncCheckAll();
}

function toggleAll(cb) {
    if (currentStatus !== 'PENDING') return;
    document.querySelectorAll('#apprTableBody tr[data-idx]').forEach(tr => {
        const rowCb = tr.querySelector('input[type="checkbox"]');
        if (!rowCb) return;
        rowCb.checked = cb.checked;
        tr.classList.toggle('selected', cb.checked);
    });
}

function syncCheckAll() {
    const checkAll = document.getElementById('checkAll');
    if (!checkAll) return;
    const rowChecks = Array.from(document.querySelectorAll('#apprTableBody tr[data-idx] input[type="checkbox"]'));
    checkAll.checked = rowChecks.length > 0 && rowChecks.every(cb => cb.checked);
}

function getSelectedIds() {
    return Array.from(document.querySelectorAll('#apprTableBody tr.selected'))
        .map(tr => tableData[parseInt(tr.dataset.idx)].requestId)
        .filter(Boolean);
}

async function doSearch() {
    const params = new URLSearchParams({
        stepStatus: currentStatus,
        category: currentCategory,
        fromDate: document.getElementById('fromDate').value,
        toDate: document.getElementById('toDate').value,
        empCode: document.getElementById('empCodeFilter').value
    });
    const res = await fetch('/attendance/approval/list?' + params);
    if (!res.ok) {
        showToast('조회 중 오류가 발생했습니다.', 'error');
        return;
    }
    renderTable(await res.json());
}

async function doBulkApprove() {
    const ids = getSelectedIds();
    if (ids.length === 0) {
        showToast('선택된 항목이 없습니다.', 'error');
        return;
    }
    if (!confirm(ids.length + '건을 승인하시겠습니까?')) return;
    const res = await fetch('/attendance/approval/approve', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ requestIds: ids })
    });
    const json = await res.json();
    if (!json.success) {
        showToast(json.message || '처리 중 오류가 발생했습니다.', 'error');
        return;
    }
    showToast('승인되었습니다.', 'success');
    doSearch();
}

function openRejectModal() {
    const ids = getSelectedIds();
    if (ids.length === 0) {
        showToast('선택된 항목이 없습니다.', 'error');
        return;
    }
    document.getElementById('rejectReasonInput').value = '';
    document.getElementById('rejectModal').classList.add('open');
}

function closeRejectModal() {
    document.getElementById('rejectModal').classList.remove('open');
}

async function doBulkReject() {
    const ids = getSelectedIds();
    const reason = document.getElementById('rejectReasonInput').value.trim();
    if (!reason) {
        showToast('반려 사유를 입력하세요.', 'error');
        return;
    }
    const res = await fetch('/attendance/approval/reject', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ requestIds: ids, rejectReason: reason })
    });
    const json = await res.json();
    closeRejectModal();
    if (!json.success) {
        showToast(json.message || '처리 중 오류가 발생했습니다.', 'error');
        return;
    }
    showToast('반려되었습니다.', 'success');
    doSearch();
}

async function openDetail(requestId) {
    const res = await fetch('/attendance/approval/detail?requestId=' + encodeURIComponent(requestId));
    if (!res.ok) {
        showToast('상세 조회 중 오류가 발생했습니다.', 'error');
        return;
    }
    const d = await res.json();

    const reqTypeName = escapeHtml(d.reqType || '-');
    const groupName = d.reqGroup === 'OTHER' ? '기타근태' : '일반근태';
    const statusLabel = { DRAFT:'미상신', SUBMITTED:'승인중', APPROVED:'승인완료', REJECTED:'반려', CANCELED:'취소' };

    let timeInfo = '';
    if (d.reqGroup === 'GENERAL') {
        const startLabel = dayTypeLabel(d.startTimeType) + ' ' + (d.startTime || '-');
        const endLabel   = dayTypeLabel(d.endTimeType)   + ' ' + (d.endTime   || '-');
        timeInfo = '<div class="lbl">시간</div><div class="val">'+escapeHtml(startLabel)+' ~ '+escapeHtml(endLabel)+'</div>';
    } else {
        timeInfo = '<div class="lbl">변경근무</div><div class="val">'+escapeHtml(d.changeShiftName || d.changeShiftCode || '-')+'</div>';
    }

    document.getElementById('detailInfo').innerHTML =
        '<div class="lbl">신청번호</div><div class="val">'+escapeHtml(d.requestId)+'</div>'
        + '<div class="lbl">근태구분</div><div class="val">'+groupName+' / '+reqTypeName+'</div>'
        + '<div class="lbl">근무일</div><div class="val">'+escapeHtml(d.targetDate)+'</div>'
        + '<div class="lbl">대상자</div><div class="val">'+escapeHtml(d.targetEmpName)+' ('+escapeHtml(d.targetEmpCode)+') / '+escapeHtml(d.targetDeptName)+'</div>'
        + '<div class="lbl">신청자</div><div class="val">'+escapeHtml(d.requesterEmpName)+' ('+escapeHtml(d.requesterEmpCode)+')</div>'
        + timeInfo
        + '<div class="lbl">사유</div><div class="val">'+escapeHtml(d.reason || '-')+'</div>'
        + '<div class="lbl">사유상세</div><div class="val">'+escapeHtml(d.reasonDetail || '-')+'</div>'
        + '<div class="lbl">신청상태</div><div class="val">'+escapeHtml(statusLabel[d.requestStatus] || d.requestStatus || '-')+'</div>';

    const chain = d.approvalChain || [];
    document.getElementById('chainBody').innerHTML = chain.length === 0
        ? '<tr><td colspan="6" style="padding:12px;color:#999;">결재선 정보 없음</td></tr>'
        : chain.map(s =>
            '<tr>'
            + '<td>'+escapeHtml(s.stepNo)+'</td>'
            + '<td>'+escapeHtml(stepTypeName(s.stepType))+'</td>'
            + '<td>'+escapeHtml(s.approverName || s.approverEmpCode || '-')+'</td>'
            + '<td>'+stepStatusBadge(s.status)+'</td>'
            + '<td>'+escapeHtml(s.decisionAt || '-')+'</td>'
            + '<td style="text-align:left;max-width:140px;">'+escapeHtml(s.rejectReason || '-')+'</td>'
            + '</tr>'
        ).join('');

    document.getElementById('detailModal').classList.add('open');
    document.body.classList.add('detail-drawer-open');
}

function closeDetailModal() {
    const modal = document.getElementById('detailModal');
    modal.classList.add('closing');
    document.body.classList.remove('detail-drawer-open');
    setTimeout(() => modal.classList.remove('open', 'closing'), 260);
}

document.addEventListener('click', function(e) {
    const rm = document.getElementById('rejectModal');
    if (e.target === rm) closeRejectModal();
});

function showToast(msg, type) {
    const t = document.getElementById('toast');
    t.textContent = msg;
    t.className = type;
    t.style.display = 'block';
    setTimeout(() => { t.style.display = 'none'; }, 3000);
}

document.addEventListener('DOMContentLoaded', function () {
    const today = new Date();
    const fromDate = new Date(today);
    const toDate = new Date(today);
    fromDate.setMonth(fromDate.getMonth() - 1);
    toDate.setMonth(toDate.getMonth() + 1);

    document.getElementById('fromDate').value = formatDate(fromDate);
    document.getElementById('toDate').value = formatDate(toDate);
    toggleActionBar();
    doSearch();
});

function formatDate(date) {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return y + '-' + m + '-' + d;
}
