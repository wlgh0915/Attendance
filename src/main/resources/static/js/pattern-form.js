const DAY_NAMES = ['일', '월', '화', '수', '목', '금', '토'];

let optionsTemplate = '';
let showWorkDayOnly = false;

function captureOptionsTemplate() {
    const firstSel = document.querySelector('#detailTable tbody tr.day-row select');
    if (firstSel) optionsTemplate = firstSel.innerHTML;
}

function getStartDate() {
    const input = document.querySelector('[data-role="startDate"]');
    if (!input || !input.value) return null;

    const date = new Date(input.value + 'T00:00:00');
    return Number.isNaN(date.getTime()) ? null : date;
}

function getPatternDays() {
    const typeEl = document.querySelector('[data-field="patternType"]');
    const unitEl = document.querySelector('[data-field="cycleUnit"]');
    const countEl = document.querySelector('[data-field="cycleCount"]');
    const type = typeEl ? typeEl.value : 'FIXED';
    const unit = unitEl ? unitEl.value : 'W';
    const count = Math.max(1, parseInt(countEl ? countEl.value : '1', 10) || 1);

    if (!type || type === 'FIXED') return 28;
    if (unit === 'W') return count * 7;
    return count * 4;
}

function getCycleSize() {
    const unitEl = document.querySelector('[data-field="cycleUnit"]');
    const countEl = document.querySelector('[data-field="cycleCount"]');
    const unit = unitEl ? unitEl.value : 'W';
    const count = Math.max(1, parseInt(countEl ? countEl.value : '1', 10) || 1);

    return unit === 'W' ? 7 : count;
}

function getCycleText() {
    const unitEl = document.querySelector('[data-field="cycleUnit"]');
    const countEl = document.querySelector('[data-field="cycleCount"]');
    const unit = unitEl ? unitEl.value : 'W';
    const count = Math.max(1, parseInt(countEl ? countEl.value : '1', 10) || 1);

    return unit === 'W' ? count + '주 단위' : count + '일 단위';
}

function insertWeekHeaders() {
    const tbody = document.querySelector('#detailTable tbody');
    if (!tbody) return;

    tbody.querySelectorAll('tr.week-header-row').forEach(function(row) {
        row.remove();
    });

    const rows = Array.from(tbody.querySelectorAll('tr.day-row'));
    const cycleSize = getCycleSize();
    const unitEl = document.querySelector('[data-field="cycleUnit"]');
    const unit = unitEl ? unitEl.value : 'W';
    const cycleLabel = unit === 'W' ? '주기' : '회차';

    if (rows.length <= cycleSize) return;

    rows.forEach(function(row, index) {
        if (index % cycleSize !== 0) return;

        const headerRow = document.createElement('tr');
        headerRow.className = 'week-header-row';
        headerRow.innerHTML = '<td colspan="3">' + (Math.floor(index / cycleSize) + 1) + cycleLabel + '</td>';
        row.parentNode.insertBefore(headerRow, row);
    });
}

function updateDayLabels() {
    const startDate = getStartDate();
    const cycleSize = getCycleSize();

    document.querySelectorAll('#detailTable tbody tr.day-row').forEach(function(row, index) {
        const label = row.querySelector('.day-text');
        if (!label) return;

        row.classList.remove('row-saturday', 'row-sunday');

        if (!startDate) {
            label.textContent = '-';
            label.className = 'day-text';
            return;
        }

        const date = new Date(startDate);
        date.setDate(date.getDate() + (index % cycleSize));

        const dayOfWeek = date.getDay();
        label.textContent = DAY_NAMES[dayOfWeek];
        label.className = 'day-text' + (dayOfWeek === 6 ? ' day-sat' : dayOfWeek === 0 ? ' day-sun' : '');

        if (dayOfWeek === 6) row.classList.add('row-saturday');
        if (dayOfWeek === 0) row.classList.add('row-sunday');
    });
}

function updateSummary() {
    const rows = document.querySelectorAll('#detailTable tbody tr.day-row').length;
    const startDate = getStartDate();
    const daysEl = document.getElementById('summaryDays');
    const cycleEl = document.getElementById('summaryCycle');
    const startEl = document.getElementById('summaryStartDay');

    if (daysEl) daysEl.textContent = rows + '일';
    if (cycleEl) cycleEl.textContent = getCycleText();
    if (startEl) startEl.textContent = startDate ? DAY_NAMES[startDate.getDay()] + '요일 시작' : '-';
}

function updateTable() {
    insertWeekHeaders();
    updateDayLabels();
    updateSummary();
    applyWorkDayFilter();
}

function toggleWorkDayFilter() {
    showWorkDayOnly = !showWorkDayOnly;

    const btn = document.getElementById('filterToggleBtn');
    if (btn) {
        btn.classList.toggle('active', showWorkDayOnly);
        btn.textContent = showWorkDayOnly ? '전체 표시' : '근무일만 표시';
    }

    applyWorkDayFilter();
}

function isOffDayRow(row) {
    const sel = row.querySelector('select');
    if (!sel) return false;
    if (!sel.value) return true;

    const opt = sel.querySelector('option[value="' + sel.value + '"]');
    if (!opt) return false;

    const workDayType = opt.dataset.wdt || '';
    return workDayType === 'OFF' || workDayType === 'HOLIDAY';
}

function copyFirstCycle() {
    const tbody = document.querySelector('#detailTable tbody');
    if (!tbody) return;

    const dayRows = Array.from(tbody.querySelectorAll('tr.day-row'));
    const cycleSize = getCycleSize();

    if (dayRows.length <= cycleSize) {
        alert('복사할 다음 주기가 없습니다.');
        return;
    }

    const hasCode = dayRows.slice(0, cycleSize).some(function(row) {
        const sel = row.querySelector('select');
        return sel && sel.value;
    });

    if (!hasCode) {
        alert('첫 주기에 근태코드를 먼저 입력해 주세요.');
        return;
    }

    for (let i = cycleSize; i < dayRows.length; i++) {
        const sourceSel = dayRows[i % cycleSize].querySelector('select');
        const targetSel = dayRows[i].querySelector('select');
        if (sourceSel && targetSel) targetSel.value = sourceSel.value;
    }

    applyWorkDayFilter();
}

function applyWorkDayFilter() {
    const tbody = document.querySelector('#detailTable tbody');
    if (!tbody) return;

    tbody.querySelectorAll('tr.day-row').forEach(function(row) {
        row.style.display = (showWorkDayOnly && isOffDayRow(row)) ? 'none' : '';
    });

    tbody.querySelectorAll('tr.week-header-row').forEach(function(header) {
        let next = header.nextElementSibling;
        let anyVisible = false;

        while (next && !next.classList.contains('week-header-row')) {
            if (next.classList.contains('day-row') && next.style.display !== 'none') {
                anyVisible = true;
                break;
            }
            next = next.nextElementSibling;
        }

        header.style.display = anyVisible ? '' : 'none';
    });
}

function rebuildDetailRows(days) {
    const tbody = document.querySelector('#detailTable tbody');
    if (!tbody) return;

    const saved = [];
    tbody.querySelectorAll('tr.day-row').forEach(function(row) {
        const sel = row.querySelector('select');
        saved.push(sel ? sel.value : '');
    });

    tbody.innerHTML = '';

    for (let i = 0; i < days; i++) {
        const tr = document.createElement('tr');
        tr.className = 'day-row';

        const td1 = document.createElement('td');
        td1.innerHTML = '<span class="day-index">' + (i + 1) + '</span>' +
            '<input type="hidden" name="details[' + i + '].seq" value="' + (i + 1) + '">';

        const td2 = document.createElement('td');
        td2.innerHTML = '<span class="day-text">-</span>';

        const sel = document.createElement('select');
        sel.name = 'details[' + i + '].shiftCode';
        sel.innerHTML = optionsTemplate;
        if (saved[i]) sel.value = saved[i];
        sel.addEventListener('change', function() {
            if (showWorkDayOnly) applyWorkDayFilter();
        });

        const td3 = document.createElement('td');
        td3.appendChild(sel);

        tr.appendChild(td1);
        tr.appendChild(td2);
        tr.appendChild(td3);
        tbody.appendChild(tr);
    }

    updateTable();
}

document.addEventListener('DOMContentLoaded', function() {
    captureOptionsTemplate();
    updateTable();

    const startDateInput = document.querySelector('[data-role="startDate"]');
    const typeEl = document.querySelector('[data-field="patternType"]');
    const unitEl = document.querySelector('[data-field="cycleUnit"]');
    const countEl = document.querySelector('[data-field="cycleCount"]');
    const copyBtn = document.getElementById('copyFirstCycleBtn');
    const filterBtn = document.getElementById('filterToggleBtn');

    if (startDateInput) {
        startDateInput.addEventListener('change', updateTable);
        startDateInput.addEventListener('input', updateTable);
    }

    if (typeEl) {
        typeEl.addEventListener('change', function() {
            if (this.value === 'FIXED') {
                if (unitEl) unitEl.value = 'W';
                if (countEl) countEl.value = '1';
            }
            rebuildDetailRows(getPatternDays());
        });
    }

    if (unitEl) {
        unitEl.addEventListener('change', function() {
            rebuildDetailRows(getPatternDays());
        });
    }

    if (countEl) {
        countEl.addEventListener('change', function() {
            rebuildDetailRows(getPatternDays());
        });
        countEl.addEventListener('input', function() {
            rebuildDetailRows(getPatternDays());
        });
    }

    if (copyBtn) copyBtn.addEventListener('click', copyFirstCycle);
    if (filterBtn) filterBtn.addEventListener('click', toggleWorkDayFilter);
});
