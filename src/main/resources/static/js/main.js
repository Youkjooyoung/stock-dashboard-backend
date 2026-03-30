const API = 'http://localhost:8081/api/stock';
const USER_API = 'http://localhost:8081/api/user';
let allData = [];
let detailChartInstance = null;
let watchlist = [];
const token = localStorage.getItem('token');

async function loadDashboard() {
    try {
        checkLoginStatus();
        if (token) await loadWatchlist();

        const res = await fetch(`${API}/prices`);
        allData = await res.json();

        if (!allData || allData.length === 0) {
            alert('데이터가 없습니다. 먼저 수집을 실행하세요.');
            return;
        }

        renderSummary(allData);
        renderVolumeChart(allData);
        renderPriceChart(allData);
        renderTable(allData);

        document.getElementById('update-time').textContent =
            '최종 업데이트: ' + new Date().toLocaleString();
    } catch (e) {
        console.error('데이터 로드 실패', e);
    }
}

function checkLoginStatus() {
    if (token) {
        document.getElementById('login-btn').style.display = 'none';
        document.getElementById('logout-btn').style.display = 'block';
        document.getElementById('user-email').textContent =
            localStorage.getItem('email') || '';
    }
}

function doLogout() {
    localStorage.removeItem('token');
    localStorage.removeItem('email');
    location.reload();
}

async function loadWatchlist() {
    try {
        const res = await fetch(`${USER_API}/watchlist`, {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        watchlist = await res.json();
    } catch (e) {
        watchlist = [];
    }
}

async function toggleWatchlist(itemId, btn) {
    if (!token) {
        alert('로그인이 필요합니다!');
        location.href = 'login.html';
        return;
    }
    const isWatched = watchlist.includes(itemId);
    const method = isWatched ? 'DELETE' : 'POST';
    await fetch(`${USER_API}/watchlist/${itemId}`, {
        method,
        headers: { 'Authorization': 'Bearer ' + token }
    });
    if (isWatched) {
        watchlist = watchlist.filter(id => id !== itemId);
        btn.textContent = '☆';
        btn.style.color = '#ccc';
    } else {
        watchlist.push(itemId);
        btn.textContent = '★';
        btn.style.color = '#f5a623';
    }
}

function renderSummary(data) {
    const sorted = [...data].sort((a, b) => b.trqu - a.trqu);
    document.getElementById('total-items').textContent = data.length + '개';
    document.getElementById('top-volume-nm').textContent = sorted[0].itmsNm || sorted[0].srtnCd;
    document.getElementById('top-volume').textContent = sorted[0].trqu.toLocaleString();
    document.getElementById('base-date').textContent = sorted[0].basDt || '-';
}

function renderVolumeChart(data) {
    const top10 = [...data].sort((a, b) => b.trqu - a.trqu).slice(0, 10);
    new Chart(document.getElementById('volumeChart'), {
        type: 'bar',
        data: {
            labels: top10.map(d => d.itmsNm || d.srtnCd),
            datasets: [{ label: '거래량', data: top10.map(d => d.trqu), backgroundColor: '#1a3c5e' }]
        },
        options: { responsive: true, plugins: { legend: { display: false } }, scales: { x: { ticks: { font: { size: 10 } } } } }
    });
}

function renderPriceChart(data) {
    const top10 = [...data].sort((a, b) => b.clpr - a.clpr).slice(0, 10);
    new Chart(document.getElementById('priceChart'), {
        type: 'bar',
        data: {
            labels: top10.map(d => d.itmsNm || d.srtnCd),
            datasets: [{ label: '종가', data: top10.map(d => d.clpr), backgroundColor: '#2196f3' }]
        },
        options: { responsive: true, plugins: { legend: { display: false } }, scales: { x: { ticks: { font: { size: 10 } } } } }
    });
}

function renderTable(data) {
    const tbody = document.getElementById('stock-table-body');
    tbody.innerHTML = '';
    data.forEach(d => {
        const open = d.mkp || 0;
        const close = d.clpr || 0;
        const rate = open > 0 ? ((close - open) / open * 100) : 0;
        const diff = close - open;
        const cls = rate > 0 ? 'up' : rate < 0 ? 'down' : 'zero';
        const sign = rate > 0 ? '▲' : rate < 0 ? '▼' : '-';
        const isWatched = watchlist.includes(d.itemId);
        const row = document.createElement('tr');
        row.style.cursor = 'pointer';
        row.innerHTML = `
            <td>${d.srtnCd || '-'}</td>
            <td>${d.itmsNm || '-'}</td>
            <td>${d.mrktCtg || 'KOSPI'}</td>
            <td>${open.toLocaleString()}</td>
            <td class="${cls}">${close.toLocaleString()}</td>
            <td>${(d.hipr || 0).toLocaleString()}</td>
            <td>${(d.lopr || 0).toLocaleString()}</td>
            <td>${(d.trqu || 0).toLocaleString()}</td>
            <td class="${cls}">${sign} ${Math.abs(diff).toLocaleString()} (${rate.toFixed(2)}%)</td>
            <td><button class="watch-btn" style="background:none;border:none;font-size:18px;cursor:pointer;color:${isWatched ? '#f5a623' : '#ccc'}">${isWatched ? '★' : '☆'}</button></td>`;
        row.querySelector('.watch-btn').addEventListener('click', (e) => {
            e.stopPropagation();
            toggleWatchlist(d.itemId, e.target);
        });
        row.addEventListener('click', () => openModal(d.srtnCd, d.itmsNm));
        tbody.appendChild(row);
    });
}

async function openModal(ticker, name) {
    document.getElementById('modal-title').textContent = name + ' (' + ticker + ') 종가 추이';
    document.getElementById('modal').style.display = 'flex';
    try {
        const res = await fetch(`${API}/prices/${ticker}`);
        const data = await res.json();
        const sorted = data.sort((a, b) => a.basDt.localeCompare(b.basDt));
        if (detailChartInstance) detailChartInstance.destroy();
        detailChartInstance = new Chart(document.getElementById('detailChart'), {
            type: 'line',
            data: {
                labels: sorted.map(d => d.basDt),
                datasets: [{ label: '종가', data: sorted.map(d => d.clpr), borderColor: '#1a3c5e', backgroundColor: 'rgba(26,60,94,0.1)', tension: 0.3, fill: true, pointRadius: 5 }]
            },
            options: { responsive: true, plugins: { legend: { display: false } } }
        });
    } catch (e) {
        console.error('상세 데이터 로드 실패', e);
    }
}

function closeModal() {
    document.getElementById('modal').style.display = 'none';
}

document.getElementById('search-input').addEventListener('input', function () {
    const keyword = this.value.trim().toLowerCase();
    const filtered = allData.filter(d =>
        (d.itmsNm || '').toLowerCase().includes(keyword) ||
        (d.srtnCd || '').includes(keyword)
    );
    renderTable(filtered);
});

loadDashboard();