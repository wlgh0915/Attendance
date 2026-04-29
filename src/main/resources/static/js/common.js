/* ===== 사이드바 토글 ===== */
function toggleSidebar() {
    document.body.classList.toggle('sidebar-collapsed');
    localStorage.setItem('sidebarCollapsed', document.body.classList.contains('sidebar-collapsed'));
}

/* ===== 토스트 알림 ===== */
function showToast(message, type) {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const icon = type === 'success' ? '✓' : '✕';

    const card = document.createElement('div');
    card.className = 'toast-card toast-' + type;
    card.innerHTML =
        '<span class="toast-icon">' + icon + '</span>' +
        '<span class="toast-message">' + message + '</span>' +
        '<button class="toast-close" onclick="closeToast(this)" aria-label="닫기">×</button>';

    container.appendChild(card);
}

function closeToast(btn) {
    const card = btn.closest('.toast-card');
    card.classList.add('toast-hide');
    card.addEventListener('animationend', function () { card.remove(); });
}

function setPendingApprovalCount(count) {
    const badge = document.getElementById('pendingApprovalBadge');
    if (!badge) return;

    const alert = badge.closest('.approval-alert');
    const value = Number(count) || 0;

    badge.dataset.count = value;
    badge.textContent = value > 99 ? '99+' : String(value);
    badge.classList.toggle('hidden', value <= 0);
    if (alert) alert.classList.toggle('has-alert', value > 0);
}

async function refreshPendingApprovalCount() {
    const badge = document.getElementById('pendingApprovalBadge');
    if (!badge) return;

    try {
        const res = await fetch('/attendance/approval/pending-count');
        if (!res.ok) return;
        const data = await res.json();
        setPendingApprovalCount(data.count);
    } catch (e) {
        // Header notification is non-critical; keep the server-rendered value.
    }
}

document.addEventListener('DOMContentLoaded', function () {
    /* 사이드바 상태 복원 */
    if (localStorage.getItem('sidebarCollapsed') === 'true') {
        document.body.classList.add('sidebar-collapsed');
    }

    /* 숨겨진 toast-data 요소를 읽어 토스트 표시 */
    document.querySelectorAll('.toast-data[data-message]').forEach(function (el) {
        var msg = el.getAttribute('data-message');
        var type = el.getAttribute('data-type') || 'success';
        if (msg) showToast(msg, type);
    });

    refreshPendingApprovalCount();
});
