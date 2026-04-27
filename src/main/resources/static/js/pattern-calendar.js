/* SHIFT_NAMES, SHIFT_TIMES, PALETTE 는 calendar.html 인라인 스크립트에서 전역 선언됨 */

const colorMap = {};
let colorIdx = 0;

function getColor(code) {
    if (!colorMap[code]) {
        colorMap[code] = PALETTE[colorIdx % PALETTE.length];
        colorIdx++;
    }
    return colorMap[code];
}

document.addEventListener('DOMContentLoaded', function () {

    /* ── sessionStorage 토스트 (fetch 삭제 후 리로드 시) ── */
    const pendingToast = sessionStorage.getItem('calendarToast');
    if (pendingToast) {
        sessionStorage.removeItem('calendarToast');
        try {
            const t = JSON.parse(pendingToast);
            if (typeof showToast === 'function') showToast(t.message, t.type);
        } catch (e) {}
    }

    /* ── 색상 초기화 ── */
    Object.keys(SHIFT_NAMES).forEach(function (code) { getColor(code); });

    document.querySelectorAll('.shift-cell[data-shift]').forEach(function (cell) {
        cell.style.backgroundColor = getColor(cell.dataset.shift);
    });

    /* ── 범례 ── */
    const legendBody = document.getElementById('legendBody');
    const allCodes   = Object.keys(SHIFT_NAMES);
    if (allCodes.length === 0) {
        legendBody.innerHTML = '<div style="font-size:12px;color:#bdc3c7;">등록된 근태코드 없음</div>';
    } else {
        allCodes.forEach(function (code) {
            const color = getColor(code);
            const name  = SHIFT_NAMES[code] || code;
            const time  = SHIFT_TIMES[code]  || '';
            legendBody.innerHTML +=
                '<div class="legend-item">' +
                '<span class="legend-dot" style="background:' + color + '"></span>' +
                '<div class="legend-info">' +
                '<div class="legend-name">' + name + '</div>' +
                '<div class="legend-code">' + code + '</div>' +
                (time ? '<div class="legend-time">' + time + '</div>' : '') +
                '</div></div>';
        });
    }

    /* ── 월 선택기 ── */
    document.getElementById('monthPicker').addEventListener('change', function () {
        if (!this.value) return;
        const parts = this.value.split('-');
        location.href = '/pattern/calendar?year=' + parts[0] + '&month=' + parseInt(parts[1], 10);
    });

    /* ── 추가 버튼 ── */
    document.getElementById('btnAdd').addEventListener('click', function () {
        location.href = '/pattern/new';
    });

    /* ── 체크박스 ── */
    const checkAll  = document.getElementById('checkAll');
    const rowChecks = document.querySelectorAll('.row-check');
    const btnEdit   = document.getElementById('btnEdit');
    const btnDelete = document.getElementById('btnDelete');
    const selCount  = document.getElementById('selCount');

    function getCheckedCodes() {
        return Array.from(rowChecks)
            .filter(function (cb) { return cb.checked; })
            .map(function (cb) { return cb.closest('tr').dataset.patternCode; });
    }

    function updateToolbar() {
        const codes = getCheckedCodes();
        btnEdit.disabled   = codes.length !== 1;
        btnDelete.disabled = codes.length === 0;
        selCount.textContent = codes.length > 0 ? codes.length + '개 선택' : '';
        checkAll.checked       = rowChecks.length > 0 && codes.length === rowChecks.length;
        checkAll.indeterminate = codes.length > 0 && codes.length < rowChecks.length;
    }

    document.querySelectorAll('td.col-check').forEach(function (td) {
        td.addEventListener('click', function () {
            const cb = this.querySelector('.row-check');
            cb.checked = !cb.checked;
            cb.closest('tr').classList.toggle('row-selected', cb.checked);
            updateToolbar();
        });
    });

    rowChecks.forEach(function (cb) {
        cb.addEventListener('change', function () {
            cb.closest('tr').classList.toggle('row-selected', cb.checked);
            updateToolbar();
        });
    });

    checkAll.addEventListener('change', function () {
        rowChecks.forEach(function (cb) {
            cb.checked = checkAll.checked;
            cb.closest('tr').classList.toggle('row-selected', checkAll.checked);
        });
        updateToolbar();
    });

    /* ── 수정 버튼 ── */
    btnEdit.addEventListener('click', function () {
        const codes = getCheckedCodes();
        if (codes.length === 1) {
            location.href = '/pattern/edit/' + encodeURIComponent(codes[0]);
        }
    });

    /* ── 삭제 버튼 ── */
    btnDelete.addEventListener('click', async function () {
        const codes = getCheckedCodes();
        if (codes.length === 0) return;
        if (!confirm('선택한 패턴 ' + codes.length + '개를 삭제하시겠습니까?\n(' + codes.join(', ') + ')')) return;

        const failed = [];
        for (const code of codes) {
            try {
                await fetch('/pattern/delete', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: new URLSearchParams({ workPatternCode: code }).toString()
                });
            } catch (e) {
                failed.push(code);
            }
        }

        const success = codes.length - failed.length;
        if (failed.length > 0) {
            sessionStorage.setItem('calendarToast', JSON.stringify({
                type: 'error',
                message: failed.join(', ') + ' 패턴은 삭제할 수 없습니다. (부서에서 사용 중)'
            }));
        } else {
            sessionStorage.setItem('calendarToast', JSON.stringify({
                type: 'success',
                message: success + '개 패턴이 삭제되었습니다.'
            }));
        }
        location.reload();
    });
});