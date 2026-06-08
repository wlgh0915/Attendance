/* ===== 사이드바 토글 ===== */
function toggleSidebar() {
    document.body.classList.toggle('sidebar-collapsed');
    localStorage.setItem('sidebarCollapsed', document.body.classList.contains('sidebar-collapsed'));
}

/* ===== 토스트 알림 ===== */
function showToast(message, type) {
    const container = document.getElementById('toast-container');
    if (!container) return;
    if (type === 'error') console.error('[toast]', message);

    const icon = type === 'success' ? '✓' : '✕';

    const card = document.createElement('div');
    card.className = 'toast-card toast-' + type;
    card.innerHTML =
        '<span class="toast-icon">' + icon + '</span>' +
        '<span class="toast-message">' + message + '</span>' +
        '<button class="toast-close" onclick="closeToast(this)" aria-label="닫기">×</button>';

    container.appendChild(card);
    card._toastTimer = setTimeout(function () {
        closeToastCard(card);
    }, 3000);
}

function closeToast(btn) {
    const card = btn.closest('.toast-card');
    closeToastCard(card);
}

function closeToastCard(card) {
    if (!card) return;
    if (card._toastTimer) {
        clearTimeout(card._toastTimer);
        card._toastTimer = null;
    }
    if (card.classList.contains('toast-hide')) return;
    card.classList.add('toast-hide');
    card.addEventListener('animationend', function () { card.remove(); });
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
});
