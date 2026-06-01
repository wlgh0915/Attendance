function toggleSubMenu(element) {
    element.parentElement.classList.toggle('active');
}

function toggleNavSection(header) {
    const group = header.closest('.nav-section-group');
    group.classList.toggle('closed');
    const arrow = header.querySelector('.section-arrow');
    arrow.textContent = group.classList.contains('closed') ? '▶' : '▼';
}