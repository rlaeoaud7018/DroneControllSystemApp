// 브라우저는 "같은 호스트"로는 동시 연결을 6개까지만 허용하는데, 캠 라이브뷰는 MJPEG라
// 연결이 끝나지 않고 계속 유지된다 -> 캠이 7대 이상 RUNNING이면 7번째부터는 연결 슬롯이
// 없어서 영원히 pending 상태로 멈춰버림(실제로 캠 7대 동시 확인 중 1대가 이 문제로 안 뜸).
// 루프백 호스트명을 캠마다 돌려써서 브라우저가 서로 다른 origin으로 보게 하면 호스트별로
// 6개씩 별도 슬롯을 받는다(동일 로컬 서버를 가리키므로 기능상 차이는 없음).
// (IPv6 루프백 [::1]은 analysis-server가 0.0.0.0(IPv4 전용)에만 바인딩돼 있어 연결이 안 돼서 제외함)
const ANALYSIS_SERVER_HOSTS = ["localhost", "127.0.0.1"];
function analysisServerBaseFor(cameraId) {
	const host = ANALYSIS_SERVER_HOSTS[cameraId % ANALYSIS_SERVER_HOSTS.length];
	return `http://${host}:5050`;
}
const STATS_WINDOW_MINUTES = 10;

// 혼잡 단계 임계치. 실제 event_thresholds 기능이 생기기 전까지 쓰는 임시 기준값.
const DENSITY_CAUTION = 0.01;
const DENSITY_DANGER = 0.02;

const DONUT_COLORS = ["#4fc3f7", "#ffd166", "#6ee89a", "#ff8a8a", "#b39ddb", "#f48fb1"];
const LEVEL_COLOR = { normal: "#4fc3f7", caution: "#ffd166", danger: "#ff5c5c" };

let zones = [];
let cameras = []; // { ...camera, zoneName, zoneId, areaM2 }
let selectedCameraId = null;
let latestStatsByZoneId = {}; // zoneId -> 최신 ZoneMinuteStat
let statsWindowByZoneId = {}; // zoneId -> ZoneMinuteStat[] (최근 10분)
let eventCountByZoneId = {};
let chart = null;
let donutChart = null;
let pollTimer = null;
let eventPollTimer = null;
let map = null;
let mapRightClickListener = null;
let markersByZoneId = {};
let currentUser = null; // {id, loginId, name, role}
let openEventCount = 0;

let currentDongId = null;
let currentGuName = "-";
let currentDongName = "-";

let statsNode = null;
let statsMetric = "count";
let splitView = "all";
let checkedCameraIds = new Set();

function toIsoNoOffset(date) {
	const pad = (n) => String(n).padStart(2, "0");
	return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
		`T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

async function fetchJson(url, options) {
	const res = await fetch(url, options);
	if (res.status === 204) {
		return null;
	}
	return res.json();
}

function congestionLevel(density) {
	if (density == null) return "normal";
	if (Number(density) >= DENSITY_DANGER) return "danger";
	if (Number(density) >= DENSITY_CAUTION) return "caution";
	return "normal";
}

function zoneGeo(zone) {
	try {
		return JSON.parse(zone.geometry);
	} catch (e) {
		return null;
	}
}

// ---------- 로그인 / 회원가입 ----------

async function checkAuth() {
	const res = await fetch("api/v1/users/me");

	if (res.status === 401) {
		showOverlay("loginOverlay");
		return;
	}

	currentUser = await res.json();

	if (currentUser.role === "PRE_USER") {
		showOverlay("pendingOverlay");
		return;
	}

	hideOverlays();
	renderUserInfo();
	initStatsPanel();
	await loadRegionFilters();
	startEventPolling();
	startPolling();
}

function showOverlay(id) {
	document.getElementById("loginOverlay").classList.toggle("active", id === "loginOverlay");
	document.getElementById("pendingOverlay").classList.toggle("active", id === "pendingOverlay");
}

function hideOverlays() {
	document.getElementById("loginOverlay").classList.remove("active");
	document.getElementById("pendingOverlay").classList.remove("active");
}

function renderUserInfo() {
	document.getElementById("userInfo").innerHTML = `
		${currentUser.name} <span class="role-badge">${currentUser.role}</span>
		<button id="logoutBtn">로그아웃</button>
	`;
	document.getElementById("logoutBtn").addEventListener("click", logout);
	document.getElementById("adminTabBtn").style.display = isAdminUser() ? "inline-block" : "none";
}

async function logout() {
	await fetch("api/v1/auth/logout", { method: "POST" });
	currentUser = null;
	if (pollTimer) clearInterval(pollTimer);
	if (eventPollTimer) clearInterval(eventPollTimer);
	location.reload();
}

document.querySelectorAll(".auth-tab-btn").forEach((btn) => {
	btn.addEventListener("click", () => {
		document.querySelectorAll(".auth-tab-btn").forEach((b) => b.classList.toggle("active", b === btn));
		document.getElementById("loginForm").classList.toggle("active", btn.dataset.authtab === "login");
		document.getElementById("registerForm").classList.toggle("active", btn.dataset.authtab === "register");
	});
});

document.getElementById("loginForm").addEventListener("submit", async (e) => {
	e.preventDefault();
	const loginId = document.getElementById("loginLoginId").value;
	const password = document.getElementById("loginPassword").value;
	const errorEl = document.getElementById("loginError");
	errorEl.textContent = "";

	const res = await fetch("api/v1/auth/login", {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({ loginId, password }),
	});

	if (res.ok) {
		checkAuth();
	} else {
		const err = await res.json();
		errorEl.textContent = err.message || "로그인에 실패했습니다.";
	}
});

document.getElementById("registerForm").addEventListener("submit", async (e) => {
	e.preventDefault();
	const body = {
		loginId: document.getElementById("regLoginId").value,
		password: document.getElementById("regPassword").value,
		name: document.getElementById("regName").value,
		email: document.getElementById("regEmail").value,
		phone: document.getElementById("regPhone").value,
	};
	const errorEl = document.getElementById("registerError");
	errorEl.textContent = "";

	const res = await fetch("api/v1/users", {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify(body),
	});

	if (res.ok) {
		document.getElementById("loginLoginId").value = body.loginId;
		document.getElementById("loginPassword").value = body.password;
		document.querySelector('.auth-tab-btn[data-authtab="login"]').click();
		document.getElementById("loginForm").requestSubmit();
	} else {
		const err = await res.json();
		errorEl.textContent = err.message || "가입에 실패했습니다.";
	}
});

document.getElementById("pendingLogoutBtn").addEventListener("click", logout);

function isAdminUser() {
	return currentUser && (currentUser.role === "ADMIN" || currentUser.role === "SUPER_ADMIN");
}

// ---------- 지역 필터 (시 / 구 / 동) ----------

function fillRegionSelect(selectEl, list, placeholder) {
	selectEl.innerHTML = `<option value="">${placeholder}</option>` +
		list.map((r) => `<option value="${r.id}" data-level="${r.level}" data-name="${r.name}">${r.name}</option>`).join("");
	if (list.length > 0) {
		selectEl.value = String(list[0].id);
	}
}

async function loadRegionFilters() {
	const top = await fetchJson("api/v1/regions");
	fillRegionSelect(document.getElementById("filterCity"), top || [], "시 선택");
	await onCityChange();
}

async function onCityChange() {
	const citySel = document.getElementById("filterCity");
	const guSel = document.getElementById("filterGu");
	const dongSel = document.getElementById("filterDong");
	const opt = citySel.selectedOptions[0];

	if (!opt || !opt.value) {
		guSel.innerHTML = '<option value="">구 선택</option>';
		dongSel.innerHTML = '<option value="">동 선택</option>';
		return;
	}

	if (opt.dataset.level === "DONG") {
		guSel.innerHTML = '<option value="">-</option>';
		dongSel.innerHTML = `<option value="${opt.value}" selected>${opt.dataset.name}</option>`;
		applyDong(opt.value, "-", opt.dataset.name);
		return;
	}

	const children = await fetchJson(`api/v1/regions?parentId=${opt.value}`);
	fillRegionSelect(guSel, children || [], "구 선택");
	await onGuChange();
}

async function onGuChange() {
	const citySel = document.getElementById("filterCity");
	const guSel = document.getElementById("filterGu");
	const dongSel = document.getElementById("filterDong");
	const opt = guSel.selectedOptions[0];

	if (!opt || !opt.value) {
		dongSel.innerHTML = '<option value="">동 선택</option>';
		return;
	}

	if (opt.dataset.level === "DONG") {
		dongSel.innerHTML = `<option value="${opt.value}" selected>${opt.dataset.name}</option>`;
		applyDong(opt.value, citySel.selectedOptions[0]?.dataset.name || "-", opt.dataset.name);
		return;
	}

	const children = await fetchJson(`api/v1/regions?parentId=${opt.value}`);
	fillRegionSelect(dongSel, children || [], "동 선택");
	onDongChange();
}

function onDongChange() {
	const guSel = document.getElementById("filterGu");
	const dongSel = document.getElementById("filterDong");
	const opt = dongSel.selectedOptions[0];
	if (!opt || !opt.value) return;
	applyDong(opt.value, guSel.selectedOptions[0]?.dataset.name || "-", opt.dataset.name);
}

function applyDong(dongId, guName, dongName) {
	currentDongId = Number(dongId);
	currentGuName = guName;
	currentDongName = dongName;
	loadAll();
}

document.getElementById("filterCity").addEventListener("change", onCityChange);
document.getElementById("filterGu").addEventListener("change", onGuChange);
document.getElementById("filterDong").addEventListener("change", onDongChange);

document.getElementById("regionEditBtn").addEventListener("click", () => {
	alert("지역 등록/수정 화면은 준비 중입니다.");
});

// ---------- 데이터 로딩 ----------

async function loadAll() {
	if (currentDongId == null) return;

	zones = await fetchJson(`api/v1/zones?dongId=${currentDongId}`);

	cameras = [];
	for (const zone of zones) {
		const zoneCameras = await fetchJson(`api/v1/cameras?zoneId=${zone.id}`);
		for (const camera of zoneCameras) {
			cameras.push({ ...camera, zoneName: zone.name, zoneId: zone.id, areaM2: zone.areaM2 });
		}
	}

	await refreshAllZoneStats();
	renderCameraGrid();
	renderMapMarkers();
}

async function refreshAllZoneStats() {
	const to = new Date();
	const from = new Date(to.getTime() - STATS_WINDOW_MINUTES * 60 * 1000);

	for (const zone of zones) {
		const stats = await fetchJson(
			`api/v1/zones/${zone.id}/stats?from=${toIsoNoOffset(from)}&to=${toIsoNoOffset(to)}`
		);
		statsWindowByZoneId[zone.id] = stats || [];
		latestStatsByZoneId[zone.id] = (stats && stats.length > 0) ? stats[stats.length - 1] : null;
	}

	renderSummary();
	renderDonut();
	renderDensityDetail();
	renderFlowDetail();
	renderMovementDetail();
	renderPeakDetail();
	renderAnomalyDetail();
	renderStatsStatusBar();
}

// ---------- 통계 탭: 상단 상태바(실시간 감시 중 · 카메라 연결 수) ----------

function renderStatsStatusBar() {
	const liveEl = document.getElementById("statsLiveCameras");
	if (!liveEl) return;
	liveEl.textContent = cameras.filter((c) => c.status === "RUNNING").length;
	document.getElementById("statsTotalCameras").textContent = cameras.length;
	document.getElementById("statsOverviewTime").textContent = new Date().toLocaleTimeString("ko-KR");
}


// ---------- 통계 패널 ----------

function initStatsPanel() {
	const tpl = document.getElementById("statsPanelTemplate");
	statsNode = tpl.content.firstElementChild.cloneNode(true);
	document.getElementById("statsSidebar").appendChild(statsNode);

	statsNode.querySelectorAll(".stats-metric-tab").forEach((btn) => {
		btn.addEventListener("click", () => {
			statsNode.querySelectorAll(".stats-metric-tab").forEach((b) => b.classList.toggle("active", b === btn));
			statsMetric = btn.dataset.metric;
			renderDonut();
		});
	});

	statsNode.querySelectorAll(".stats-block.collapsible").forEach((block) => {
		const toggle = block.querySelector(".stats-block-toggle");
		toggle.addEventListener("click", () => {
			block.classList.toggle("collapsed");
			const collapsed = block.classList.contains("collapsed");
			toggle.querySelector(".chevron").textContent = collapsed ? "▸" : "▾";
			if (!collapsed && block.dataset.section === "threshold") {
				renderThresholdDetail();
			}
		});
	});

	statsNode.querySelector(".statsCollapseBtn").addEventListener("click", closeStatsSidebar);
	statsNode.querySelector(".statsRefreshInterval").addEventListener("change", startPolling);
}

function renderSummary() {
	const latestValues = Object.values(latestStatsByZoneId).filter(Boolean);

	const totalCount = latestValues.reduce((sum, s) => sum + Number(s.countAvg), 0);
	const avgDensity = latestValues.length
		? latestValues.reduce((sum, s) => sum + Number(s.density), 0) / latestValues.length
		: 0;
	const maxCount = latestValues.length ? Math.max(...latestValues.map((s) => s.countMax)) : 0;

	let worstZoneName = "-";
	let worstDensity = -1;
	for (const zone of zones) {
		const stat = latestStatsByZoneId[zone.id];
		if (stat && Number(stat.density) > worstDensity) {
			worstDensity = Number(stat.density);
			worstZoneName = zone.name;
		}
	}

	statsNode.querySelector(".statsCamCount").textContent = cameras.length;
	statsNode.querySelector(".sumCameraCount").textContent = cameras.length;
	statsNode.querySelector(".sumTotalCount").textContent = Math.round(totalCount);
	statsNode.querySelector(".sumDensity").textContent = avgDensity.toFixed(3);
	statsNode.querySelector(".sumMaxCount").textContent = maxCount;
	statsNode.querySelector(".sumWorstZone").textContent = worstZoneName;
	statsNode.querySelector(".sumEventCount").textContent = openEventCount;

	let normal = 0, caution = 0, danger = 0;
	for (const zone of zones) {
		const stat = latestStatsByZoneId[zone.id];
		const level = congestionLevel(stat ? stat.density : null);
		if (level === "danger") danger++;
		else if (level === "caution") caution++;
		else normal++;
	}
	document.getElementById("legendNormal").textContent = normal;
	document.getElementById("legendCaution").textContent = caution;
	document.getElementById("legendDanger").textContent = danger;
}

function renderDonut() {
	let labels, data, colors;

	if (statsMetric === "level") {
		let normal = 0, caution = 0, danger = 0;
		for (const zone of zones) {
			const stat = latestStatsByZoneId[zone.id];
			const level = congestionLevel(stat ? stat.density : null);
			if (level === "danger") danger++;
			else if (level === "caution") caution++;
			else normal++;
		}
		labels = ["정상", "주의", "위험"];
		data = [normal, caution, danger];
		colors = [LEVEL_COLOR.normal, LEVEL_COLOR.caution, LEVEL_COLOR.danger];
	} else if (statsMetric === "event") {
		labels = zones.map((z) => z.name);
		data = zones.map((z) => eventCountByZoneId[z.id] || 0);
		colors = zones.map((_, i) => DONUT_COLORS[i % DONUT_COLORS.length]);
	} else {
		labels = zones.map((z) => z.name);
		data = zones.map((z) => (latestStatsByZoneId[z.id] ? Number(latestStatsByZoneId[z.id].countAvg) : 0));
		colors = zones.map((_, i) => DONUT_COLORS[i % DONUT_COLORS.length]);
	}

	const total = data.reduce((a, b) => a + b, 0);
	const unit = statsMetric === "count" ? "명" : statsMetric === "event" ? "건" : "구역";
	statsNode.querySelector(".donutTotal").textContent = Math.round(total);
	statsNode.querySelector(".donutUnit").textContent = unit;

	const ctx = statsNode.querySelector(".donutChart");
	if (donutChart) {
		donutChart.data.labels = labels;
		donutChart.data.datasets[0].data = data;
		donutChart.data.datasets[0].backgroundColor = colors;
		donutChart.update();
	} else {
		donutChart = new Chart(ctx, {
			type: "doughnut",
			data: { labels, datasets: [{ data, backgroundColor: colors, borderWidth: 0 }] },
			options: { plugins: { legend: { display: false } }, cutout: "65%" },
		});
	}

	const legendEl = statsNode.querySelector(".donut-legend");
	legendEl.innerHTML = labels.map((label, i) => {
		const pct = total > 0 ? Math.round((data[i] / total) * 100) : 0;
		return `
			<li>
				<span class="swatch" style="background:${colors[i]}"></span>
				${label}
				<span class="bar-track"><span class="bar-fill" style="width:${pct}%;background:${colors[i]}"></span></span>
				<span class="count">${Math.round(data[i])}${unit}</span>
			</li>
		`;
	}).join("");
}

function renderDensityDetail() {
	const body = statsNode.querySelector(".densityDetailBody");
	if (zones.length === 0) {
		body.innerHTML = '<p class="empty-note">구역이 없습니다.</p>';
		return;
	}

	body.innerHTML = `
		<table class="detail-table">
			<thead><tr><th>구역</th><th>인원</th><th>밀집도</th><th>단계</th></tr></thead>
			<tbody>
				${zones.map((z) => {
					const s = latestStatsByZoneId[z.id];
					const level = congestionLevel(s ? s.density : null);
					const levelLabel = level === "danger" ? "위험" : level === "caution" ? "주의" : "정상";
					return `<tr><td>${z.name}</td><td>${s ? Math.round(s.countAvg) : "-"}</td><td>${s ? Number(s.density).toFixed(3) : "-"}</td><td>${levelLabel}</td></tr>`;
				}).join("")}
			</tbody>
		</table>
	`;
}

function renderFlowDetail() {
	const body = statsNode.querySelector(".flowDetailBody");
	if (zones.length === 0) {
		body.innerHTML = '<p class="empty-note">구역이 없습니다.</p>';
		return;
	}

	body.innerHTML = `
		<table class="detail-table">
			<thead><tr><th>구역</th><th>유입</th><th>유출</th><th>순유입</th></tr></thead>
			<tbody>
				${zones.map((z) => {
					const stats = statsWindowByZoneId[z.id] || [];
					let inflow = 0, outflow = 0;
					for (const s of stats) {
						inflow += s.inflowN + s.inflowE + s.inflowS + s.inflowW;
						outflow += s.outflowN + s.outflowE + s.outflowS + s.outflowW;
					}
					const net = inflow - outflow;
					const trendClass = net > 0 ? "trend-up" : net < 0 ? "trend-down" : "trend-flat";
					return `<tr><td>${z.name}</td><td>${inflow}</td><td>${outflow}</td><td class="${trendClass}">${net > 0 ? "+" : ""}${net}</td></tr>`;
				}).join("")}
			</tbody>
		</table>
		<p class="empty-note">최근 ${STATS_WINDOW_MINUTES}분간 누적치 (추정)</p>
	`;
}

const DIRECTION_KEYS = ["E", "NE", "N", "NW", "W", "SW", "S", "SE"];
const DIRECTION_ARROW = { E: "→", NE: "↗", N: "↑", NW: "↖", W: "←", SW: "↙", S: "↓", SE: "↘" };

function renderMovementDetail() {
	const body = statsNode.querySelector(".movementDetailBody");
	if (zones.length === 0) {
		body.innerHTML = '<p class="empty-note">구역이 없습니다.</p>';
		return;
	}

	body.innerHTML = `
		<table class="detail-table">
			<thead><tr><th>구역</th><th>평균 속도(px/s)</th><th>이동량</th><th>주 이동방향</th></tr></thead>
			<tbody>
				${zones.map((z) => {
					const stats = statsWindowByZoneId[z.id] || [];
					if (stats.length === 0) {
						return `<tr><td>${z.name}</td><td>-</td><td>-</td><td>-</td></tr>`;
					}

					const speeds = stats.map((s) => Number(s.avgSpeedPxS) || 0).filter((v) => v > 0);
					const avgSpeed = speeds.length ? speeds.reduce((a, b) => a + b, 0) / speeds.length : 0;

					const totals = {};
					let moveSum = 0;
					for (const dir of DIRECTION_KEYS) {
						const key = "move" + dir[0] + dir.slice(1).toLowerCase();
						const sum = stats.reduce((acc, s) => acc + (s[key] || 0), 0);
						totals[dir] = sum;
						moveSum += sum;
					}

					let dominant = "-";
					let dominantCount = 0;
					for (const dir of DIRECTION_KEYS) {
						if (totals[dir] > dominantCount) {
							dominantCount = totals[dir];
							dominant = dir;
						}
					}
					const dominantLabel = dominantCount > 0 ? `${DIRECTION_ARROW[dominant]} ${dominant}` : "-";

					return `<tr><td>${z.name}</td><td>${avgSpeed.toFixed(1)}</td><td>${moveSum}</td><td>${dominantLabel}</td></tr>`;
				}).join("")}
			</tbody>
		</table>
		<p class="empty-note">방향은 카메라 화면 기준 상대 방향(실제 지리적 방위 아님) · 속도는 픽셀/초 추정치 · 최근 ${STATS_WINDOW_MINUTES}분 집계</p>
	`;
}

function renderPeakDetail() {
	const body = statsNode.querySelector(".peakDetailBody");

	const byTime = {};
	for (const zone of zones) {
		for (const s of (statsWindowByZoneId[zone.id] || [])) {
			byTime[s.at] = (byTime[s.at] || 0) + Number(s.countAvg);
		}
	}

	const entries = Object.entries(byTime);
	if (entries.length === 0) {
		body.innerHTML = '<p class="empty-note">데이터가 없습니다.</p>';
		return;
	}

	const top3 = [...entries].sort((a, b) => b[1] - a[1]).slice(0, 3);
	const sortedByTime = [...entries].sort((a, b) => a[0].localeCompare(b[0]));
	const half = Math.floor(sortedByTime.length / 2) || 1;
	const firstAvg = sortedByTime.slice(0, half).reduce((s, [, v]) => s + v, 0) / half;
	const secondAvg = sortedByTime.slice(half).reduce((s, [, v]) => s + v, 0) / Math.max(sortedByTime.length - half, 1);
	const diff = secondAvg - firstAvg;
	const trendClass = diff > 0.5 ? "trend-up" : diff < -0.5 ? "trend-down" : "trend-flat";
	const trendLabel = diff > 0.5
		? `▲ 증가 (${diff.toFixed(1)}명)`
		: diff < -0.5
			? `▼ 감소 (${Math.abs(diff).toFixed(1)}명)`
			: "- 변동 적음";

	body.innerHTML = `
		<table class="detail-table">
			<thead><tr><th>순위</th><th>시각</th><th>총 인원</th></tr></thead>
			<tbody>
				${top3.map(([at, v], i) => `<tr><td>${i + 1}위</td><td>${at.substring(11, 16)}</td><td>${Math.round(v)}</td></tr>`).join("")}
			</tbody>
		</table>
		<p class="${trendClass}" style="margin-top:8px;">최근 ${STATS_WINDOW_MINUTES}분 추세: ${trendLabel}</p>
	`;
}

function renderAnomalyDetail() {
	const body = statsNode.querySelector(".anomalyDetailBody");

	const rows = [];
	for (const zone of zones) {
		const stats = statsWindowByZoneId[zone.id] || [];
		for (let i = 1; i < stats.length; i++) {
			const prev = Number(stats[i - 1].countAvg);
			const curr = Number(stats[i].countAvg);
			const delta = curr - prev;
			if (Math.abs(delta) >= Math.max(5, prev * 0.5)) {
				rows.push({ zone: zone.name, at: stats[i].at, prev, curr, delta });
			}
		}
	}

	if (rows.length === 0) {
		body.innerHTML = '<p class="empty-note">비정상적인 인원 변동이 감지되지 않았습니다.</p>';
		return;
	}

	body.innerHTML = `
		<table class="detail-table">
			<thead><tr><th>구역</th><th>시각</th><th>변동</th></tr></thead>
			<tbody>
				${rows.slice(-8).reverse().map((r) => `
					<tr>
						<td>${r.zone}</td>
						<td>${r.at.substring(11, 16)}</td>
						<td class="${r.delta > 0 ? "trend-up" : "trend-down"}">${r.prev}명 → ${r.curr}명 (${r.delta > 0 ? "+" : ""}${r.delta})</td>
					</tr>
				`).join("")}
			</tbody>
		</table>
	`;
}

async function renderThresholdDetail() {
	const body = statsNode.querySelector(".thresholdDetailBody");
	if (zones.length === 0) {
		body.innerHTML = '<p class="empty-note">구역이 없습니다.</p>';
		return;
	}

	body.innerHTML = zones.map((z) => `
		<div style="margin-bottom:10px;">
			<strong style="font-size:12px;">${z.name}</strong>
			<div class="threshold-rows" data-zone-id="${z.id}" style="margin-top:6px;">불러오는 중...</div>
		</div>
	`).join("");

	for (const zone of zones) {
		const thresholds = (await fetchJson(`api/v1/zones/${zone.id}/thresholds`)) || [];
		const level1 = thresholds.find((t) => t.levelNo === 1);
		const level2 = thresholds.find((t) => t.levelNo === 2);
		const container = body.querySelector(`.threshold-rows[data-zone-id="${zone.id}"]`);
		const editable = isAdminUser();

		container.innerHTML = `
			<div class="threshold-row">
				<label>주의 단계</label>
				<input type="number" step="0.001" value="${level1 ? level1.densityThreshold : ""}" data-zone-id="${zone.id}" data-level="1" ${editable ? "" : "disabled"}>
				${editable ? `<button data-zone-id="${zone.id}" data-level="1">저장</button>` : ""}
			</div>
			<div class="threshold-row">
				<label>위험 단계</label>
				<input type="number" step="0.001" value="${level2 ? level2.densityThreshold : ""}" data-zone-id="${zone.id}" data-level="2" ${editable ? "" : "disabled"}>
				${editable ? `<button data-zone-id="${zone.id}" data-level="2">저장</button>` : ""}
			</div>
		`;
	}

	body.querySelectorAll(".threshold-row button").forEach((btn) => {
		btn.addEventListener("click", async () => {
			const zoneId = Number(btn.dataset.zoneId);
			const levelNo = Number(btn.dataset.level);
			const input = body.querySelector(`input[data-zone-id="${zoneId}"][data-level="${levelNo}"]`);
			const value = Number(input.value);

			const existing = (await fetchJson(`api/v1/zones/${zoneId}/thresholds`)) || [];
			const merged = existing.filter((t) => t.levelNo !== levelNo);
			merged.push({ zoneId, levelNo, densityThreshold: value });

			const res = await fetch(`api/v1/zones/${zoneId}/thresholds`, {
				method: "PUT",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify(merged),
			});

			alert(res.ok ? "기준이 저장되었습니다." : "저장에 실패했습니다.");
		});
	});
}

function openStatsSidebar() {
	document.getElementById("statsSidebar").classList.add("open");
}

function closeStatsSidebar() {
	document.getElementById("statsSidebar").classList.remove("open");
}

document.getElementById("statsToggleBtn").addEventListener("click", () => {
	document.getElementById("statsSidebar").classList.toggle("open");
});

// ---------- 카메라 카드 그리드 ----------

let lastEntrySignatures = []; // 위치별 "c{id}:{status}" | "z{zoneId}" — 카드 하나하나를 독립적으로 비교하기 위함

function renderCameraGrid() {
	const grid = document.getElementById("cameraGrid");

	let total = 0;
	const allEntries = []; // { zone, camera|null }
	for (const zone of zones) {
		const zoneCameras = cameras.filter((c) => c.zoneId === zone.id);
		if (zoneCameras.length === 0) {
			total++;
			allEntries.push({ zone, camera: null });
		} else {
			for (const camera of zoneCameras) {
				total++;
				allEntries.push({ zone, camera });
			}
		}
	}

	document.getElementById("cameraGridCount").textContent = total;
	grid.classList.toggle("split4", splitView === "split4");

	let visibleEntries = allEntries;
	if (splitView === "split4") {
		const idsToShow = checkedCameraIds.size > 0 ? [...checkedCameraIds] : cameras.slice(0, 4).map((c) => c.id);
		visibleEntries = allEntries.filter((e) => e.camera && idsToShow.includes(e.camera.id));
	}

	// 실행 중인 캠은 <img src=".../live">로 MJPEG 스트림을 물고 있다. 카메라 한 대 상태가
	// 바뀌었다고 그리드 전체를 다시 그리면, 안 바뀐 다른 카드의 스트림 연결까지 매번 끊겼다
	// 새로 열리길 반복하다가 브라우저의 동시 연결 수 제한에 걸려버린다(실제로 겪은 버그 —
	// 다른 카메라들을 재시작하는 동안 멀쩡한 카메라 영상이 계속 처음부터 다시 연결되면서
	// "뒤로감기"처럼 보였음). 그래서 위치별로 바뀐 카드만 교체하고 나머지는 절대 건드리지 않는다.
	const newSignatures = visibleEntries.map((e) => (e.camera ? `c${e.camera.id}:${e.camera.status}` : `z${e.zone.id}`));

	if (grid.children.length !== visibleEntries.length) {
		grid.innerHTML = visibleEntries.map((e) => (e.camera ? renderCameraCard(e.zone, e.camera) : renderEmptyZoneCard(e.zone))).join("");
		lastEntrySignatures = newSignatures;
		wireCameraGridEvents();
		return;
	}

	const cards = Array.from(grid.children);
	newSignatures.forEach((sig, i) => {
		if (sig === lastEntrySignatures[i]) return;

		const entry = visibleEntries[i];
		const html = entry.camera ? renderCameraCard(entry.zone, entry.camera) : renderEmptyZoneCard(entry.zone);
		const wrapper = document.createElement("div");
		wrapper.innerHTML = html.trim();
		const newCard = wrapper.firstElementChild;
		cards[i].replaceWith(newCard);
		cards[i] = newCard;
		wireCardEvents(newCard);
	});
	lastEntrySignatures = newSignatures;

	updateCameraGridInPlace(visibleEntries);
}

function updateCameraGridInPlace(entries) {
	const cards = document.getElementById("cameraGrid").children;

	entries.forEach((entry, i) => {
		const card = cards[i];
		if (!card || !entry.camera) return;

		const camera = entry.camera;
		const stat = latestStatsByZoneId[entry.zone.id];
		const count = stat ? Math.round(stat.countAvg) : 0;
		const eventCount = eventCountByZoneId[entry.zone.id] || 0;

		const countEl = card.querySelector(".f-count");
		const eventEl = card.querySelector(".f-events");
		if (countEl) countEl.textContent = count;
		if (eventEl) eventEl.textContent = eventCount;

		const checkbox = card.querySelector('input[type="checkbox"]');
		if (checkbox) checkbox.checked = checkedCameraIds.has(camera.id);

		card.classList.toggle("selected", camera.id === selectedCameraId);
	});
}

function renderCameraCard(zone, camera) {
	const isRunning = camera.status === "RUNNING";
	const isTransitioning = camera.status === "PREPARING";
	const stat = latestStatsByZoneId[zone.id];
	const count = stat ? Math.round(stat.countAvg) : 0;
	const level = congestionLevel(stat ? stat.density : null);
	const eventCount = eventCountByZoneId[zone.id] || 0;
	const thumb = isRunning
		? `<img src="${analysisServerBaseFor(camera.id)}/analyzer/v1/cameras/${camera.id}/live" alt="">`
		: "영상 없음 · 올려 주세요";
	const checked = checkedCameraIds.has(camera.id) ? "checked" : "";

	return `
		<div class="cam-card ${camera.id === selectedCameraId ? "selected" : ""}" data-camera-id="${camera.id}">
			<div class="cam-card-top">
				<label class="cam-checkbox"><input type="checkbox" data-camera-id="${camera.id}" ${checked}></label>
				<div class="cam-card-title">
					${zone.name}
					${level !== "normal" ? `<span class="level-badge ${level}">${level === "danger" ? "위험" : "주의"}</span>` : ""}
				</div>
			</div>
			<div class="cam-card-location">${currentGuName} &gt; ${currentDongName} · ${zone.areaM2}㎡</div>
			<div class="cam-card-thumb">${thumb}</div>
			<div class="cam-card-stats">
				<span>상태 <b class="status-badge ${camera.status}">${camera.status}</b></span>
				<span>인원 <b class="f-count">${count}</b></span>
				<span>이벤트 <b class="f-events">${eventCount}</b></span>
			</div>
			<div class="cam-card-actions">
				<button class="secondary" data-action="upload" data-camera-id="${camera.id}">영상 올리기</button>
				<button data-action="analyze" data-camera-id="${camera.id}">${isRunning || isTransitioning ? "다시 분석" : "분석 시작"}</button>
				<button class="secondary" data-action="stop" data-camera-id="${camera.id}" ${isRunning || isTransitioning ? "" : "disabled"}>중단</button>
			</div>
		</div>
	`;
}

function renderEmptyZoneCard(zone) {
	return `
		<div class="cam-card">
			<div class="cam-card-top">
				<div class="cam-card-title">${zone.name}</div>
			</div>
			<div class="cam-card-location">${currentGuName} &gt; ${currentDongName} · ${zone.areaM2}㎡</div>
			<div class="cam-card-thumb">캠 없음</div>
			<div class="cam-card-stats"><span>이 구역에는 등록된 카메라가 없습니다.</span></div>
			<div class="cam-card-actions">
				<button data-action="register" data-zone-id="${zone.id}">카메라 등록</button>
			</div>
		</div>
	`;
}

function wireCardEvents(card) {
	card.addEventListener("click", (e) => {
		if (e.target.closest("button") || e.target.closest("input")) return;
		const camId = card.dataset.cameraId;
		if (camId) openCameraModal(Number(camId));
	});

	const cb = card.querySelector('input[type="checkbox"]');
	if (cb) {
		cb.addEventListener("click", (e) => e.stopPropagation());
		cb.addEventListener("change", () => {
			const id = Number(cb.dataset.cameraId);
			if (cb.checked) checkedCameraIds.add(id);
			else checkedCameraIds.delete(id);
			if (splitView === "split4") renderCameraGrid();
		});
	}

	const uploadBtn = card.querySelector('button[data-action="upload"]');
	if (uploadBtn) uploadBtn.addEventListener("click", (e) => { e.stopPropagation(); handleUploadSource(Number(uploadBtn.dataset.cameraId)); });

	const analyzeBtn = card.querySelector('button[data-action="analyze"]');
	if (analyzeBtn) analyzeBtn.addEventListener("click", (e) => { e.stopPropagation(); handleAnalyze(Number(analyzeBtn.dataset.cameraId)); });

	const stopBtn = card.querySelector('button[data-action="stop"]');
	if (stopBtn) stopBtn.addEventListener("click", (e) => { e.stopPropagation(); handleStop(Number(stopBtn.dataset.cameraId)); });

	const registerBtn = card.querySelector('button[data-action="register"]');
	if (registerBtn) registerBtn.addEventListener("click", (e) => { e.stopPropagation(); handleRegisterCamera(Number(registerBtn.dataset.zoneId)); });
}

function wireCameraGridEvents() {
	document.querySelectorAll("#cameraGrid .cam-card").forEach(wireCardEvents);
}

document.getElementById("splitAllBtn").addEventListener("click", () => {
	splitView = "all";
	document.getElementById("splitAllBtn").classList.add("active");
	document.getElementById("split4Btn").classList.remove("active");
	renderCameraGrid();
});

document.getElementById("split4Btn").addEventListener("click", () => {
	splitView = "split4";
	document.getElementById("split4Btn").classList.add("active");
	document.getElementById("splitAllBtn").classList.remove("active");
	renderCameraGrid();
});

async function setCameraState(cameraId, state) {
	const res = await fetch(`api/v1/cameras/${cameraId}/state`, {
		method: "PATCH",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({ state }),
	});
	if (!res.ok) {
		const err = await res.json();
		alert(err.message || "요청 처리에 실패했습니다.");
	}
	return res.ok;
}

async function handleUploadSource(cameraId) {
	const camera = cameras.find((c) => c.id === cameraId);
	const uri = prompt("영상 파일 경로(sourceUri)를 입력하세요.", camera ? camera.sourceUri : "");
	if (!uri) return;

	const res = await fetch(`api/v1/cameras/${cameraId}`, {
		method: "PATCH",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({ sourceUri: uri }),
	});

	if (res.ok) {
		await loadAll();
	} else {
		const err = await res.json();
		alert(err.message || "변경에 실패했습니다. (가동 중에는 영상을 바꿀 수 없습니다)");
	}
}

async function handleAnalyze(cameraId) {
	const camera = cameras.find((c) => c.id === cameraId);
	if (!camera) return;

	if (camera.status === "RUNNING" || camera.status === "PREPARING") {
		await setCameraState(cameraId, "STOPPED");
		await setCameraState(cameraId, "RUNNING");
	} else {
		await setCameraState(cameraId, "RUNNING");
	}

	await loadAll();
	if (selectedCameraId === cameraId) refreshModalCamera();
}

async function handleStop(cameraId) {
	await setCameraState(cameraId, "STOPPED");
	await loadAll();
	if (selectedCameraId === cameraId) refreshModalCamera();
}

async function handleRegisterCamera(zoneId) {
	const name = prompt("카메라 이름을 입력하세요.");
	if (!name) return;
	const uri = prompt("영상 파일 경로(sourceUri)를 입력하세요.");
	if (!uri) return;

	const res = await fetch("api/v1/cameras", {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({ zoneId, name, sourceType: "DUMMY", sourceUri: uri }),
	});

	if (res.ok) {
		await loadAll();
	} else {
		const err = await res.json();
		alert(err.message || "카메라 등록에 실패했습니다.");
	}
}

// ---------- 카메라 확대 모달 ----------

let statsSSE = null; // sse-001: 모달이 열려있는 동안만 구역 통계를 실시간 구독

function openCameraModal(cameraId) {
	selectedCameraId = cameraId;
	lastModalSignature = null; // 새로 열 때는 항상 새로 그려서 라이브 스트림을 새로 연결한다
	document.getElementById("cameraModalOverlay").classList.add("active");
	document.getElementById("playbackResult").style.display = "none";

	const now = new Date();
	const tenMinAgo = new Date(now.getTime() - STATS_WINDOW_MINUTES * 60 * 1000);
	document.getElementById("toolsFrom").value = toIsoNoOffset(tenMinAgo);
	document.getElementById("toolsTo").value = toIsoNoOffset(now);

	refreshModalCamera();
	renderCameraGrid();

	const camera = cameras.find((c) => c.id === cameraId);
	if (camera) {
		openStatsStream(camera.zoneId);
	}
}

function closeCameraModal() {
	document.getElementById("cameraModalOverlay").classList.remove("active");
	selectedCameraId = null;
	closeStatsStream();
	renderCameraGrid();
}

/** 구역 통계를 실시간으로 구독해 상단 통계 박스를 즉시 갱신한다 (10초 폴링 대신). */
function openStatsStream(zoneId) {
	closeStatsStream();
	statsSSE = new EventSource(`api/v1/zones/${zoneId}/stats/stream`);
	statsSSE.addEventListener("stat", (ev) => {
		const s = JSON.parse(ev.data);
		document.getElementById("statCount").textContent = Math.round(s.countAvg);
		document.getElementById("statDensity").textContent = Number(s.density).toFixed(3);
		document.getElementById("statDwell").textContent = s.dwellAvgSec;
	});
	statsSSE.onerror = () => {
		// 네트워크 문제 등으로 끊기면 브라우저가 자동 재연결을 시도한다. 별도 처리 불필요.
	};
}

function closeStatsStream() {
	if (statsSSE) {
		statsSSE.close();
		statsSSE = null;
	}
}

document.getElementById("exportStatsBtn").addEventListener("click", () => {
	if (selectedCameraId == null) return;
	const camera = cameras.find((c) => c.id === selectedCameraId);
	if (!camera) return;
	const from = document.getElementById("toolsFrom").value;
	const to = document.getElementById("toolsTo").value;
	window.location = `api/v1/zones/${camera.zoneId}/stats/export?from=${from}&to=${to}`;
});

document.getElementById("playbackBtn").addEventListener("click", async () => {
	if (selectedCameraId == null) return;
	const camera = cameras.find((c) => c.id === selectedCameraId);
	if (!camera) return;
	const from = document.getElementById("toolsFrom").value;
	const to = document.getElementById("toolsTo").value;

	const result = document.getElementById("playbackResult");
	result.style.display = "block";
	result.innerHTML = "불러오는 중...";

	const data = await fetchJson(`api/v1/zones/${camera.zoneId}/playback?from=${from}&to=${to}`);
	const recordings = (data && data.recordings) || [];
	const timeline = (data && data.timeline) || [];

	result.innerHTML = `
		<h4>녹화 이력 (${recordings.length}건)</h4>
		${recordings.length === 0
			? '<p class="playback-empty">선택한 기간에 재생된 녹화 이력이 없습니다.</p>'
			: recordings.map((r) => `
				<div class="playback-recording-row">
					<span>${r.startedAt.replace("T", " ")}</span>
					<span>${r.filePath}</span>
				</div>
			`).join("")}
		<h4 style="margin-top:12px;">해당 구간 인원 추이 (${timeline.length}개 포인트)</h4>
		${timeline.length === 0 ? '<p class="playback-empty">통계 데이터가 없습니다.</p>' : ""}
	`;
	if (timeline.length > 0) {
		renderChart(timeline);
	}
});

document.getElementById("cameraModalClose").addEventListener("click", closeCameraModal);
document.getElementById("cameraModalOverlay").addEventListener("click", (e) => {
	if (e.target.id === "cameraModalOverlay") closeCameraModal();
});

async function refreshModalCamera() {
	if (selectedCameraId == null) return;

	const latest = await fetchJson(`api/v1/cameras/${selectedCameraId}`);
	const idx = cameras.findIndex((c) => c.id === selectedCameraId);
	if (idx < 0) return;
	cameras[idx] = { ...cameras[idx], ...latest };

	const camera = cameras[idx];
	document.getElementById("detailTitle").textContent = `${camera.zoneName} · ${camera.name}`;

	await refreshChartForZone(camera.zoneId);
	renderCameraDetail(camera);
}

async function refreshChartForZone(zoneId) {
	const to = new Date();
	const from = new Date(to.getTime() - STATS_WINDOW_MINUTES * 60 * 1000);

	const stats = await fetchJson(
		`api/v1/zones/${zoneId}/stats?from=${toIsoNoOffset(from)}&to=${toIsoNoOffset(to)}`
	);

	if (!stats || stats.length === 0) {
		document.getElementById("statCount").textContent = "-";
		document.getElementById("statDensity").textContent = "-";
		document.getElementById("statDwell").textContent = "-";
		renderChart([]);
		return;
	}

	const latest = stats[stats.length - 1];
	document.getElementById("statCount").textContent = Math.round(latest.countAvg);
	document.getElementById("statDensity").textContent = Number(latest.density).toFixed(3);
	document.getElementById("statDwell").textContent = latest.dwellAvgSec;

	renderChart(stats);
}

function renderChart(stats) {
	const ctx = document.getElementById("chart");
	const labels = stats.map((s) => s.at.substring(11, 16));
	const data = stats.map((s) => s.countAvg);

	if (chart) {
		chart.data.labels = labels;
		chart.data.datasets[0].data = data;
		chart.update();
		return;
	}

	chart = new Chart(ctx, {
		type: "line",
		data: {
			labels,
			datasets: [{
				label: "인원수(1분 평균)",
				data,
				borderColor: "#4fc3f7",
				backgroundColor: "rgba(79,195,247,0.15)",
				tension: 0.3,
				fill: true,
			}],
		},
		options: {
			scales: {
				x: { ticks: { color: "#9aa0ab" } },
				y: { ticks: { color: "#9aa0ab" }, beginAtZero: true },
			},
			plugins: { legend: { labels: { color: "#e8e8e8" } } },
		},
	});
}

let lastModalSignature = null;

function renderCameraDetail(camera) {
	const panel = document.getElementById("cameraDetail");
	const isRunning = camera.status === "RUNNING";
	const isTransitioning = camera.status === "PREPARING";

	// 그리드와 같은 이유: RUNNING 상태가 그대로면 innerHTML을 다시 그리지 않아야
	// <img>의 라이브 스트림 연결이 매 폴링마다 끊겼다 다시 열리는 걸 막는다.
	const signature = `${camera.id}:${camera.status}`;
	if (signature === lastModalSignature) {
		return;
	}
	lastModalSignature = signature;

	panel.innerHTML = `
		<div class="camera-header">
			<strong>${camera.name}</strong>
			<span class="status-badge ${camera.status}">${camera.status}</span>
		</div>
		<button id="modalToggleBtn" ${isTransitioning ? "disabled" : ""}>
			${isRunning || isTransitioning ? "중단" : "분석 시작"}
		</button>
		${isRunning ? `<img src="${analysisServerBaseFor(camera.id)}/analyzer/v1/cameras/${camera.id}/live" alt="실시간 영상">` : ""}
	`;

	document.getElementById("modalToggleBtn").addEventListener("click", async () => {
		await setCameraState(camera.id, isRunning || isTransitioning ? "STOPPED" : "RUNNING");
		await refreshModalCamera();
		await loadAll();
	});
}

// ---------- 이벤트 로그 ----------

const EVENT_LEVEL_LABEL = { 1: "주의", 2: "위험" };

function zoneName(zoneId) {
	const zone = zones.find((z) => z.id === zoneId);
	return zone ? zone.name : `구역 ${zoneId}`;
}

async function fetchEvents() {
	return (await fetchJson("api/v1/events")) || [];
}

async function refreshEventBadge() {
	const events = await fetchEvents();
	openEventCount = events.filter((e) => e.status !== "RESOLVED").length;

	eventCountByZoneId = {};
	for (const ev of events) {
		eventCountByZoneId[ev.zoneId] = (eventCountByZoneId[ev.zoneId] || 0) + 1;
	}

	const badge = document.getElementById("eventBadge");
	if (openEventCount > 0) {
		badge.textContent = openEventCount;
		badge.style.display = "inline-block";
	} else {
		badge.style.display = "none";
	}

	if (statsNode) {
		statsNode.querySelector(".sumEventCount").textContent = openEventCount;
	}

	return events;
}

function startEventPolling() {
	refreshEventBadge();
	if (eventPollTimer) clearInterval(eventPollTimer);
	eventPollTimer = setInterval(refreshEventBadge, 5000);
}

async function renderEventsTab() {
	const events = await refreshEventBadge();
	const listEl = document.getElementById("eventList");

	if (events.length === 0) {
		listEl.innerHTML = '<p style="color:#9aa0ab;">발생한 이벤트가 없습니다.</p>';
		return;
	}

	listEl.innerHTML = events.map((ev) => `
		<div class="event-card level-${ev.levelNo}">
			<div class="event-header">
				<strong>${zoneName(ev.zoneId)} · ${EVENT_LEVEL_LABEL[ev.levelNo] || ev.levelNo}단계</strong>
				<span class="status-badge ${ev.status}">${ev.status}</span>
			</div>
			<div class="event-meta">
				발생: ${ev.occurredAt} · 밀집도 ${ev.densityAtEvent} (기준 ${ev.thresholdDensity})
				${ev.memo ? ` · 메모: ${ev.memo}` : ""}
			</div>
			${isAdminUser() && ev.status !== "RESOLVED" ? `
				<div class="event-actions">
					<input type="text" placeholder="조치 메모" id="memo-${ev.id}">
					${ev.status === "UNCONFIRMED" ? `<button data-action="CONFIRMED" data-id="${ev.id}">확인</button>` : ""}
					<button data-action="RESOLVED" data-id="${ev.id}">조치완료</button>
				</div>
			` : ""}
		</div>
	`).join("");

	listEl.querySelectorAll("button[data-action]").forEach((btn) => {
		btn.addEventListener("click", () => updateEventStatus(Number(btn.dataset.id), btn.dataset.action));
	});
}

async function updateEventStatus(eventId, status) {
	const memoInput = document.getElementById(`memo-${eventId}`);
	const memo = memoInput ? memoInput.value : null;

	const res = await fetch(`api/v1/events/${eventId}`, {
		method: "PATCH",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({ status, memo: memo || null }),
	});

	if (res.ok) {
		renderEventsTab();
	} else {
		const err = await res.json();
		alert(err.message || "처리에 실패했습니다.");
	}
}

// ---------- 메인 탭 (대시보드 / 통계·구역비교 / 이벤트로그) ----------

function setMainTab(tabName) {
	document.querySelectorAll(".main-tab-btn").forEach((b) => b.classList.toggle("active", b.dataset.maintab === tabName));
	document.getElementById("dashboardMainPanel").classList.toggle("active", tabName === "dashboard");
	document.getElementById("statsMainPanel").classList.toggle("active", tabName === "stats");
	document.getElementById("eventsMainPanel").classList.toggle("active", tabName === "events");
	document.getElementById("adminMainPanel").classList.toggle("active", tabName === "admin");

	if (statsNode) {
		if (tabName === "stats") {
			document.getElementById("statsFullHost").appendChild(statsNode);
		} else {
			document.getElementById("statsSidebar").appendChild(statsNode);
		}
	}

	if (tabName === "dashboard" && map) {
		google.maps.event.trigger(map, "resize");
	}
	if (tabName === "events") {
		renderEventsTab();
	}
	if (tabName === "admin") {
		renderAdminTab();
	}
}

// ---------- 관리 탭: 혼잡 임계치 설정 전용 화면 (acman-007) ----------

async function renderAdminTab() {
	document.getElementById("adminDongName").textContent = currentDongName;
	const body = document.getElementById("adminThresholdBody");

	if (!isAdminUser()) {
		body.innerHTML = `<tr><td colspan="4" class="playback-empty">관리자만 접근할 수 있습니다.</td></tr>`;
		return;
	}
	if (zones.length === 0) {
		body.innerHTML = `<tr><td colspan="4" class="playback-empty">선택된 동에 구역이 없습니다. 지역 필터에서 동을 먼저 선택하세요.</td></tr>`;
		return;
	}

	body.innerHTML = zones.map((z) => `
		<tr data-zone-id="${z.id}">
			<td><strong>${z.name}</strong></td>
			<td><input type="number" step="0.001" class="admin-level1" data-zone-id="${z.id}"></td>
			<td><input type="number" step="0.001" class="admin-level2" data-zone-id="${z.id}"></td>
			<td><button class="admin-save-btn" data-zone-id="${z.id}">저장</button></td>
		</tr>
	`).join("");

	for (const zone of zones) {
		const thresholds = (await fetchJson(`api/v1/zones/${zone.id}/thresholds`)) || [];
		const level1 = thresholds.find((t) => t.levelNo === 1);
		const level2 = thresholds.find((t) => t.levelNo === 2);
		const row = body.querySelector(`tr[data-zone-id="${zone.id}"]`);
		if (level1) row.querySelector(".admin-level1").value = level1.densityThreshold;
		if (level2) row.querySelector(".admin-level2").value = level2.densityThreshold;
	}

	body.querySelectorAll(".admin-save-btn").forEach((btn) => {
		btn.addEventListener("click", async () => {
			const zoneId = Number(btn.dataset.zoneId);
			const row = body.querySelector(`tr[data-zone-id="${zoneId}"]`);
			const level1 = Number(row.querySelector(".admin-level1").value);
			const level2 = Number(row.querySelector(".admin-level2").value);

			const res = await fetch(`api/v1/zones/${zoneId}/thresholds`, {
				method: "PUT",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify([
					{ zoneId, levelNo: 1, densityThreshold: level1 },
					{ zoneId, levelNo: 2, densityThreshold: level2 },
				]),
			});
			alert(res.ok ? `${zoneName(zoneId)} 기준이 저장되었습니다.` : "저장에 실패했습니다.");
		});
	});
}

document.querySelectorAll(".main-tab-btn").forEach((btn) => {
	btn.addEventListener("click", () => setMainTab(btn.dataset.maintab));
});

// ---------- 지도 영역 크기 조절 ----------

(function setupMapResize() {
	const handle = document.getElementById("mapResizeHandle");
	const section = document.getElementById("mapSection");
	let dragging = false;
	let startY = 0;
	let startHeight = 0;

	handle.addEventListener("mousedown", (e) => {
		dragging = true;
		startY = e.clientY;
		startHeight = section.getBoundingClientRect().height;
		e.preventDefault();
	});

	window.addEventListener("mousemove", (e) => {
		if (!dragging) return;
		const next = Math.min(Math.max(startHeight + (e.clientY - startY), 140), window.innerHeight * 0.8);
		section.style.height = `${next}px`;
		if (map) google.maps.event.trigger(map, "resize");
	});

	window.addEventListener("mouseup", () => { dragging = false; });
})();

// ---------- 구글 지도 ----------

function markerIcon(color) {
	const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="34" height="34">` +
		`<circle cx="17" cy="17" r="15" fill="${color}" stroke="#0f1115" stroke-width="2"/>` +
		`<path d="M11 13h3l1.2-2h3.6l1.2 2h3v8H11z" fill="#0f1115"/>` +
		`<circle cx="17" cy="17" r="2.6" fill="${color}"/>` +
		`</svg>`;
	return {
		url: `data:image/svg+xml;base64,${btoa(svg)}`,
		scaledSize: new google.maps.Size(34, 34),
		labelOrigin: new google.maps.Point(17, 40),
	};
}

function clearMapMarkers() {
	Object.values(markersByZoneId).forEach((m) => m.setMap(null));
	markersByZoneId = {};
}

window.initMap = function () {
	map = new google.maps.Map(document.getElementById("map"), {
		center: { lat: 36.3504, lng: 127.3845 },
		zoom: 14,
		mapTypeControl: false,
		streetViewControl: false,
		styles: [
			{ elementType: "geometry", stylers: [{ color: "#1d212a" }] },
			{ elementType: "labels.text.fill", stylers: [{ color: "#8a929e" }] },
			{ elementType: "labels.text.stroke", stylers: [{ color: "#1d212a" }] },
			{ featureType: "poi", stylers: [{ visibility: "off" }] },
			{ featureType: "transit", stylers: [{ visibility: "off" }] },
			{ featureType: "road", elementType: "geometry", stylers: [{ color: "#2a2f3a" }] },
			{ featureType: "road", elementType: "labels", stylers: [{ visibility: "simplified" }] },
			{ featureType: "administrative", elementType: "geometry", stylers: [{ color: "#3a4150" }] },
			{ featureType: "water", elementType: "geometry", stylers: [{ color: "#17191f" }] },
		],
	});
	wireMapRightClick();
	renderMapMarkers();
};

function wireMapRightClick() {
	if (mapRightClickListener) google.maps.event.removeListener(mapRightClickListener);
	mapRightClickListener = map.addListener("rightclick", async (e) => {
		if (currentDongId == null) {
			alert("먼저 지역을 선택하세요.");
			return;
		}

		const name = prompt("새 구역 이름을 입력하세요.");
		if (!name) return;
		const areaM2 = Number(prompt("구역 면적(㎡)을 입력하세요.", "500"));
		if (!areaM2) return;

		const geometry = JSON.stringify({ lat: e.latLng.lat(), lng: e.latLng.lng() });
		const zoneRes = await fetch("api/v1/zones", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ dongId: currentDongId, name, areaM2, geometry }),
		});

		if (!zoneRes.ok) {
			const err = await zoneRes.json();
			alert(err.message || "구역 생성에 실패했습니다.");
			return;
		}
		const zone = await zoneRes.json();

		const camName = prompt("카메라 이름을 입력하세요.", `${name} CCTV`);
		if (camName) {
			const uri = prompt("영상 파일 경로(sourceUri)를 입력하세요.");
			if (uri) {
				await fetch("api/v1/cameras", {
					method: "POST",
					headers: { "Content-Type": "application/json" },
					body: JSON.stringify({ zoneId: zone.id, name: camName, sourceType: "DUMMY", sourceUri: uri }),
				});
			}
		}

		await loadAll();
	});
}

function renderMapMarkers() {
	if (!map) return;
	clearMapMarkers();

	for (const zone of zones) {
		const geo = zoneGeo(zone);
		if (!geo) continue;

		const stat = latestStatsByZoneId[zone.id];
		const level = congestionLevel(stat ? stat.density : null);
		const count = stat ? Math.round(stat.countAvg) : 0;
		const eventCount = eventCountByZoneId[zone.id] || 0;

		const marker = new google.maps.Marker({
			position: { lat: geo.lat, lng: geo.lng },
			map,
			title: `${zone.name} (${count}명)`,
			icon: markerIcon(LEVEL_COLOR[level]),
			label: eventCount > 0 ? { text: String(eventCount), color: "#ffffff", fontSize: "10px" } : undefined,
		});

		marker.addListener("click", () => {
			const camerasInZone = cameras.filter((c) => c.zoneId === zone.id);
			if (camerasInZone.length > 0) {
				openCameraModal(camerasInZone[0].id);
			}
		});

		markersByZoneId[zone.id] = marker;
	}
}

// 구글 지도 스크립트가 안 뜨는 경우(키 미설정) 안내 표시
setTimeout(() => {
	if (typeof google === "undefined") {
		document.getElementById("map").innerHTML =
			'<div style="padding:20px;color:#9aa0ab;">구글 지도 API 키가 설정되지 않았습니다. index.html의 YOUR_GOOGLE_MAPS_API_KEY를 발급받은 키로 교체하세요.</div>';
	}
}, 3000);

// ---------- 폴링 ----------

function startPolling() {
	if (pollTimer) clearInterval(pollTimer);
	const select = statsNode ? statsNode.querySelector(".statsRefreshInterval") : null;
	const seconds = select ? Number(select.value) : 10;

	pollTimer = setInterval(async () => {
		await refreshAllZoneStats();
		renderCameraGrid();
		renderMapMarkers();
		if (selectedCameraId != null) refreshModalCamera();
	}, seconds * 1000);
}

checkAuth();
