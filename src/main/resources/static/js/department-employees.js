document.addEventListener('DOMContentLoaded', function () {
    var selectAll = document.getElementById('selectAll');

    selectAll.addEventListener('change', function () {
        document.querySelectorAll('input[name="empCodes"]').forEach(function (cb) {
            cb.checked = selectAll.checked;
        });
    });

    document.querySelector('thead th.th-checkbox').addEventListener('click', function (e) {
        if (e.target === selectAll) return;
        selectAll.checked = !selectAll.checked;
        selectAll.dispatchEvent(new Event('change'));
    });

    document.querySelectorAll('tbody tr').forEach(function (row) {
        row.addEventListener('click', function (e) {
            if (e.target.type === 'checkbox') return;
            var cb = row.querySelector('input[name="empCodes"]');
            if (cb) cb.checked = !cb.checked;
        });
    });
});