function toggleSubMenu(element) {
    // 클릭된 메뉴의 부모(li.has-sub)를 찾음
    const parent = element.parentElement;

    // active 클래스가 있으면 제거, 없으면 추가
    parent.classList.toggle('active');

    // 화살표 방향 변경을 위한 로직
    const arrow = element.querySelector('.arrow-icon');
    if (parent.classList.contains('active')) {
        arrow.innerText = '▲';
    } else {
        arrow.innerText = '▼';
    }
}