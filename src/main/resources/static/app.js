// ==========================================
// 1. 전역 상태 및 유틸리티
// ==========================================
let currentLat = null;
let currentLon = null;
let recWeatherData = null;
let globalRecentLogs = [];
let globalAllLogsData = [];
let globalWardrobeData = [];
let globalWishlistData = [];

let isLoginMode = true;
let isSessionExpired = false;
let currentWardrobeTab = 'bottle';
let currentIsSample = false;
let isWardrobeReorderMode = false;
let wardrobeSortable = null;

let isWishFormOpen = false;
let isWishReorderMode = false;
let wishSortableInstance = null;
let currentWishPromotionId = null;

let crawledNotesData = null;
let crawledImageUrl = "";
let wishCrawledNotesData = null;
let wishCrawledImageUrl = "";

let summaryChartInstance = null;
let abortController = null;
let isScanning = false;
let wishPromoteAbort = null;

let detailReturnTo = 'wardrobe';
let currentRecommendationLog = null;
let radarChartInstance = null;

let currentSearchQuery = '';
let currentSeasonFilter = 'ALL';

const seasonDisplay = {
    'SPRING': { text: '🌸 봄', color: '#ec4899', border: 'rgba(236, 72, 153, 0.3)', bg: 'rgba(236, 72, 153, 0.05)' },
    'SUMMER': { text: '🌿 여름', color: '#10b981', border: 'rgba(16, 185, 129, 0.3)', bg: 'rgba(16, 185, 129, 0.05)' },
    'FALL': { text: '🍂 가을', color: '#d97706', border: 'rgba(217, 119, 6, 0.3)', bg: 'rgba(217, 119, 6, 0.05)' },
    'WINTER': { text: '❄️ 겨울', color: '#0ea5e9', border: 'rgba(14, 165, 233, 0.3)', bg: 'rgba(14, 165, 233, 0.05)' }
};

let crawledSeasonsData = [];
let wishCrawledSeasonsData = [];

let crawledSeasonStats = {};      // 💡 본품 크롤링 스탯
let wishCrawledSeasonStats = {};  // 💡 위시리스트 크롤링 스탯

async function fetchWithAuth(url, options = {}) {
    const token = localStorage.getItem('jwt_token');
    const headers = {
        ...options.headers,
        'Authorization': `Bearer ${token}`,
        'ngrok-skip-browser-warning': 'true'
    };

    const response = await fetch(url, { ...options, headers });

    if (response.status === 401) {
        if (!isSessionExpired) {
            isSessionExpired = true;
            alert("세션이 만료되었습니다. 다시 로그인해주세요.");
            localStorage.removeItem('jwt_token');
            window.location.reload();
        }
        throw new Error("Unauthorized");
    }
    return response;
}

function getWeatherIcon(weatherText) {
    if (!weatherText) return '❓';
    const w = weatherText.toLowerCase();
    if (w.includes('clear')) return '☀️';
    if (w.includes('cloud')) return '☁️';
    if (w.includes('rain') || w.includes('drizzle')) return '🌧️';
    if (w.includes('thunder') || w.includes('storm')) return '⛈️';
    if (w.includes('snow')) return '❄️';
    if (w.includes('mist') || w.includes('fog') || w.includes('haze')) return '🌫️';
    return '🌤️';
}

function renderNotesHtml(notes) {
    if (!notes || Object.keys(notes).length === 0) return '<div style="font-size:10px; color:var(--accent-color);">No notes recorded.</div>';
    let html = '';
    const categories = [
        { key: 'top', label: 'TOP' },
        { key: 'middle', label: 'MIDDLE' },
        { key: 'base', label: 'BASE' },
        { key: 'general', label: 'NOTES' }
    ];
    categories.forEach(cat => {
        if (notes[cat.key] && notes[cat.key].length > 0) {
            html += `<div class="note-category">${cat.label}</div>`;
            html += notes[cat.key].map(n => `<span class="note-chip">${n}</span>`).join('');
        }
    });
    return html;
}

function parseNotesPreview(notesRaw) {
    if (!notesRaw) return "No notes recorded.";
    try {
        const parsed = typeof notesRaw === 'string' ? JSON.parse(notesRaw) : notesRaw;
        const allNotes = [];
        ['top', 'middle', 'base', 'general'].forEach(k => {
            if (parsed[k]) allNotes.push(...parsed[k]);
        });
        return allNotes.length > 0 ? allNotes.join(', ') : "No notes recorded.";
    } catch (e) {
        return "No notes recorded.";
    }
}

// ==========================================
// 2. 인증 & 네비게이션
// ==========================================
function checkAuth() {
    const token = localStorage.getItem('jwt_token');
    if (token) {
        switchView('main', document.querySelector('.nav-item.main-tab'));
        initializeAppData();
    } else {
        document.querySelector('.bottom-nav').style.display = 'none';
        switchView('auth', null);
    }
}

function toggleAuthMode() {
    isLoginMode = !isLoginMode;
    document.getElementById('signup-fields').style.display = isLoginMode ? 'none' : 'block';
    document.getElementById('auth-submit-btn').innerText = isLoginMode ? 'LOGIN' : 'SIGN UP';
    document.getElementById('auth-toggle-btn').innerText = isLoginMode ? 'Switch to Sign Up' : 'Back to Login';
    document.getElementById('auth-status').innerText = '';
}

async function submitAuth() {
    const fields = {
        'auth-id': document.getElementById('auth-id'),
        'auth-pw': document.getElementById('auth-pw'),
        'auth-name': document.getElementById('auth-name'),
        'auth-loc': document.getElementById('auth-loc')
    };
    const noti = document.getElementById('auth-noti').checked;
    const statusMsg = document.getElementById('auth-status');

    Object.values(fields).forEach(el => el.classList.remove('error-border'));
    const requiredKeys = isLoginMode ? ['auth-id', 'auth-pw'] : ['auth-id', 'auth-pw', 'auth-name', 'auth-loc'];
    let hasError = false;

    for (const key of requiredKeys) {
        if (!fields[key].value.trim()) {
            fields[key].classList.add('error-border');
            if (!hasError) fields[key].focus();
            hasError = true;
        }
    }

    if (hasError) {
        statusMsg.innerText = "[ERROR] Required fields are missing.";
        return;
    }

    statusMsg.innerText = isLoginMode ? "Authenticating..." : "Creating account...";
    const endpoint = isLoginMode ? '/api/v1/auth/login' : '/api/v1/auth/signup';
    const payload = isLoginMode
        ? { userId: fields['auth-id'].value.trim(), rawPassword: fields['auth-pw'].value }
        : {
            userId: fields['auth-id'].value.trim(),
            rawPassword: fields['auth-pw'].value,
            name: fields['auth-name'].value.trim(),
            defaultLocation: fields['auth-loc'].value.trim(),
            notiEnabled: noti
        };

    try {
        const res = await fetchWithAuth(endpoint, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            if (isLoginMode) {
                const data = await res.json();
                localStorage.setItem('jwt_token', data.token);
                statusMsg.innerText = "[SUCCESS] Access granted.";
                setTimeout(() => {
                    document.querySelector('.bottom-nav').style.display = 'flex';
                    switchView('main', document.querySelector('.nav-item.main-tab'));
                    initializeAppData();
                }, 1000);
            } else {
                statusMsg.innerText = "[SUCCESS] Account created. Please login.";
                setTimeout(() => toggleAuthMode(), 1500);
            }
        } else {
            const errorData = await res.json();
            statusMsg.innerText = `[ERROR] ${errorData.error || 'Request failed'}`;
        }
    } catch (e) {
        statusMsg.innerText = "[ERROR] Network failure.";
    } finally {
        if (!btn.classList.contains('success')) {
            btn.innerText = btn.dataset.label || "LOGIN";   // ✅ 모드에 맞게
            btn.disabled = false;
        }
    }
}

function logout() {
    if (!confirm("로그아웃 하시겠습니까?")) return;
    localStorage.removeItem('jwt_token');
    window.location.reload();
}

function switchView(viewId, element) {
    document.querySelectorAll('.view-section').forEach(el => el.classList.remove('active'));
    document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));

    const targetView = document.getElementById('view-' + viewId);
    if (targetView) targetView.classList.add('active');
    if (element) element.classList.add('active');
}

// ==========================================
// 3. 날씨 및 실시간 추천
// ==========================================
async function fetchRealWeather(lat = null, lon = null) {
    const locText = document.getElementById('current-loc');
    const weatherInfo = document.getElementById('weather-info');
    weatherInfo.innerText = "fetching real-time data...";
    try {
        let url = "/api/v1/weather";
        if (lat !== null && lon !== null) url += `?lat=${lat}&lon=${lon}`;
        const response = await fetchWithAuth(url);
        if (response.ok) {
            const data = await response.json();
            recWeatherData = { weather: data.weather, temp: parseFloat(data.temp) };
            updateRecommendation();

            let buttonsHtml = `<button type="button" class="loc-btn loc-btn--sm" onclick="updateLocation()">update</button>`;
            if (lat !== null) buttonsHtml += `<button type="button" class="loc-btn loc-btn--sm" onclick="resetLocation()">default</button>`;

            const weatherIcon = getWeatherIcon(data.weather);
            const displayTemp = parseFloat(data.temp).toFixed(1);
            locText.innerHTML = `${data.location} ${buttonsHtml}`;
            weatherInfo.innerHTML = `<span style="font-size: 14px; margin-right: 4px;">${weatherIcon}</span>${data.weather} <span style="margin-left: 8px; color: var(--accent-color); font-weight: normal;">${displayTemp}°C / ${data.humidity}%</span>`;
        } else {
            weatherInfo.innerText = "[ERROR] Failed to load weather data.";
        }
    } catch (error) {
        weatherInfo.innerText = "[ERROR] Network failure.";
    }
}

function updateLocation() {
    document.getElementById('current-loc').innerHTML =
        `locating... <button type="button" class="loc-btn loc-btn--quiet is-busy" disabled>wait</button>`;
    if ("geolocation" in navigator) {
        navigator.geolocation.getCurrentPosition(
            (position) => {
                currentLat = position.coords.latitude;
                currentLon = position.coords.longitude;
                fetchRealWeather(currentLat, currentLon);
            },
            () => {
                alert("GPS access denied.");
                fetchRealWeather();
            },
            { enableHighAccuracy: true, maximumAge: 0 }
        );
    }
}

function resetLocation() {
    document.getElementById('current-loc').innerHTML =
        `locating... <button type="button" class="loc-btn loc-btn--quiet is-busy" disabled>wait</button>`;
    currentLat = null;
    currentLon = null;
    fetchRealWeather();
}

function updateRecommendation() {
    if (!recWeatherData || globalRecentLogs.length === 0) return;

    const currentW = (recWeatherData.weather || '').toLowerCase();
    const currentT = recWeatherData.tempMax !== undefined ? recWeatherData.tempMax : recWeatherData.temp;

    const isClear = currentW.includes('clear') || currentW.includes('sun');
    const isCloud = currentW.includes('cloud') || currentW.includes('haze') || currentW.includes('fog');
    const isRainSnow = currentW.includes('rain') || currentW.includes('snow') || currentW.includes('drizzle') || currentW.includes('storm');

    const matchWeather = (logW) => {
        if (!logW) return false;
        const w = logW.toLowerCase();
        if (isClear && (w.includes('clear') || w.includes('sun'))) return true;
        if (isCloud && (w.includes('cloud'))) return true;
        if (isRainSnow && (w.includes('rain') || w.includes('snow'))) return true;
        return false;
    };

    let candidates = globalRecentLogs.filter(l => matchWeather(l.weather) && l.temp !== null && Math.abs(parseFloat(l.temp) - currentT) <= 5);
    let reason = "🌡️ 현재 날씨와 온도에 가장 완벽한 픽";

    if (candidates.length === 0) {
        candidates = globalRecentLogs.filter(l => matchWeather(l.weather));
        reason = "☁️ 오늘 같은 날씨에 유독 자주 찾은 향수";
    }
    if (candidates.length === 0) {
        candidates = globalRecentLogs;
        reason = "👑 날씨 무관, 요즘 가장 손이 많이 가는 향수";
    }

    const counts = {};
    candidates.forEach(l => {
        if (l.perfumeName) counts[l.perfumeName] = (counts[l.perfumeName] || 0) + 1;
    });

    const bestName = Object.keys(counts).sort((a, b) => counts[b] - counts[a])[0];
    if (!bestName) return;

    const bestLog = candidates.find(l => l.perfumeName === bestName);
    currentRecommendationLog = bestLog;
    const imgTag = bestLog.imageUrl
        ? `<img src="${bestLog.imageUrl}" style="width: 50px; height: 70px; object-fit: cover; border-radius: 2px; border: 1px solid #e0e0dc;">`
        : `<div style="width: 50px; height: 70px; background: #f5f5f5; border: 1px solid #e0e0dc; border-radius: 2px; display:flex; align-items:center; justify-content:center; font-size:8px; color:var(--accent-color);">No Img</div>`;

    document.getElementById('recommendation-content').innerHTML = `
        ${imgTag}
        <div style="flex: 1; overflow: hidden;">
            <div style="font-size: 10px; color: #10b981; text-transform: uppercase; font-weight: bold;">RECOMMENDED →</div>
            <div style="font-weight: bold; font-size: 15px; color: var(--text-color); margin: 2px 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${bestLog.perfumeName}</div>
            <div style="font-size: 11px; color: var(--accent-color);">${reason}</div>
        </div>
    `;
    const recWidget = document.getElementById('recommendation-widget');
    recWidget.style.display = 'block';
    recWidget.classList.add('is-clickable');
    recWidget.onclick = openDetailFromRecommendation;
    recWidget.title = '향수 정보 보기';
}

// ==========================================
// 4. 착향 로그 및 모달 (기록 / 수정 / 수동)
// ==========================================
async function fetchRecentLogs() {
    const container = document.getElementById('recent-logs-container');
    try {
        const response = await fetchWithAuth(`/api/v1/logs/recent?limit=5&t=${Date.now()}`);
        if (response.ok) {
            const logs = await response.json();
            if (logs.length === 0) {
                container.innerHTML = `<div style="text-align: center; color: var(--accent-color); font-size: 11px; margin-top: 20px;">No records found.</div>`;
                return;
            }
            container.innerHTML = logs.map(log => renderLogItemHtml(log)).join('');
        } else {
            container.innerHTML = `<div style="text-align: center; color: var(--accent-color); font-size: 11px;">[ERROR] Failed to load.</div>`;
        }
    } catch (error) {
        container.innerHTML = `<div style="text-align: center; color: var(--accent-color); font-size: 11px;">[ERROR] Network issue.</div>`;
    }
}

function openDetailFromRecommendation() {
    if (!currentRecommendationLog) return;
    const pid = resolvePerfumeId(currentRecommendationLog);
    if (!pid) {
        alert(`'${currentRecommendationLog.perfumeName}'은(는) 현재 옷장에 없습니다.`);
        return;
    }
    openPerfumeDetail(pid, 'main');
}

function renderLogItemHtml(log) {
    registerLog(log);

    const imgTag = log.imageUrl
        ? `<img src="${log.imageUrl}" class="log-thumb" alt="thumb">`
        : `<div class="log-thumb">No Img</div>`;

    const shortDate = log.date ? log.date.split('T')[0] : 'N/A';
    const tempHum = [log.temp ? `${log.temp}°C` : '', log.humidity ? `${log.humidity}%` : ''].filter(Boolean).join(' / ');
    const weatherIcon = getWeatherIcon(log.weather);

    let rateHtml = (log.rate && log.rate !== 'null' && log.rate > 0)
        ? `<div style="color:#f59e0b; font-size:12px; font-weight:bold; margin-top:5px; letter-spacing:2px;">⭐ ${parseFloat(log.rate).toFixed(1)}</div>`
        : `<div style="display:inline-block; padding:4px 8px; margin: 5px 4px 4px 4px; background-color:rgba(244, 63, 94, 0.1); color:#f43f5e; border-radius:4px; font-size:10px; font-weight:bold; animation:badge-pulse 2s infinite; border: 1px solid; align-self: flex-start;">✍️ 터치해서 별점 남기기</div>`;

    const toDetail = `onclick="event.stopPropagation(); openDetailFromLog('${log.pageId}')"`;

    return `
        <div class="log-item" onclick="openEditLogById('${log.pageId}')">
            <div class="log-left">
                <div class="log-thumb-link" ${toDetail} title="향수 정보 보기">
                    ${imgTag}
                </div>
                <div class="log-details">
                    <div class="log-perfume">
                        <span class="log-name-link" ${toDetail} title="향수 정보 보기">${log.perfumeName}</span>
                    </div>
                    <div class="log-date">[${shortDate}]</div>
                    ${rateHtml}
                </div>
            </div>
            <div class="log-weather">
                <span style="font-size: 14px;">${weatherIcon}</span> ${log.weather || 'Unknown'}<br>${tempHum}
            </div>
        </div>
    `;
}

function openEditLog(pageId, name, date, weather, temp, humidity, rate, comment) {
    const delBtn = document.getElementById('edit-delete-btn');
    const subBtn = document.getElementById('edit-submit-btn');
    delBtn.innerText = "이 기록 삭제";
    subBtn.innerText = "UPDATE";
    delBtn.disabled = false;
    subBtn.disabled = false;
    delBtn.classList.remove('success');
    subBtn.classList.remove('success');
    document.getElementById('edit-status').innerText = "";
    document.getElementById('edit-page-id').value = pageId;
    document.getElementById('edit-perfume-name').innerText = name;
    document.getElementById('edit-date').innerText = `[${date}]`;
    document.getElementById('edit-weather').value = weather || 'Clear';
    document.getElementById('edit-temp').value = temp || '';
    document.getElementById('edit-humidity').value = humidity || '';
    document.getElementById('edit-rate').value = rate && rate !== 'null' && rate !== 'undefined' ? rate : '';
    document.getElementById('edit-comment').value = comment && comment !== 'null' ? comment : '';
    document.getElementById('edit-status').innerText = "";

    updateStarUI(rate);
    switchView('edit-log', null);
}

function cancelEditLog() {
    switchView('main', document.querySelector('.nav-item.main-tab'));
}

async function submitEditLog() {
    const pageId = document.getElementById('edit-page-id').value;
    const weather = document.getElementById('edit-weather').value;
    const temp = document.getElementById('edit-temp').value;
    const humidity = document.getElementById('edit-humidity').value;
    const statusMsg = document.getElementById('edit-status');
    const submitBtn = document.getElementById('edit-submit-btn');
    const rate = document.getElementById('edit-rate').value;
    const comment = document.getElementById('edit-comment').value.trim();

    statusMsg.innerText = "Updating Notion DB...";
    submitBtn.innerText = "Updating...";
    submitBtn.disabled = true

    try {
        const res = await fetchWithAuth(`/api/v1/logs/${pageId}`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                weather: weather,
                temp: temp ? parseFloat(temp) : null,
                humidity: humidity ? parseFloat(humidity) : null,
                rate: rate ? parseFloat(rate) : null,
                comment: comment || null
            })
        });

        if (res.ok) {
            submitBtn.classList.add('success');
            submitBtn.innerText = "UPDATED";
            statusMsg.innerText = "[SUCCESS] Log modified.";
            setTimeout(() => {
                submitBtn.classList.remove('success');
                submitBtn.innerText = "Update Log";
                submitBtn.disabled = false
                switchView('main', document.querySelector('.nav-item.main-tab'));
                refreshAfterLogChange();
            }, 1500);
        } else {
            statusMsg.innerText = "[ERROR] Update failed.";
            submitBtn.innerText = "Update Log";
            submitBtn.disabled = false
        }
    } catch (e) {
        statusMsg.innerText = "[ERROR] Network failure.";
        submitBtn.innerText = "Update Log";
        submitBtn.disabled = false
    }
}

async function deleteLog() {
    if (!confirm("진짜로 이 착향 기록을 삭제하시겠습니까?")) return;
    const pageId = document.getElementById('edit-page-id').value;
    const statusMsg = document.getElementById('edit-status');
    const delBtn = document.getElementById('edit-delete-btn');
    const updateBtn = document.getElementById('edit-submit-btn');

    const DEL_LABEL = "이 기록 삭제";

    statusMsg.innerText = "Deleting from Notion...";
    delBtn.innerText = "Wait...";
    delBtn.disabled = true;
    updateBtn.disabled = true;

    try {
        const res = await fetchWithAuth(`/api/v1/logs/${pageId}`, { method: "DELETE" });
        if (res.ok) {
            delBtn.classList.add('success');
            delBtn.innerText = "DELETED";
            statusMsg.innerText = "[SUCCESS] Log deleted.";
            setTimeout(() => {
                delBtn.classList.remove('success');
                delBtn.innerText = DEL_LABEL;
                delBtn.disabled = false;
                updateBtn.disabled = false;
                statusMsg.innerText = "";
                switchView('main', document.querySelector('.nav-item.main-tab'));
                refreshAfterLogChange();
            }, 1500);
        } else {
            statusMsg.innerText = "[ERROR] Delete failed.";
            delBtn.innerText = DEL_LABEL;
            delBtn.disabled = false;
            updateBtn.disabled = false;
        }
    } catch (e) {
        statusMsg.innerText = "[ERROR] Network failure.";
        delBtn.innerText = DEL_LABEL;
        delBtn.disabled = false;
        updateBtn.disabled = false;
    }
}

// 수동 기록 폼 - 향수 리스트 불러오기 (본품 -> 샘플 순서 및 꼬리표 적용)
async function fetchPerfumeList() {
    try {
        const res = await fetchWithAuth("/api/v1/perfumes/list");
        if (res.ok) {
            const perfumes = await res.json();

            // 1. 본품과 샘플 분리 (백엔드에서 이미 옷장 순서대로 내려오므로 상대적 순서는 자동 유지됨)
            const bottles = perfumes.filter(p => !p.isSample);
            const samples = perfumes.filter(p => p.isSample);

            // 2. 본품 먼저, 그 다음 샘플 순으로 배열 합치기
            const sortedPerfumes = [...bottles, ...samples];

            const selectBox = document.getElementById('manual-perfume-select');
            selectBox.innerHTML = '<option value="">-- Choose a perfume --</option>' +
                sortedPerfumes.map(p => {
                    // 3. 샘플일 경우 이름 뒤에 (샘플) 라벨 추가
                    const label = p.isSample ? ' (샘플)' : '';
                    return `<option value="${p.id}">${p.name}${label}</option>`;
                }).join('');
        }
    } catch (e) {
        console.error("Perfume list load failed");
    }
}

async function submitManualLog() {

    const perfumeId = document.getElementById('manual-perfume-select').value;
    const manualDate = document.getElementById('manual-date').value;
    const mTemp = document.getElementById('manual-temp').value;
    const mHum = document.getElementById('manual-hum').value;
    const statusMsg = document.getElementById('manual-status');
    const submitBtn = document.getElementById('manual-submit-btn');

    if (!perfumeId) {
        statusMsg.innerText = "[ERROR] 향수를 선택해주세요.";
        return;
    }
    // 💡 날짜 필수 검증
    if (!manualDate) {
        statusMsg.innerText = "[ERROR] 날짜를 지정해주세요.";
        return;
    }

    statusMsg.innerText = "Saving log to Notion...";
    submitBtn.innerText = "Saving...";

    try {
        const res = await fetchWithAuth("/api/v1/logs/manual", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                perfumeId: perfumeId,
                date: manualDate || null,
                lat: currentLat,
                lon: currentLon,
                useCurrentTemp: document.getElementById('manual-use-current-temp')?.checked || false,
                temp: mTemp ? parseFloat(mTemp) : null,
                humidity: mHum ? parseFloat(mHum) : null
            })
        });

        if (res.ok) {
            submitBtn.classList.add('success');
            submitBtn.innerText = "LOGGED";
            statusMsg.innerText = "[SUCCESS] Manual log saved.";
            setTimeout(() => {
                submitBtn.classList.remove('success');
                submitBtn.innerText = "Save Log";
                statusMsg.innerText = "";
                document.getElementById('manual-date').value = '';
                switchView('main', document.querySelector('.nav-item.main-tab'));
                refreshAfterLogChange();
            }, 1500);
        } else {
            statusMsg.innerText = "[ERROR] Failed to save log.";
            submitBtn.innerText = "Save Log";
        }
    } catch (e) {
        statusMsg.innerText = "[ERROR] Network failure.";
        submitBtn.innerText = "Save Log";
    }
}

// 전체 로그 검색
async function openAllLogsView() {
    switchView('all-logs', null);
    const container = document.getElementById('all-logs-container');
    container.innerHTML = `<div style="text-align: center; color: var(--accent-color); font-size: 11px; margin-top: 20px;">loading...</div>`;
    clearSearchDates();

    try {
        const res = await fetchWithAuth(`/api/v1/logs/recent?limit=100&t=${Date.now()}`);
        if (res.ok) {
            globalAllLogsData = await res.json();
            renderAllLogs();
        } else {
            container.innerHTML = `<div style="text-align: center; color: var(--accent-color); font-size: 11px;">[ERROR] Failed to load logs.</div>`;
        }
    } catch (e) {
        container.innerHTML = `<div style="text-align: center; color: var(--accent-color); font-size: 11px;">[ERROR] Network issue.</div>`;
    }
}

function clearSearchDates() {
    document.getElementById('search-start-date').value = '';
    document.getElementById('search-end-date').value = '';
    renderAllLogs();
}

function renderAllLogs() {
    const container = document.getElementById('all-logs-container');
    const startDate = document.getElementById('search-start-date').value;
    const endDate = document.getElementById('search-end-date').value;

    let filteredLogs = globalAllLogsData;
    if (startDate || endDate) {
        filteredLogs = globalAllLogsData.filter(log => {
            if (!log.date) return false;
            const logDate = log.date.split('T')[0];
            if (startDate && endDate) return logDate >= startDate && logDate <= endDate;
            if (startDate) return logDate >= startDate;
            if (endDate) return logDate <= endDate;
            return true;
        });
    }

    if (filteredLogs.length === 0) {
        container.innerHTML = `<div style="text-align: center; color: var(--accent-color); font-size: 11px; margin-top: 20px;">조건에 맞는 로그가 없습니다.</div>`;
        return;
    }

    container.innerHTML = filteredLogs.map(log => renderLogItemHtml(log)).join('');
}

// ==========================================
// 5. 옷장(Wardrobe) 모듈
// ==========================================
async function fetchWardrobe() {
    const container = document.getElementById('wardrobe-container');
    container.innerHTML = `<div style="text-align: center; color: var(--accent-color); font-size: 11px; margin-top: 20px;">loading...</div>`;
    try {
        const res = await fetchWithAuth("/api/v1/perfumes/list");
        if (res.ok) {
            globalWardrobeData = await res.json();
            renderWardrobeGrid();
        }
    } catch (e) {
        console.error("옷장 로딩 에러:", e); // 💡 실제 에러 원인을 파악하기 위해 로그 추가
        container.innerHTML = `<div style="text-align: center; color: var(--accent-color); font-size: 11px;">[ERROR] Network issue.</div>`;
    }
}

function renderWardrobeGrid() {
    const container = document.getElementById('wardrobe-container');

    // 1. 탭 필터 (본품 vs 샘플)
    let filteredData = globalWardrobeData.filter(p =>
        currentWardrobeTab === 'sample' ? p.isSample === true : p.isSample !== true
    );

    // 2. 텍스트 검색 필터 (이름, 브랜드, 노트 전체)
    if (currentSearchQuery) {
        filteredData = filteredData.filter(p => {
            const matchName = (p.name || '').toLowerCase().includes(currentSearchQuery);
            const matchBrand = (p.brand || '').toLowerCase().includes(currentSearchQuery);
            const allNotes = [];
            if (p.notes) {
                ['top', 'middle', 'base', 'general'].forEach(k => {
                    if (p.notes[k]) allNotes.push(...p.notes[k].map(n => n.toLowerCase()));
                });
            }
            const matchNote = allNotes.some(n => n.includes(currentSearchQuery));
            return matchName || matchBrand || matchNote;
        });
    }

    // 💡 3. 계절 스마트 필터 (구형 노트 필터링 폐기, 완벽한 seasons 데이터 기반 필터링)
    if (currentSeasonFilter !== 'ALL') {
        filteredData = filteredData.filter(p =>
            getRecommendedSeasons(p.seasons, p.seasonStats ?? p.season_stats)
                .has(currentSeasonFilter)
        );
    }

    document.getElementById('wardrobe-title').innerText = `My Wardrobe (${filteredData.length})`;

    if (filteredData.length === 0) {
        container.innerHTML = `<div style="text-align: center; color: var(--accent-color); font-size: 11px; margin-top: 20px;">조건에 맞는 향수가 없습니다.</div>`;
        return;
    }

    container.innerHTML = filteredData.map(p => {
        const imgTag = p.imageUrl ? `<img src="${p.imageUrl}" class="wardrobe-thumb" alt="thumb">` : `<div class="wardrobe-thumb">No Img</div>`;
        const shortDate = p.date ? p.date.split('T')[0] : '';
        const notesPreview = parseNotesPreview(p.notes);

        // 기존 뱃지/바 생성 로직 지우고 통합 함수로 교체
        const seasonUI = generateSeasonUI(p.seasons, p.seasonStats ?? p.season_stats);

        return `
            <div class="wardrobe-card" data-id="${p.id}" onclick="openPerfumeDetail('${p.id}')">
                <div class="wardrobe-drag-handle" style="display: none; cursor: grab; font-size: 18px; color: var(--accent-color); padding: 0 10px 0 0;" onclick="event.stopPropagation()">≡</div>
                ${imgTag}
                <div class="wardrobe-info">
                    <div class="wardrobe-brand">
                        ${p.brand || 'UNKNOWN'} ${shortDate ? `<span style="margin-left:5px; font-size:9px;">[${shortDate}]</span>` : ''}
                    </div>
                    <div class="wardrobe-name">${p.name}</div>
                    ${seasonUI} <!-- 💡 생성된 UI 주입 -->
                    <div class="wardrobe-notes">${notesPreview}</div>
                </div>
            </div>
        `;

    }).join('');
}

function switchWardrobeTab(tab) {
    if (isWardrobeReorderMode) toggleWardrobeReorderMode();

    currentSearchQuery = '';
    const searchInput = document.getElementById('wardrobe-search');
    if (searchInput) searchInput.value = '';

    currentSeasonFilter = 'ALL';
    document.querySelectorAll('.season-btn').forEach(b => b.classList.remove('active'));
    document.querySelector('.season-btn')?.classList.add('active');

    currentWardrobeTab = tab;
    const btnBottle = document.getElementById('tab-bottle');
    const btnSample = document.getElementById('tab-sample');
    const btnAddSample = document.getElementById('btn-add-sample');

    if (tab === 'bottle') {
        btnBottle.style.color = 'var(--accent-color)';
        btnBottle.style.fontWeight = 'bold';
        btnBottle.style.borderBottom = '2px solid var(--accent-color)';
        btnSample.style.color = '#a1a1aa';
        btnSample.style.fontWeight = 'normal';
        btnSample.style.borderBottom = '2px solid transparent';
        if (btnAddSample) btnAddSample.style.display = 'none';
    } else {
        btnSample.style.color = 'var(--accent-color)';
        btnSample.style.fontWeight = 'bold';
        btnSample.style.borderBottom = '2px solid var(--accent-color)';
        btnBottle.style.color = '#a1a1aa';
        btnBottle.style.fontWeight = 'normal';
        btnBottle.style.borderBottom = '2px solid transparent';
        if (btnAddSample) btnAddSample.style.display = 'inline-flex';
    }
    renderWardrobeGrid();
}

function toggleWardrobeReorderMode() {
    isWardrobeReorderMode = !isWardrobeReorderMode;
    const defaultActions = document.getElementById('wardrobe-default-actions');
    const reorderActions = document.getElementById('wardrobe-reorder-actions');
    const dragHandles = document.querySelectorAll('.wardrobe-drag-handle');

    if (isWardrobeReorderMode) {
        defaultActions.style.display = 'none';
        reorderActions.style.display = 'flex';
        dragHandles.forEach(el => el.style.display = 'block');
        initWardrobeSortable();
    } else {
        defaultActions.style.display = 'flex';
        reorderActions.style.display = 'none';
        dragHandles.forEach(el => el.style.display = 'none');
        if (wardrobeSortable) {
            wardrobeSortable.destroy();
            wardrobeSortable = null;
        }
    }
}

function initWardrobeSortable() {
    const container = document.getElementById('wardrobe-container');
    if (container) {
        wardrobeSortable = new Sortable(container, {
            animation: 200,
            handle: '.wardrobe-drag-handle',
            ghostClass: 'sortable-ghost',
            dragClass: 'sortable-drag',
            forceFallback: true,
            fallbackClass: 'sortable-drag'
        });
    }
}

async function saveWardrobeOrder() {
    const cards = document.querySelectorAll('#wardrobe-container .wardrobe-card');
    const orderedIds = Array.from(cards).map(card => card.getAttribute('data-id'));

    if (orderedIds.length === 0) {
        toggleWardrobeReorderMode();
        return;
    }

    const saveBtn = document.getElementById('btn-save-w-order');
    const originalText = saveBtn.textContent;
    saveBtn.textContent = "saving...";
    saveBtn.classList.add('is-busy');

    try {
        const res = await fetchWithAuth("/api/v1/perfumes/reorder", {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(orderedIds)
        });

        if (res.ok) {
            toggleWardrobeReorderMode();
            fetchWardrobe();
        } else {
            alert("순서 저장에 실패했습니다.");
        }
    } catch (e) {
        alert("네트워크 오류가 발생했습니다.");
    } finally {
        saveBtn.textContent = originalText;
        saveBtn.classList.remove('is-busy');
    }
}

function openPerfumeDetail(perfumeId, from = 'wardrobe') {
    const p = globalWardrobeData.find(x => x.id === perfumeId);
    if (!p) return;

    detailReturnTo = from;

    document.getElementById('detail-brand').innerText = p.brand || 'UNKNOWN BRAND';
    document.getElementById('detail-name').innerText = p.name;
    document.getElementById('detail-date').innerText = p.date ? `Added: ${p.date.split('T')[0]}` : 'Added: N/A';
    document.getElementById('detail-delete-btn').setAttribute('onclick', `deleteWardrobe('${p.id}')`);
    document.getElementById('detail-edit-btn').setAttribute('onclick', `openEditInfo('wardrobe', '${p.id}', '${p.name.replace(/'/g, "\\'")}', '${(p.brand || '').replace(/'/g, "\\'")}')`);

    const imgEl = document.getElementById('detail-image');
    const boxEl = document.getElementById('detail-image-box');
    if (p.imageUrl) {
        imgEl.src = p.imageUrl;
        imgEl.style.display = 'block';
        boxEl.style.display = 'none';
    } else {
        imgEl.style.display = 'none';
        boxEl.style.display = 'flex';
    }

    document.getElementById('detail-notes-container').innerHTML =
        generateSeasonUI(p.seasons, p.seasonStats ?? p.season_stats, { size: 'lg', force: 'bar' })
        + renderNotesHtml(p.notes);

    document.getElementById('detail-notes-container').innerHTML = renderNotesHtml(p.notes);
    switchView('perfume-detail', null);
    fetchPerfumeHistory(perfumeId);
}

function closePerfumeDetail() {
    if (detailReturnTo === 'main') {
        switchView('main', document.querySelector('.nav-item.main-tab'));
    } else if (detailReturnTo === 'all-logs') {
        switchView('all-logs', null);
    } else {
        switchView('wardrobe', document.querySelectorAll('.nav-item')[1]);
    }
}

async function fetchPerfumeHistory(perfumeId) {
    const container = document.getElementById('detail-history-container');
    container.innerHTML = `<div style="text-align: center; color: var(--accent-color); font-size: 11px; margin-top: 20px;">fetching history...</div>`;

    const owner = globalWardrobeData.find(p => p.id === perfumeId);

    try {
        const response = await fetchWithAuth(`/api/v1/logs/perfume/${perfumeId}`);
        if (response.ok) {
            const logs = await response.json();
            if (logs.length === 0) {
                container.innerHTML = `<div style="text-align: center; color: var(--accent-color); font-size: 11px; margin-top: 20px;">No wearing history found.</div>`;
                return;
            }
            container.innerHTML = logs.map(log => {
                registerLog(log, {
                    perfumeId: perfumeId,
                    perfumeName: log.perfumeName || (owner ? owner.name : '')
                });

                const shortDate = log.date ? log.date.split('T')[0] : 'N/A';
                const weatherIcon = getWeatherIcon(log.weather);
                const tempHum = [log.temp ? `${log.temp}°C` : '', log.humidity ? `${log.humidity}%` : ''].filter(Boolean).join(' / ');
                let rateHtml = (log.rate && log.rate !== 'null' && log.rate > 0)
                    ? `<div style="color:#f59e0b; font-size:12px; font-weight:bold; margin-top:5px; letter-spacing:2px;">⭐ ${parseFloat(log.rate).toFixed(1)}</div>`
                    : `<div style="display:inline-block; padding:4px 8px; margin: 5px 4px 4px 4px; background-color:rgba(244, 63, 94, 0.1); color:#f43f5e; border-radius:4px; font-size:10px; font-weight:bold; animation:badge-pulse 2s infinite; border: 1px solid;">✍️ 터치해서 별점 남기기</div>`;

                return `
                    <div class="log-item" onclick="openEditLogById('${log.pageId}')">
                        <div class="log-left">
                            <div class="log-details">
                                <div class="log-date" style="font-size: 12px; color: var(--text-color); font-weight: bold;">${shortDate}</div>
                                ${rateHtml}
                            </div>
                        </div>
                        <div class="log-weather">
                            <span style="font-size: 14px;">${weatherIcon}</span> ${log.weather || 'Unknown'}<br>${tempHum}
                        </div>
                    </div>
                `;
            }).join('');
        }
    } catch (e) {
        container.innerHTML = `<div style="text-align: center; color: var(--accent-color); font-size: 11px;">[ERROR] Network issue.</div>`;
    }
}

async function deleteWardrobe(pageId) {
    if (!confirm('진짜 옷장에서 빼시겠습니까? (과거 착향 로그는 보존됩니다)')) return;
    try {
        const res = await fetchWithAuth(`/api/v1/perfumes/${pageId}`, { method: "DELETE" });
        if (res.ok) {
            fetchWardrobe();
            switchView('wardrobe', document.querySelectorAll('.nav-item')[1]);
        } else {
            alert('삭제 실패!');
        }
    } catch (e) {
        alert('네트워크 오류');
    }
}

// ==========================================
// 6. NFC 스캔 & 향수 등록 파이프라인
// ==========================================
const scanBtn = document.getElementById('scan-btn');
const statusDiv = document.getElementById('status');

function resetScanUI() {
    isScanning = false;
    scanBtn.classList.remove('scanning', 'success');
    scanBtn.innerText = "TAP TO SCAN (NFC)";
    statusDiv.innerText = "waiting for interaction...";
    if (abortController) abortController.abort();
}

scanBtn.addEventListener('click', async () => {
    if (!("NDEFReader" in window)) {
        statusDiv.innerText = "[ERROR] NFC not supported.";
        return;
    }
    if (isScanning) {
        resetScanUI();
        statusDiv.innerText = "[CANCELLED] Scan aborted.";
        return;
    }

    try {
        abortController = new AbortController();
        const ndef = new NDEFReader();
        await ndef.scan({ signal: abortController.signal });

        isScanning = true;
        scanBtn.classList.add('scanning');
        scanBtn.innerText = "Scanning... (Tap to cancel)";
        statusDiv.innerText = "Please approach the tag.";

        ndef.onreading = async (event) => {
            abortController.abort();
            if (navigator.vibrate) navigator.vibrate([100, 50, 100]);
            const uid = event.serialNumber;

            scanBtn.classList.remove('scanning');
            scanBtn.classList.add('success');
            scanBtn.innerText = "STAMPED";
            statusDiv.innerText = `Tag detected. UID: ${uid}\nSyncing with server...`;

            try {
                const response = await fetchWithAuth("/api/v1/logs/scan", {
                    method: "POST", headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        uid: uid,
                        lat: currentLat,
                        lon: currentLon,
                        useCurrentTemp: document.getElementById('scan-use-current-temp')?.checked || false
                    })
                });
                const resultText = await response.text();

                if (response.ok) {
                    statusDiv.innerText = `[SUCCESS] Logged on Notion.`;
                    setTimeout(() => {
                        refreshAfterLogChange();
                        resetScanUI();
                    }, 1500);
                } else if (response.status === 400 && resultText.includes("Unregistered")) {
                    statusDiv.innerText = `[UNKNOWN] Redirecting to form...`;
                    setTimeout(() => {
                        document.getElementById('reg-uid').value = uid;
                        fetchBrands();
                        switchView('register', null);
                        resetScanUI();
                        refreshAfterLogChange();
                    }, 1200);
                } else {
                    statusDiv.innerText = `[ERROR] ${resultText}`;
                    setTimeout(resetScanUI, 2500);
                }
            } catch (apiError) {
                statusDiv.innerText = "[ERROR] Network failure.";
                setTimeout(resetScanUI, 2500);
            }
        };
    } catch (error) {
        if (error.name !== 'AbortError') {
            statusDiv.innerText = `[ERROR] ${error}`;
            resetScanUI();
        }
    }
});

async function handleIosNfcScan(uid) {
    const statusDiv = document.getElementById('status');
    statusDiv.innerText = `[iOS NFC] 태그(${uid}) 확인 중...`;

    try {
        const response = await fetchWithAuth("/api/v1/logs/scan", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ uid: uid, lat: currentLat, lon: currentLon })
        });
        const resultText = await response.text();

        if (response.ok) {
            statusDiv.innerText = `[SUCCESS] 착향 로그가 기록되었습니다.`;
            setTimeout(() => {
                refreshAfterLogChange();
            }, 1500);
        } else if (response.status === 400 && resultText.includes("Unregistered")) {
            alert("미등록된 향수 태그입니다. 신규 등록 화면으로 이동합니다.");
            document.getElementById('reg-uid').value = uid;
            fetchBrands();
            switchView('register', null);
            statusDiv.innerText = "waiting for interaction...";
        } else {
            statusDiv.innerText = `[ERROR] ${resultText}`;
        }
    } catch (e) {
        statusDiv.innerText = "[ERROR] 서버 통신 실패";
    }
}

async function fetchBrands() {
    try {
        const res = await fetchWithAuth("/api/v1/perfumes/brands");
        if (res.ok) {
            const brands = await res.json();
            document.getElementById('brand-list').innerHTML = brands.map(b => `<option value="${b}">`).join('');
        }
    } catch (e) {
        console.error("Brand load failed");
    }
}

async function crawlUrl() {
    const urlInput = document.getElementById('reg-url').value.trim();
    const crawlBtn = document.getElementById('crawl-btn');
    const terminal = document.getElementById('crawl-terminal');
    const step2 = document.getElementById('crawl-step-2');
    const regStatus = document.getElementById('reg-status');
    const previewContainer = document.getElementById('notes-preview-container');
    const previewBox = document.getElementById('notes-preview-box');

    if (!urlInput) {
        regStatus.innerText = "Please paste a URL first.";
        return;
    }

    crawlBtn.disabled = true;
    crawledNotesData = null;
    crawledImageUrl = "";
    previewContainer.style.display = 'none';
    previewBox.innerHTML = '';
    regStatus.innerText = "";

    crawlBtn.classList.add('is-busy');
    crawlBtn.textContent = 'crawling...';
    terminal.style.display = 'flex';
    terminal.classList.remove('error');
    step2.innerHTML = '> Fetching Fragrantica Notes... <span class="blink">_</span>';

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 70000);

    let retryPhase = 0;
    const progressInterval = setInterval(() => {
        retryPhase++;
        if (retryPhase === 1) step2.innerHTML = '> [WARN] Bot detection triggered. Retrying (1/3)... <span class="blink">_</span>';
        else if (retryPhase === 2) step2.innerHTML = '> [WARN] Solving Cloudflare challenge. Retrying (2/3)... <span class="blink">_</span>';
        else if (retryPhase === 3) step2.innerHTML = '> [WARN] Forcing extraction. Final attempt (3/3)... <span class="blink">_</span>';
    }, 16000);

    try {
        const res = await fetchWithAuth(`/api/v1/perfumes/crawl?url=${encodeURIComponent(urlInput)}`, {
            signal: controller.signal
        });

        clearTimeout(timeoutId);
        clearInterval(progressInterval);

        if (res.ok) {
            const data = await res.json();
            crawledNotesData = data.notes;
            crawledImageUrl = data.imageUrl;
            crawledSeasonsData = data.seasons || [];
            crawledSeasonStats = data.seasonStats || {};

            if (!data.notes || Object.keys(data.notes).length === 0) {
                terminal.classList.add('error');
                step2.innerHTML = '> [BLOCKED] Anti-bot system prevented crawling.';
                regStatus.innerText = "Please enter manually.";
            } else {
                step2.innerHTML = '> [SUCCESS] Notes extracted perfectly.';
                regStatus.innerText = "[SUCCESS] Data auto-filled.";

                // 화면에 계절 뱃지도 예쁘게 뿌려줌 (색상 및 한글 적용)
                const seasonBadges = generateSeasonUI(crawledSeasonsData, crawledSeasonStats, { force: 'tag' });

                previewBox.innerHTML = (seasonBadges ? `<div style="margin-bottom:10px;">${seasonBadges}</div>` : '') + renderNotesHtml(data.notes);
                previewContainer.style.display = 'block';
            }
        } else {
            terminal.classList.add('error');
            step2.innerHTML = '> [ERROR] Backend scraping failed.';
            regStatus.innerText = "Please check URL and try again.";
        }
    } catch (e) {
        terminal.classList.add('error');
        step2.innerHTML = (e.name === 'AbortError')
            ? '> [TIMEOUT] Scraping took too long. (Max retries exceeded)'
            : '> [ERROR] Network connection lost.';
        regStatus.innerText = "Please try again or enter manually.";
    } finally {
        clearInterval(progressInterval);
        clearTimeout(timeoutId);
        crawlBtn.classList.remove('is-busy');
        crawlBtn.textContent = 'auto fill';
        crawlBtn.disabled = false;
    }
}

function openRegisterView(isSample = false) {
    currentIsSample = isSample;
    fetchBrands();
    switchView('register', null);

    const titleEl = document.querySelector('#view-register h2');
    const uidInput = document.getElementById('reg-uid');
    const uidGroup = uidInput ? uidInput.closest('.form-group') : null;

    const cancelBtn = `<button type="button" class="loc-btn loc-btn--muted" onclick="cancelRegistration()">cancel</button>`;
    if (titleEl) titleEl.innerHTML = `<span>Register ${isSample ? 'Sample' : 'Perfume'}</span>${cancelBtn}`;
}

function cancelRegistration() {
    document.querySelectorAll('#view-register input:not(#reg-uid)').forEach(el => el.value = '');
    document.getElementById('notes-preview-container').style.display = 'none';
    document.getElementById('crawl-terminal').style.display = 'none';
    document.getElementById('reg-status').innerText = '';
    crawledNotesData = null;
    crawledImageUrl = "";

    if (currentIsSample) {
        switchView('wardrobe', document.querySelectorAll('.nav-item')[1]);
        switchWardrobeTab('sample');
    } else {
        switchView('main', document.querySelector('.nav-item.main-tab'));
    }
}

document.getElementById('reg-auto-log').addEventListener('change', (e) => {
    document.getElementById('register-submit-btn').innerText = e.target.checked ? "Register & Log" : "Register Only";
});

document.getElementById('register-submit-btn').addEventListener('click', async () => {
    let uid = document.getElementById('reg-uid').value;
    const name = document.getElementById('reg-name').value.trim();
    const brand = document.getElementById('reg-brand').value.trim();
    const url = document.getElementById('reg-url').value.trim();
    const autoLog = document.getElementById('reg-auto-log').checked;
    const regStatus = document.getElementById('reg-status');
    const submitBtn = document.getElementById('register-submit-btn');

    if (currentIsSample) {
        uid = `SAMPLE-${new Date().getTime()}`;
    } else if (!uid) {
        regStatus.innerText = "[ERROR] NFC UID is required.";
        return;
    }

    if (!name) {
        regStatus.innerText = "[ERROR] Name is required.";
        return;
    }

    submitBtn.innerText = "Processing...";
    regStatus.innerText = "Syncing with Master DB...";

    try {
        const res = await fetchWithAuth("/api/v1/perfumes/register", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                uid, name, brand, url,
                imageUrl: crawledImageUrl,
                notes: crawledNotesData,
                lat: currentLat, lon: currentLon,
                isSample: currentIsSample,
                skipLog: !autoLog,
                useCurrentTemp: document.getElementById('reg-use-current-temp')?.checked || false,
                seasons: crawledSeasonsData,
                seasonStats: crawledSeasonStats
            })
        });

        const resultText = await res.text();

        if (res.ok) {
            submitBtn.classList.add('success');
            submitBtn.innerText = "SUCCESS";
            regStatus.innerText = `[SUCCESS] ${currentIsSample ? 'Sample' : 'Perfume'} added to Wardrobe.`;

            setTimeout(() => {
                document.querySelectorAll('#view-register input:not(#reg-uid):not([type="checkbox"])').forEach(el => el.value = '');
                document.getElementById('notes-preview-container').style.display = 'none';
                document.getElementById('reg-auto-log').checked = false;

                const terminal = document.getElementById('crawl-terminal');
                if (terminal) terminal.style.display = 'none';

                crawledNotesData = null;
                crawledImageUrl = "";
                submitBtn.classList.remove('success');
                submitBtn.innerText = "Register Only";
                regStatus.innerText = "";

                switchView('wardrobe', document.querySelector('.nav-item.wardrobe-tab') || null);
                switchWardrobeTab(currentIsSample ? 'sample' : 'bottle');
                fetchWardrobe();
                refreshAfterLogChange();
            }, 2000);
        } else {
            regStatus.innerText = `[ERROR] ${resultText}`;
            submitBtn.innerText = autoLog ? "Register & Log" : "Register Only";
        }
    } catch (e) {
        regStatus.innerText = "[ERROR] Network failure.";
        submitBtn.innerText = autoLog ? "Register & Log" : "Register Only";
    }
});

// ==========================================
// 7. 통계(Summary) 모듈
// ==========================================
async function fetchSummary() {
    try {
        const res = await fetchWithAuth(`/api/v1/logs/recent?limit=100&t=${Date.now()}`);
        if (!res.ok) return;
        const logs = await res.json();
        globalRecentLogs = logs;
        updateRecommendation();

        // 💡 이달의 향수 (Perfume of the Month)
        const currentMonth = new Date().toISOString().slice(0, 7); // "YYYY-MM"
        const thisMonthLogs = logs.filter(l => l.date && l.date.startsWith(currentMonth));
        const potmContainer = document.getElementById('potm-container');

        if (thisMonthLogs.length === 0) {
            potmContainer.innerHTML = `<div style="font-size:11px; color:var(--accent-color);">이번 달 기록이 없습니다.</div>`;
        } else {
            const mCounts = {};
            thisMonthLogs.forEach(l => {
                const name = l.perfumeName || 'Unknown';
                mCounts[name] = (mCounts[name] || 0) + 1;
            });
            const potm = Object.entries(mCounts).sort((a, b) => b[1] - a[1])[0];
            const potmLog = thisMonthLogs.find(l => l.perfumeName === potm[0]);
            const imgTag = potmLog.imageUrl
                ? `<img src="${potmLog.imageUrl}" class="potm__img" alt="">`
                : `<div class="potm__img potm__img--empty">No Img</div>`;

            potmContainer.className = 'potm';
            potmContainer.innerHTML = `
                ${imgTag}
                <div class="potm__body">
                    <span class="potm__tag">👑 ${new Date().getMonth() + 1}월의 최애</span>
                    <div class="potm__name">${potm[0]}</div>
                    <div class="potm__sub">이번 달 <b>${potm[1]}회</b> 착향</div>
                </div>`;
        }

        // 1. Top 3 명예의 전당
        const counts = logs.reduce((acc, log) => {
            const name = log.perfumeName || 'Unknown';
            acc[name] = (acc[name] || 0) + 1;
            return acc;
        }, {});
        const sortedTop = Object.entries(counts).sort((a, b) => b[1] - a[1]).slice(0, 3);
        const chartColors = ['#f43f5e', '#a855f7', '#6366f1'];

        const ctx = document.getElementById('topPerfumeChart').getContext('2d');
        if (summaryChartInstance) summaryChartInstance.destroy();
        summaryChartInstance = new Chart(ctx, {
            type: 'doughnut',
            data: {
                labels: sortedTop.map(x => x[0]),
                datasets: [{
                    data: sortedTop.map(x => x[1]),
                    backgroundColor: chartColors,
                    borderWidth: 0,
                    cutout: '75%'
                }]
            },
            options: {
                responsive: true, maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        enabled: true,
                        backgroundColor: 'rgba(0, 0, 0, 0.8)',
                        displayColors: false,
                        padding: 8,
                        callbacks: {
                            title: () => '',
                            label: (context) => `${context.label} : ${context.parsed}회`
                        }
                    }
                }
            }
        });

        const legendBox = document.getElementById('top-legend-container');
        const trophies = ['🥇', '🥈', '🥉'];
        const maxCount = sortedTop.length ? sortedTop[0][1] : 1;

        legendBox.innerHTML = sortedTop.map((item, i) => `
                <div class="rank-row" style="--rank:${chartColors[i]}">
                    <div class="rank-row__top">
                        <span class="rank-row__medal">${trophies[i]}</span>
                        <span class="rank-row__name">${item[0]}</span>
                        <span class="rank-row__count">${item[1]}</span>
                    </div>
                    <div class="rank-row__track">
                        <div class="rank-row__fill" style="width:${(item[1] / maxCount * 100).toFixed(1)}%"></div>
                    </div>
                </div>`).join('');

        // 2. 잔디 심기 (횟수별 농도 + 요일 정렬)
        const heatmapBox = document.getElementById('heatmap-container');
        const dateCounts = {};
        logs.forEach(l => {
            if (!l.date) return;
            const d = l.date.split('T')[0];
            dateCounts[d] = (dateCounts[d] || 0) + 1;
        });

        const today = new Date();
        const todayStr = toLocalDateStr(today);
        const days = [];
        for (let i = 29; i >= 0; i--) {
            const d = new Date();
            d.setDate(today.getDate() - i);
            days.push(d);
        }

        const lvOf = n => n <= 0 ? '' : n === 1 ? 'lv1' : n === 2 ? 'lv2' : n === 3 ? 'lv3' : 'lv4';

        heatmapBox.innerHTML = days.map(d => {
            const ds  = toLocalDateStr(d);
            const n   = dateCounts[ds] || 0;
            const cls = [lvOf(n), ds === todayStr ? 'is-today' : ''].filter(Boolean).join(' ');
            const md  = `${d.getMonth() + 1}/${d.getDate()}`;
            return `<div class="heatmap-cell ${cls}" title="${md} · ${n}회"></div>`;
        }).join('');

        // 스탯 스트립
        const strip = document.getElementById('summary-stats');
        if (strip) {
            const uniq = new Set(logs.map(l => l.perfumeName).filter(Boolean)).size;
            const dset = new Set(Object.keys(dateCounts));
            let streak = 0;
            const cur = new Date();
            if (!dset.has(toLocalDateStr(cur))) cur.setDate(cur.getDate() - 1);
            while (dset.has(toLocalDateStr(cur))) { streak++; cur.setDate(cur.getDate() - 1); }

            strip.innerHTML = `
                <div class="stat-cell"><div class="stat-cell__num">${logs.length}</div><span class="stat-cell__label">TOTAL LOGS</span></div>
                <div class="stat-cell"><div class="stat-cell__num">${uniq}</div><span class="stat-cell__label">PERFUMES</span></div>
                <div class="stat-cell"><div class="stat-cell__num">${streak}<small>d</small></div><span class="stat-cell__label">STREAK</span></div>`;
        }

        // 3. 날씨 픽
        const thirtyDaysAgo = new Date();
        thirtyDaysAgo.setDate(today.getDate() - 30);
        const recentLogs = logs.filter(l => l.date && new Date(l.date.split('T')[0]) >= thirtyDaysAgo);

        const weatherPick = (list) => {
            const filtered = recentLogs.filter(l => l.weather && list.some(c => l.weather.includes(c)));
            if (filtered.length === 0) return null;
            const fc = filtered.reduce((acc, l) => {
                acc[l.perfumeName] = (acc[l.perfumeName] || 0) + 1;
                return acc;
            }, {});
            const top = Object.entries(fc).sort((a, b) => b[1] - a[1])[0];
            return { name: top[0], count: top[1] };
        };

        const wRow = (ico, label, pick) => `
            <div class="wpick__row">
                <span class="wpick__ico">${ico}</span>
                <span class="wpick__label">${label}</span>
                <span class="wpick__name${pick ? '' : ' is-empty'}">${pick ? pick.name : '기록 부족'}</span>
                ${pick ? `<span class="wpick__cnt">${pick.count}회</span>` : ''}
            </div>`;

        document.getElementById('weather-insight-container').className = 'wpick';
        document.getElementById('weather-insight-container').innerHTML =
            wRow('☀️', '맑은 날', weatherPick(['Clear', 'Sunny'])) +
            wRow('☁️', '흐린 날', weatherPick(['Cloud'])) +
            wRow('🌧️', '비 · 눈', weatherPick(['Rain', 'Snow', 'Drizzle']));
    } catch (e) {
        console.error("통계 로딩 실패:", e);
    }
}

// ==========================================
// 8. 위시리스트(Wishlist) 모듈
// ==========================================
function toggleWishForm() {
    isWishFormOpen = !isWishFormOpen;
    document.getElementById('wish-form-container').style.display = isWishFormOpen ? 'block' : 'none';
    if (isWishFormOpen) {
        document.getElementById('wish-name').value = '';
        document.getElementById('wish-brand').value = '';
        document.getElementById('wish-url').value = '';
        document.getElementById('wish-img').value = '';
        document.getElementById('wish-notes-preview-container').style.display = 'none';
        document.getElementById('wish-crawl-terminal').style.display = 'none';
        wishCrawledNotesData = null;
        wishCrawledImageUrl = "";
        wishCrawledSeasonsData = [];
    }
}

async function crawlWishUrl() {
    const wishBtn = document.getElementById('wish-crawl-btn');
    const urlInput = document.getElementById('wish-url').value.trim();
    const terminal = document.getElementById('wish-crawl-terminal');
    const step = document.getElementById('wish-crawl-step');
    if (!urlInput) {
        alert("URL을 먼저 입력해주세요.");
        return;
    }

    wishBtn.disabled = true;
    wishBtn.classList.add('is-busy');
    wishBtn.textContent = 'crawling...';
    terminal.style.display = 'flex';
    terminal.classList.remove('error');
    step.innerHTML = '> Fetching Notes... <span class="blink">_</span>';
    wishCrawledNotesData = null;
    wishCrawledImageUrl = "";

    try {
        const res = await fetchWithAuth(`/api/v1/perfumes/crawl?url=${encodeURIComponent(urlInput)}`);
        if (res.ok) {
            const data = await res.json();
            wishCrawledNotesData = data.notes;
            wishCrawledImageUrl = data.imageUrl;
            wishCrawledSeasonsData = data.seasons || [];
            wishCrawledSeasonStats = data.seasonStats || {};
            document.getElementById('wish-img').value = data.imageUrl;

            step.innerHTML = '> [SUCCESS] Notes extracted.';

            // 💡 계절 뱃지 생성 및 렌더링
            const seasonBadges = generateSeasonUI(crawledSeasonsData, crawledSeasonStats, { force: 'tag' });

            const badgeHtml = generateWishlistBadgeHtml(data.notes);
            document.getElementById('wish-notes-preview-box').innerHTML = (seasonBadges ? `<div style="margin-bottom:10px;">${seasonBadges}</div>` : '') + badgeHtml + renderNotesHtml(data.notes);

            document.getElementById('wish-notes-preview-container').style.display = 'block';
        } else {
            terminal.classList.add('error');
            step.innerHTML = '> [ERROR] Scraping failed.';
        }
    } catch (e) {
        terminal.classList.add('error');
        step.innerHTML = '> [ERROR] Network issue.';
    } finally {
        wishBtn.classList.remove('is-busy');
        wishBtn.textContent = 'auto fill';
        wishBtn.disabled = false;
    }
}

async function submitWish() {
    const name = document.getElementById('wish-name').value.trim();
    const brand = document.getElementById('wish-brand').value.trim();
    if (!name) {
        alert('향수 이름을 입력해주세요.');
        return;
    }

    const todayDate = toLocalDateStr(new Date());
    const payload = {
        name: name, brand: brand,
        imageUrl: wishCrawledImageUrl,
        url: document.getElementById('wish-url').value.trim(),
        date: todayDate,
        notes: wishCrawledNotesData,
        seasons: wishCrawledSeasonsData,
        seasonStats: wishCrawledSeasonStats
    };

    try {
        const res = await fetchWithAuth("/api/v1/wishlist", {
            method: "POST", headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
        if (res.ok) {
            toggleWishForm();
            fetchWishlist();
        } else {
            alert('추가 실패!');
        }
    } catch (e) {
        alert('네트워크 오류');
    }
}

async function fetchWishlist() {
    const container = document.getElementById('wish-container');
    container.innerHTML = `<div style="text-align: center; color: var(--accent-color); font-size: 11px; margin-top: 20px;">loading...</div>`;
    try {
        const res = await fetchWithAuth("/api/v1/wishlist");
        if (res.ok) {
            globalWishlistData = await res.json();
            document.getElementById('wish-title').innerText = `Wishlist (${globalWishlistData.length})`;

            if (globalWishlistData.length === 0) {
                container.innerHTML = `<div style="text-align: center; color: var(--accent-color); font-size: 11px; margin-top: 20px;">위시리스트가 비어있습니다.</div>`;
                return;
            }
            container.innerHTML = globalWishlistData.map(w => {
                const imgTag = w.imageUrl
                    ? `<img src="${w.imageUrl}" style="width: 50px; height: 70px; object-fit: cover; border-radius: 2px; border: 1px solid #e0e0dc; flex-shrink: 0;">`
                    : `<div style="width: 50px; height: 70px; background-color: #f5f5f5; border: 1px solid #e0e0dc; border-radius: 2px; flex-shrink: 0; display: flex; align-items: center; justify-content: center; font-size: 8px; color: var(--accent-color);">No Img</div>`;
                const shortDate = w.date ? w.date.split('T')[0] : '';
                const seasonTags = generateSeasonUI(w.seasons, w.seasonStats ?? w.season_stats);

                return `
                <div class="log-item wish-card" data-id="${w.id}" style="padding: 10px;" onclick="openWishDetail('${w.id}')">
                    <div style="display: flex; align-items: center; gap: 12px; flex: 1;">
                        <div class="wish-drag-handle" style="display: none; cursor: grab; font-size: 18px; color: var(--accent-color); padding: 0 10px;" onclick="event.stopPropagation()">≡</div>
                        ${imgTag}
                        <div>
                            <div style="font-size: 10px; color: var(--accent-color); text-transform: uppercase;">${w.brand || 'UNKNOWN'} <span style="margin-left:5px; font-size:9px;">[${shortDate}]</span></div>
                            <div style="font-weight: bold; color: var(--text-color); font-size: 14px; margin-top: 2px;">${w.name}</div>
                            ${seasonTags}
                        </div>
                    </div>
                </div>
            `;
            }).join('');
        }
    } catch (e) {
        container.innerHTML = `<div style="text-align: center; color: var(--accent-color); font-size: 11px;">[ERROR] Network issue.</div>`;
    }
}

function openWishDetail(wishId) {
    const w = globalWishlistData.find(x => x.id === wishId);
    if (!w) return;
    currentWishPromotionId = wishId;

    document.getElementById('wish-detail-brand').innerText = w.brand || 'UNKNOWN BRAND';
    document.getElementById('wish-detail-name').innerText = w.name;
    document.getElementById('wish-detail-date').innerText = w.date ? `Added: ${w.date.split('T')[0]}` : 'Added: N/A';
    document.getElementById('wish-detail-delete-btn').setAttribute('onclick', `deleteWish('${w.id}')`);
    document.getElementById('wish-detail-edit-btn').setAttribute('onclick', `openEditInfo('wish', '${w.id}', '${w.name.replace(/'/g, "\\'")}', '${(w.brand || '').replace(/'/g, "\\'")}')`);

    const imgEl = document.getElementById('wish-detail-image');
    const boxEl = document.getElementById('wish-detail-image-box');
    if (w.imageUrl) {
        imgEl.src = w.imageUrl;
        imgEl.style.display = 'block';
        boxEl.style.display = 'none';
    } else {
        imgEl.style.display = 'none';
        boxEl.style.display = 'flex';
    }

    document.getElementById('wish-detail-notes-container').innerHTML = renderNotesHtml(w.notes);
    const btn = document.getElementById('wish-promote-btn');
    btn.classList.remove('scanning', 'success');
    btn.innerText = "Purchase & Register (NFC)";
    document.getElementById('wish-promote-status').innerText = "";

    switchView('wish-detail', null);
}

async function startWishPromoteScan() {
    const wish = globalWishlistData.find(w => w.id === currentWishPromotionId);
    if (!wish) return;

    const btn = document.getElementById('wish-promote-btn');
    const statusDiv = document.getElementById('wish-promote-status');

    if (!("NDEFReader" in window)) {
        statusDiv.innerText = "[ERROR] NFC not supported.";
        return;
    }
    if (btn.classList.contains('scanning')) {
        if (wishPromoteAbort) wishPromoteAbort.abort();
        btn.classList.remove('scanning');
        btn.innerText = "Purchase & Register (NFC)";
        statusDiv.innerText = "";
        return;
    }

    try {
        wishPromoteAbort = new AbortController();
        const ndef = new NDEFReader();
        await ndef.scan({ signal: wishPromoteAbort.signal });

        btn.classList.add('scanning');
        btn.innerText = "Scanning Tag... (Tap to cancel)";
        statusDiv.innerText = "NFC 태그를 스마트폰에 접촉하세요.";

        ndef.onreading = async (event) => {
            wishPromoteAbort.abort();
            if (navigator.vibrate) navigator.vibrate([100, 50, 100]);
            const uid = event.serialNumber;

            btn.classList.remove('scanning');
            btn.classList.add('success');
            btn.innerText = "TAG DETECTED";
            statusDiv.innerText = "옷장에 예쁘게 넣는 중...";

            const todayDate = toLocalDateStr(new Date());

            try {
                const regRes = await fetchWithAuth("/api/v1/perfumes/register", {
                    method: "POST", headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        uid: uid, name: wish.name, brand: wish.brand, url: wish.url,
                        imageUrl: wish.imageUrl, notes: wish.notes, date: todayDate,
                        seasons: wish.seasons,
                        seasonStats: wish.seasonStats,
                        lat: currentLat, lon: currentLon,
                        skipLog: true
                    })
                });

                if (regRes.ok) {
                    await fetchWithAuth(`/api/v1/wishlist/${wish.id}`, { method: "DELETE" });
                    statusDiv.innerHTML = "<span style='color:#10b981; font-weight:bold;'>🎉 새 향수를 들이셨군요! 옷장에 예쁘게 넣어뒀습니다.</span>";
                    setTimeout(() => {
                        fetchWardrobe();
                        fetchWishlist();
                        switchView('wardrobe', document.querySelectorAll('.nav-item')[1]);
                    }, 2000);
                } else {
                    statusDiv.innerText = `[ERROR] Registration failed.`;
                    btn.innerText = "Purchase & Register (NFC)";
                }
            } catch (e) {
                statusDiv.innerText = "[ERROR] Network failure.";
                btn.innerText = "Purchase & Register (NFC)";
            }
        };
    } catch (error) {
        if (error.name !== 'AbortError') {
            statusDiv.innerText = `[ERROR] ${error}`;
            btn.classList.remove('scanning');
            btn.innerText = "Purchase & Register (NFC)";
        }
    }
}

async function deleteWish(pageId) {
    if (!confirm('위시리스트에서 삭제할까요?')) return;
    try {
        const res = await fetchWithAuth(`/api/v1/wishlist/${pageId}`, { method: "DELETE" });
        if (res.ok) {
            fetchWishlist();
            switchView('wish', document.querySelectorAll('.nav-item')[3]);
        } else {
            alert('삭제 실패!');
        }
    } catch (e) {
        alert('네트워크 오류');
    }
}

function toggleWishReorderMode() {
    isWishReorderMode = !isWishReorderMode;
    document.getElementById('wish-default-actions').style.display = isWishReorderMode ? 'none' : 'flex';
    document.getElementById('wish-reorder-actions').style.display = isWishReorderMode ? 'flex' : 'none';

    if (isWishReorderMode) {
        isWishFormOpen = false;
        document.getElementById('wish-form-container').style.display = 'none';
    }

    const handles = document.querySelectorAll('.wish-drag-handle');
    const container = document.getElementById('wish-container');

    if (isWishReorderMode) {
        handles.forEach(h => h.style.display = 'block');
        wishSortableInstance = new Sortable(container, {
            animation: 150,
            handle: '.wish-drag-handle',
            ghostClass: 'sortable-ghost'
        });
    } else {
        handles.forEach(h => h.style.display = 'none');
        if (wishSortableInstance) wishSortableInstance.destroy();
        fetchWishlist();
    }
}

async function saveWishOrder() {
    if (!wishSortableInstance) return;
    const orderedIds = Array.from(document.querySelectorAll('.wish-card')).map(card => card.getAttribute('data-id'));

    if (orderedIds.length === 0) {
        toggleWishReorderMode();
        return;
    }

    const saveBtn = document.getElementById('btn-save-wish-order');
    const originalText = saveBtn.textContent;
    saveBtn.textContent = "saving...";
    saveBtn.classList.add('is-busy');

    try {
        const res = await fetchWithAuth("/api/v1/wishlist/reorder", {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(orderedIds)
        });

        if (res.ok) {
            toggleWishReorderMode();
            fetchWishlist();
        } else {
            alert("순서 저장 실패!");
        }
    } catch (e) {
        alert("네트워크 오류!");
    } finally {
        if (saveBtn) {
            saveBtn.textContent = originalText;
            saveBtn.classList.remove('is-busy');
        }
    }
}

// ==========================================
// 9. 공통 정보 수정 & 설정
// ==========================================
function openEditInfo(type, id, name, brand) {
    document.getElementById('edit-info-type').value = type;
    document.getElementById('edit-info-id').value = id;
    document.getElementById('edit-info-name').value = name;
    document.getElementById('edit-info-brand').value = brand;
    document.getElementById('edit-info-status').innerText = "";
    switchView('edit-info', null);
}

function cancelEditInfo() {
    const type = document.getElementById('edit-info-type').value;
    switchView(type === 'wardrobe' ? 'perfume-detail' : 'wish-detail', null);
}

async function submitEditInfo() {
    const type = document.getElementById('edit-info-type').value;
    const pageId = document.getElementById('edit-info-id').value;
    const name = document.getElementById('edit-info-name').value.trim();
    const brand = document.getElementById('edit-info-brand').value.trim();
    const btn = document.getElementById('edit-info-submit-btn');
    const statusMsg = document.getElementById('edit-info-status');

    if (!name) {
        statusMsg.innerText = "[ERROR] Name is required.";
        return;
    }

    btn.innerText = "Updating...";
    statusMsg.innerText = "Saving to Notion DB...";
    const endpoint = type === 'wardrobe' ? `/api/v1/perfumes/${pageId}` : `/api/v1/wishlist/${pageId}`;

    try {
        const res = await fetchWithAuth(endpoint, {
            method: "PATCH", headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name: name, brand: brand })
        });

        if (res.ok) {
            btn.classList.add('success');
            btn.innerText = "UPDATED";
            statusMsg.innerText = "[SUCCESS] Details updated.";
            setTimeout(() => {
                btn.classList.remove('success');
                btn.innerText = "Update Data";
                if (type === 'wardrobe') {
                    fetchWardrobe().then(() => openPerfumeDetail(pageId));
                } else {
                    fetchWishlist().then(() => openWishDetail(pageId));
                }
            }, 1000);
        } else {
            statusMsg.innerText = "[ERROR] Update failed.";
            btn.innerText = "Update Data";
        }
    } catch (e) {
        statusMsg.innerText = "[ERROR] Network failure.";
        btn.innerText = "Update Data";
    }
}

async function openSettingsView() {
    switchView('settings', null);
    document.getElementById('setting-status').innerText = "Loading user info...";
    document.getElementById('setting-pw').value = '';

    try {
        const res = await fetchWithAuth("/api/v1/users/me");
        if (res.ok) {
            const user = await res.json();
            document.getElementById('setting-id').value = user.userId || '';
            document.getElementById('setting-name').value = user.name || '';
            document.getElementById('setting-loc').value = user.defaultLocation || '';
            document.getElementById('setting-noti').checked = user.notiEnabled === true;
            document.getElementById('setting-season-bar').checked = localStorage.getItem('season_style_bar') !== 'false';
            document.getElementById('setting-status').innerText = "";
        } else {
            document.getElementById('setting-status').innerText = "[ERROR] Failed to load.";
        }
    } catch (e) {
        document.getElementById('setting-status').innerText = "[ERROR] Network failure.";
    }
}

async function submitSettings() {
    const elName = document.getElementById('setting-name');
    const elLoc = document.getElementById('setting-loc');
    const elPw = document.getElementById('setting-pw');
    const elNoti = document.getElementById('setting-noti');
    const elSeasonBar = document.getElementById('setting-season-bar');

    const name = elName ? elName.value.trim() : '';
    const loc = elLoc ? elLoc.value.trim() : '';
    const pw = elPw ? elPw.value : '';
    const noti = elNoti ? elNoti.checked : false;

    // 로컬 스토리지에 UI 설정 업데이트
    if (elSeasonBar) {
        localStorage.setItem('season_style_bar', elSeasonBar.checked);
    }

    const statusMsg = document.getElementById('setting-status');
    const btn = document.getElementById('setting-submit-btn');

    // 💡 에디터의 빨간 줄(null 경고)을 없애는 안전한 방어 로직
    if (statusMsg) statusMsg.innerText = "Updating profile...";
    if (btn) btn.innerText = "Saving...";

    const payload = { name: name, defaultLocation: loc, notiEnabled: noti };
    if (pw) payload.password = pw;

    try {
        const res = await fetchWithAuth("/api/v1/users/me", {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            if (btn) {
                btn.classList.add('success');
                btn.innerText = "UPDATED";
            }
            if (statusMsg) statusMsg.innerText = "[SUCCESS] Profile updated.";

            if (pw) {
                alert("비밀번호가 변경되었습니다. 다시 로그인해주세요.");
                logout();
                return;
            }
            setTimeout(() => {
                if (btn) {
                    btn.classList.remove('success');
                    btn.innerText = "Save Changes";
                }
                switchView('main', document.querySelector('.nav-item.main-tab'));
                fetchRealWeather();

                // 설정 변경(Bar/뱃지) 후 옷장과 위시리스트 즉각 리렌더링
                if (typeof renderWardrobeGrid === 'function') renderWardrobeGrid();
                if (typeof fetchWishlist === 'function') fetchWishlist();
            }, 1500);
        } else {
            if (statusMsg) statusMsg.innerText = "[ERROR] Update failed.";
            if (btn) btn.innerText = "Save Changes";
        }
    } catch (e) {
        if (statusMsg) statusMsg.innerText = "[ERROR] Network failure.";
        if (btn) btn.innerText = "Save Changes";
    }
}

// 별점 평가 모듈
const starContainer = document.getElementById('star-rating-container');
const starFill = document.getElementById('star-rating-fill');
const rateInput = document.getElementById('edit-rate');
const rateDisplay = document.getElementById('edit-rate-display');

function handleStarInteraction(e) {
    const rect = starContainer.getBoundingClientRect();
    const clientX = e.type.includes('touch') ? e.touches[0].clientX : e.clientX;
    const x = clientX - rect.left;
    const percent = x / rect.width;
    let score = Math.ceil(percent * 10) / 2;
    if (score < 0.5) score = 0.5;
    if (score > 5) score = 5;
    updateStarUI(score);
}

if (starContainer) {
    starContainer.addEventListener('mousedown', handleStarInteraction);
    starContainer.addEventListener('touchstart', handleStarInteraction, { passive: true });
    starContainer.addEventListener('touchmove', handleStarInteraction, { passive: true });
}

function updateStarUI(score) {
    const num = parseFloat(score);
    if (isNaN(num) || num <= 0) {
        starFill.style.width = '0%';
        rateInput.value = '';
        rateDisplay.innerText = '평가 안함';
        rateDisplay.style.color = 'var(--accent-color)';
        return;
    }
    starFill.style.width = `${(num / 5) * 100}%`;
    rateInput.value = num;
    rateDisplay.innerText = `${num.toFixed(1)} / 5.0`;
    rateDisplay.style.color = '#f59e0b';
}

async function fetchNoteAnalytics() {
    const container = document.getElementById('analytics-container');
    if (!container) return;

    container.style.opacity = '0.4';          // 갱신 중 표시

    try {
        const res = await fetchWithAuth(`/api/analytics/notes?t=${Date.now()}`);
        if (res.ok) {
            const data = await res.json();
            renderNoteAnalytics(data.climateAnalytics);
        } else {
            container.innerHTML = `[ERROR] 통계 데이터를 불러오지 못했습니다.`;
        }
    } catch (e) {
        container.innerHTML = `[ERROR] 네트워크 오류.`;
    } finally {
        container.style.opacity = '1';
    }
}

function renderNoteAnalytics(climateAnalytics) {
    const container = document.getElementById('analytics-container');
    const radarContainer = document.getElementById('radar-chart-container');

    if (!climateAnalytics || Object.keys(climateAnalytics).length === 0) {
        const scored = globalRecentLogs.filter(l =>
            l.rate && l.rate !== 'null' && parseFloat(l.rate) > 0 &&
            l.temp !== null && l.temp !== ''
        ).length;

        container.innerHTML = `
            <div style="line-height:1.7;">
                아직 분석할 데이터가 부족합니다.<br>
                <span style="color:var(--text-color); font-weight:bold;">
                    별점 + 온습도가 모두 있는 로그: ${scored}건
                </span><br>
                <span style="font-size:11px;">
                    · 같은 노트가 같은 날씨에서 <b>3회 이상</b> 쌓여야 표시됩니다<br>
                    · 별점 없는 로그, 온습도 없는 로그는 집계에서 제외됩니다
                </span>
            </div>`;

        radarContainer.style.display = 'none';

        return;
    }

    // 💡 1. 레이더 차트를 위한 노트별 종합 평점 계산
    const noteScores = {};
    for (const [climate, stats] of Object.entries(climateAnalytics)) {
        stats.goldenNotes.forEach(note => {
            if (!noteScores[note.noteName]) {
                noteScores[note.noteName] = { sum: 0, count: 0 };
            }
            noteScores[note.noteName].sum += note.averageRating * note.wearingCount;
            noteScores[note.noteName].count += note.wearingCount;
        });
    }

    const aggregated = Object.keys(noteScores).map(name => ({
        name: name,
        avg: noteScores[name].sum / noteScores[name].count,
        count: noteScores[name].count
    })).sort((a, b) => b.count - a.count || b.avg - a.avg); // 많이 뿌린 순 정렬

    const top6 = aggregated.slice(0, 6);

    // 최소 3개의 노트가 있어야 다각형(육각형/삼각형)이 그려짐
    if (top6.length >= 3) {
        radarContainer.style.display = 'block';
        const ctx = document.getElementById('noteRadarChart').getContext('2d');
        if (radarChartInstance) radarChartInstance.destroy();

        radarChartInstance = new Chart(ctx, {
            type: 'radar',
            data: {
                labels: top6.map(n => n.name),
                datasets: [{
                    label: 'Average Rating',
                    data: top6.map(n => n.avg),
                    backgroundColor: 'rgba(16, 185, 129, 0.15)', // --success-color 투명도
                    borderColor: '#10b981',
                    pointBackgroundColor: '#10b981',
                    pointBorderColor: '#fff',
                    borderWidth: 2,
                    pointRadius: 4
                }]
            },
            options: {
                scales: {
                    r: {
                        min: 0, max: 5,
                        ticks: { stepSize: 1, display: false },
                        grid: { color: 'rgba(0,0,0,0.05)' },
                        angleLines: { color: 'rgba(0,0,0,0.05)' },
                        pointLabels: {
                            font: { size: 10, family: "'Courier Prime', sans-serif", weight: 'bold' },
                            color: '#1a1a1a'
                        }
                    }
                },
                plugins: { legend: { display: false }, tooltip: { enabled: true } },
                maintainAspectRatio: false
            }
        });
    } else {
        radarContainer.style.display = 'none';
    }

    const climateNames = {
        'COLD': '❄️ COLD (15°C 미만)',
        'WARM_BREEZY': '🍃 WARM & BREEZY (쾌적한 날씨)',
        'WARM_HUMID': '💧 WARM & HUMID (습한 날씨)',
        'HOT': '🔥 HOT (25°C 이상)'
    };

    let html = '';
    for (const [climate, stats] of Object.entries(climateAnalytics)) {
        if (stats.goldenNotes.length === 0 && stats.warningNotes.length === 0) continue;

        html += `<div style="margin-bottom: 18px;">`;
        html += `<div class="note-category" style="color: var(--text-color); margin-bottom: 8px;">${climateNames[climate] || climate}</div>`;

        if (stats.goldenNotes.length > 0) {
            html += `<div style="margin-bottom: 6px;">`;
            // 상위 5개까지만 노출
            stats.goldenNotes.slice(0, 5).forEach(note => {
                const c = note.count ? `<span style="opacity:.65; font-weight:normal;"> ×${note.count}</span>` : '';
                html += `<span class="note-chip golden">✨ ${note.noteName} ${note.averageRating}${c}</span>`;
            });
            html += `</div>`;
        }

        if (stats.warningNotes.length > 0) {
            html += `<div>`;
            // 상위 5개까지만 노출
            stats.warningNotes.slice(0, 5).forEach(note => {
                html += `<span class="note-chip warning">⚠️ ${note.noteName} (${note.averageRating})</span>`;
            });
            html += `</div>`;
        }
        html += `</div>`;
    }

    container.innerHTML = html || '분석 가능한 데이터가 부족합니다.';
}

// 옷장 데이터를 바탕으로 노트 일치율(%)을 계산하는 객관적 뱃지 생성기
function generateWishlistBadgeHtml(notesObj) {
    if (!globalWardrobeData || globalWardrobeData.length === 0) return '';

    // 1. 크롤링된 새 향수의 노트 추출 및 중복 제거
    const newNotes = [];
    ['top', 'middle', 'base', 'general'].forEach(k => {
        if (notesObj[k]) newNotes.push(...notesObj[k].map(n => n.toLowerCase().trim()));
    });

    const uniqueNewNotes = [...new Set(newNotes)];
    const totalNotes = uniqueNewNotes.length;
    if (totalNotes === 0) return '';

    // 2. 내 옷장에 있는 모든 고유 노트 수집 (빈도수 계산 대신 존재 여부만 파악)
    const wardrobeNotesSet = new Set();
    globalWardrobeData.forEach(p => {
        if (!p.notes) return;
        ['top', 'middle', 'base', 'general'].forEach(k => {
            if (p.notes[k]) {
                p.notes[k].forEach(n => wardrobeNotesSet.add(n.toLowerCase().trim()));
            }
        });
    });

    // 3. 겹치는 노트와 새로운 노트 분류
    const overlappingNotes = [];
    const newDiscoveryNotes = [];

    uniqueNewNotes.forEach(n => {
        if (wardrobeNotesSet.has(n)) overlappingNotes.push(n);
        else newDiscoveryNotes.push(n);
    });

    // 4. 일치율 계산
    const overlapPercent = Math.round((overlappingNotes.length / totalNotes) * 100);

    // 5. 비율에 따른 테마 색상 및 텍스트 설정
    let themeColor = '';
    let bgColor = '';
    let titleText = '';

    if (overlapPercent >= 60) {
        // 높은 일치율 (초록)
        themeColor = 'var(--success-color)'; // #10b981
        bgColor = 'rgba(16, 185, 129, 0.1)';
        titleText = `🌿 내 옷장과 ${overlapPercent}% 일치`;
    } else if (overlapPercent >= 30) {
        // 중간 일치율 (파랑)
        themeColor = '#3b82f6';
        bgColor = 'rgba(59, 130, 246, 0.1)';
        titleText = `🌊 내 옷장과 ${overlapPercent}% 일치`;
    } else {
        // 낮은 일치율 (보라)
        themeColor = '#8b5cf6';
        bgColor = 'rgba(139, 92, 246, 0.1)';
        titleText = `🚀 내 옷장과 ${overlapPercent}% 일치`;
    }

    const capitalize = (s) => s.charAt(0).toUpperCase() + s.slice(1);
    const formatNotes = (arr) => arr.length > 0 ? arr.map(capitalize).join(', ') : '없음';

    // 6. UI 렌더링
    let html = `<div style="margin-bottom: 12px; padding: 10px; background: ${bgColor}; border-left: 3px solid ${themeColor}; border-radius: 4px;">
                    <strong style="color: ${themeColor}; font-size: 12px;">${titleText}</strong><br>`;

    if (overlappingNotes.length > 0) {
        html += `<div style="font-size: 11px; color: var(--text-color); margin-top: 6px;">
                    <b>보유 노트:</b> <span style="color: var(--accent-color);">${formatNotes(overlappingNotes)}</span>
                 </div>`;
    }
    if (newDiscoveryNotes.length > 0) {
        html += `<div style="font-size: 11px; color: var(--text-color); margin-top: 3px;">
                    <b>미보유 노트:</b> <span style="color: var(--accent-color);">${formatNotes(newDiscoveryNotes)}</span>
                 </div>`;
    }
    html += `</div>`;

    return html;
}

async function refreshSummary(btn) {
    if (btn) { btn.classList.add('is-busy'); btn.textContent = 'refreshing...'; }
    try {
        await fetchSummary();
        await fetchNoteAnalytics();   // ← 하단 블록도 같이 갱신
    } finally {
        if (btn) { btn.classList.remove('is-busy'); btn.textContent = 'refresh'; }
    }
}

function toLocalDateStr(d) {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
}

// 💡 수동 기록 모달 열기 (오늘 날짜 세팅)
function openManualLogForm() {
    document.getElementById('manual-date').value = toLocalDateStr(new Date());
    document.getElementById('manual-temp').value = '';
    document.getElementById('manual-hum').value = '';
    fetchPerfumeList();
    switchView('manual-log', null);
}

// 렌더링된 로그를 pageId로 보관 (인라인 문자열 이스케이프 문제 제거)
const logRegistry = {};

function registerLog(log, extra = {}) {
    const prev = logRegistry[log.pageId] || {};
    // 빈 값이 기존 값을 덮지 않도록 정리
    const clean = Object.fromEntries(
        Object.entries(log).filter(([, v]) => v !== null && v !== undefined && v !== '')
    );
    logRegistry[log.pageId] = { ...prev, ...clean, ...extra };
}

// 로그 → 옷장 향수 ID 해석 (클릭 시점에 실행 = 로딩 순서 무관)
function resolvePerfumeId(log) {
    if (!log) return null;
    if (log.perfumeId) return log.perfumeId;          // 백엔드가 주면 우선 사용
    const key = (log.perfumeName || '').trim().toLowerCase();
    if (!key) return null;
    const hit = globalWardrobeData.find(p => (p.name || '').trim().toLowerCase() === key);
    return hit ? hit.id : null;
}

// 로그 카드 본문 클릭 → 착향 기록 수정
function openEditLogById(pageId) {
    const log = logRegistry[pageId];
    if (!log) return;
    const shortDate = log.date ? log.date.split('T')[0] : 'N/A';
    openEditLog(log.pageId, log.perfumeName, shortDate,
        log.weather, log.temp, log.humidity, log.rate, log.comment);
}

// 사진/향수명 클릭 → 향수 상세
function openDetailFromLog(pageId) {
    const log = logRegistry[pageId];
    if (!log) return;

    const pid = resolvePerfumeId(log);
    if (!pid) {
        const nm = (log.perfumeName || '').trim();
        alert(nm
            ? `'${nm}'은(는) 현재 옷장에 없어 상세 정보를 볼 수 없습니다.`
            : `이 기록의 향수 정보를 찾을 수 없습니다.`);
        return;
    }
    const cur = document.querySelector('.view-section.active')?.id.replace('view-', '') || 'main';
    openPerfumeDetail(pid, cur);
}

function setAuthMode(mode) {
    const isSignup = mode === 'signup';
    const btn    = document.getElementById('auth-submit-btn');
    const toggle = document.getElementById('auth-toggle-btn');

    document.getElementById('signup-fields').style.display = isSignup ? 'block' : 'none';

    btn.dataset.label = isSignup ? 'SIGN UP' : 'LOGIN';
    btn.innerText = btn.dataset.label;
    toggle.innerText = isSignup ? 'Switch to Login' : 'Switch to Sign Up';

    btn.disabled = false;
    btn.classList.remove('success');
    document.getElementById('auth-status').innerText = '';
}

function toggleAuthMode() {
    const isSignup = document.getElementById('signup-fields').style.display !== 'none';
    setAuthMode(isSignup ? 'login' : 'signup');
}

['auth-id', 'auth-pw', 'auth-name', 'auth-loc'].forEach(id => {
    const el = document.getElementById(id);
    if (!el) return;
    el.addEventListener('keydown', e => {
        if (e.key === 'Enter') { e.preventDefault(); submitAuth(); }
    });
});

// 계절 필터 상태 업데이트
function setSeasonFilter(season, btnElement) {
    currentSeasonFilter = season;
    document.querySelectorAll('.season-btn').forEach(b => b.classList.remove('active'));
    if (btnElement) btnElement.classList.add('active');
    applyWardrobeFilter();
}

// 텍스트 입력 및 필터 적용 트리거
function applyWardrobeFilter() {
    const searchInput = document.getElementById('wardrobe-search');
    currentSearchQuery = searchInput ? searchInput.value.toLowerCase().trim() : '';

    // 순서 꼬임 방지: 필터 작동 중일 때는 기본 동작들을 막거나 제어
    renderWardrobeGrid();
}


/* ========================================
   SEASON METER
======================================== */
const SEASON_ORDER  = ['SPRING', 'SUMMER', 'FALL', 'WINTER'];
const SEASON_CUTOFF = 0.5;                     // 최댓값의 50% 이상 → 추천

const SEASON_META = {
    SPRING: { ko: '봄',   icon: '🌸', cls: 'is-spring' },
    SUMMER: { ko: '여름', icon: '☀️', cls: 'is-summer' },
    FALL:   { ko: '가을', icon: '🍂', cls: 'is-fall'   },
    WINTER: { ko: '겨울', icon: '❄️', cls: 'is-winter' }
};

function fmtVotes(n) {
    if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k';
    return String(n);
}

/** season_stats 정규화 (문자열/객체/AUTUMN/음수 모두 방어) */
function parseSeasonStats(raw) {
    if (!raw) return null;
    let obj = raw;
    if (typeof raw === 'string') {
        try { obj = JSON.parse(raw); } catch (e) { return null; }
    }
    if (typeof obj !== 'object' || Array.isArray(obj)) return null;

    const out = {};
    Object.keys(obj).forEach(k => {
        let key = String(k).trim().toUpperCase();
        if (key === 'AUTUMN') key = 'FALL';
        if (!SEASON_META[key]) return;
        const v = Number(obj[k]);
        if (Number.isFinite(v) && v > 0) out[key] = (out[key] || 0) + v;
    });
    return Object.keys(out).length ? out : null;
}

/** stats 우선, 없으면 seasons 배열 균등 폴백 → { val, even } */
function resolveSeasonValues(seasons, stats) {
    const parsed = parseSeasonStats(stats);
    if (parsed) return { val: parsed, even: false };

    const val = {};
    (seasons || []).forEach(s => {
        let k = String(s).trim().toUpperCase();
        if (k === 'AUTUMN') k = 'FALL';
        if (SEASON_META[k]) val[k] = 1;
    });
    return { val, even: true };
}

/** 추천 계절 Set (필터·정렬 공용) */
function getRecommendedSeasons(seasons, stats) {
    const { val } = resolveSeasonValues(seasons, stats);
    const keys = Object.keys(val);
    if (!keys.length) return new Set();
    const max = Math.max(...keys.map(k => val[k]));
    return new Set(keys.filter(k => val[k] / max >= SEASON_CUTOFF));
}

/**
 * 계절 UI 생성 (바 모드 / 배지 모드 자동 분기)
 * @param {string[]} seasons
 * @param {object|string} [stats]  season_stats
 * @param {object} [opts] { size:'lg', force:'bar'|'tag' }
 */
function generateSeasonUI(seasons, stats, opts) {
    const o = opts || {};
    const { val, even } = resolveSeasonValues(seasons, stats);

    const keys = Object.keys(val);
    if (!keys.length) return '';

    const total = keys.reduce((s, k) => s + val[k], 0);
    const max   = Math.max(...keys.map(k => val[k]));
    if (total <= 0 || max <= 0) return '';

    const pass  = k => (val[k] || 0) / max >= SEASON_CUTOFF;
    const pctOf = k => (val[k] || 0) / total * 100;

    const useBar = o.force
        ? o.force === 'bar'
        : localStorage.getItem('season_style_bar') !== 'false';

    let inner = '';

    if (useBar) {
        const MIN_GROW = 3.5;   // 데이터 없는 계절의 잔여 슬롯
        const FLOOR    = 6;     // 미세 비중도 최소한 보이게

        const segs = SEASON_ORDER.map(k => {
            const m = SEASON_META[k];
            const v = val[k] || 0;

            if (v <= 0) {
                return `<span class="season-bar__seg is-off" style="flex-grow:${MIN_GROW}" title="${m.ko} · 데이터 없음"></span>`;
            }
            const pct  = pctOf(k);
            const grow = Math.max(pct, FLOOR);
            const tip  = even ? m.ko : `${m.ko} · ${pct.toFixed(1)}% (${fmtVotes(val[k])}표)`;

            return `<span class="season-bar__seg ${m.cls}${pass(k) ? '' : ' is-weak'}" style="flex-grow:${grow.toFixed(2)}" title="${tip}"></span>`;
        }).join('');

        const label = SEASON_ORDER.filter(pass).map(k => SEASON_META[k].ko).join(', ');
        inner = `<div class="season-bar" role="img" aria-label="추천 계절: ${label || '없음'}">${segs}</div>`;

    } else {
        const tags = SEASON_ORDER.filter(pass).map(k => {
            const m   = SEASON_META[k];
            const top = val[k] >= max ? ' is-top' : '';
            const pct = even ? '' : `<span class="season-tag__pct">${Math.round(pctOf(k))}%</span>`;
            return `<span class="season-tag ${m.cls}${top}">${m.icon} ${m.ko}${pct}</span>`;
        }).join('');
        if (!tags) return '';
        inner = `<div class="season-tags">${tags}</div>`;
    }

    return `<div class="season-meter${o.size === 'lg' ? ' season-meter--lg' : ''}">${inner}</div>`;
}


// 착향 로그가 변경됐을 때 항상 이걸 호출
function refreshAfterLogChange() {
    fetchRecentLogs();
    fetchSummary();
    fetchNoteAnalytics();
}

// ==========================================
// 10. 앱 부트스트랩 및 라이프사이클
// ==========================================
function initializeAppData() {
    fetchRealWeather();
    fetchBrands();
    fetchRecentLogs();
    fetchPerfumeList();
    fetchWardrobe();
    fetchSummary();
    fetchWishlist();
    fetchNoteAnalytics();
}

window.addEventListener('DOMContentLoaded', () => {

    const today = new Date();
    const yyyy = today.getFullYear();
    const mm = String(today.getMonth() + 1).padStart(2, '0');
    const dd = String(today.getDate()).padStart(2, '0');
    document.getElementById('current-date').innerText = `${yyyy}.${mm}.${dd}`;

    checkAuth();

    const urlParams = new URLSearchParams(window.location.search);
    const scannedUid = urlParams.get('uid') || urlParams.get('id');
    if (scannedUid) {
        window.history.replaceState({}, document.title, window.location.pathname);
        setTimeout(() => handleIosNfcScan(scannedUid), 500);
    }
});

if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('/sw.js')
            .then(reg => console.log('Service Worker 등록 완료:', reg.scope))
            .catch(err => console.log('Service Worker 등록 실패:', err));
    });
}