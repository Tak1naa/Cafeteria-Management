(() => {
  const POLL_MS = 2500;

  const $ = (id) => document.getElementById(id);

  let ws = null;
  let pollTimer = null;

  async function fetchJson(path) {
    const res = await fetch(path, {
      headers: { Accept: "application/json" },
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return res.json();
  }

  function setLive(online) {
    const pill = $("live-pill");
    pill.classList.toggle("online", online);
    pill.querySelector(".label").textContent = online ? "实时同步中" : "等待连接";
  }

  function crowdLevel(avgQueue, waiting) {
    const score = (avgQueue || 0) * 2 + (waiting || 0) * 0.5;
    if (score < 4) return { key: "low", label: "畅通", hint: "现在用餐体验较好" };
    if (score < 10) return { key: "mid", label: "适中", hint: "建议选择排队较少的窗口" };
    return { key: "high", label: "繁忙", hint: "高峰时段，请预留等待时间" };
  }

  function pickRecommendedWindow(queueLengths) {
    if (!queueLengths?.length) return -1;
    let min = queueLengths[0];
    let idx = 0;
    queueLengths.forEach((n, i) => {
      if (n < min) {
        min = n;
        idx = i;
      }
    });
    return idx;
  }

  function addTimeline(text) {
    const el = $("timeline");
    const item = document.createElement("div");
    item.className = "timeline-item";
    const ts = new Date().toLocaleTimeString("zh-CN");
    item.innerHTML = `<strong>${ts}</strong> · ${escapeHtml(text)}`;
    el.prepend(item);
    while (el.children.length > 40) el.removeChild(el.lastChild);
  }

  function escapeHtml(s) {
    return String(s)
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;");
  }

  function buildWindowCard(len, index, recIdx, maxQ) {
    const card = document.createElement("article");
    card.className = "window-card" + (index === recIdx ? " recommended" : "");

    const top = document.createElement("div");
    top.className = "window-top";
    const name = document.createElement("span");
    name.className = "window-name";
    name.textContent = `窗口 ${index + 1}`;
    top.appendChild(name);
    if (index === recIdx) {
      const badge = document.createElement("span");
      badge.className = "window-badge";
      badge.textContent = "推荐";
      top.appendChild(badge);
    }

    const track = document.createElement("div");
    track.className = "window-bar";
    const fill = document.createElement("div");
    fill.className = "window-bar-fill";
    fill.style.width = `${Math.round((len / maxQ) * 100)}%`;
    track.appendChild(fill);

    const meta = document.createElement("div");
    meta.className = "window-meta";
    const pct = Math.round((len / maxQ) * 100);
    meta.innerHTML = `<span>排队 ${len} 人</span><span>${pct}% 相对负载</span>`;

    card.append(top, track, meta);
    return card;
  }

  function render(data) {
    if (!data) {
      $("main-content").hidden = true;
      $("empty-state").hidden = false;
      return;
    }

    $("main-content").hidden = false;
    $("empty-state").hidden = true;

    const queues = data.queueLengths || [];
    const avg =
      queues.length > 0 ? queues.reduce((a, b) => a + b, 0) / queues.length : 0;
    const crowd = crowdLevel(avg, data.waitingForSeat);
    const recIdx = pickRecommendedWindow(queues);

    $("stat-crowd").textContent = crowd.label;
    $("stat-crowd").className = `value crowd-${crowd.key}`;
    $("stat-crowd-hint").textContent = crowd.hint;

    $("stat-waiting").textContent = data.waitingForSeat ?? "—";
    $("stat-seats").textContent = data.availableSeats ?? "—";

    const seatTotal = (data.availableSeats || 0) + (data.waitingForSeat || 0);
    const util =
      seatTotal > 0 && data.totalSeated != null
        ? Math.round((data.totalSeated / seatTotal) * 100)
        : null;
    $("stat-util").textContent = util != null ? `${util}%` : "—";

    $("stat-sim-time").textContent =
      data.simTime != null ? `${data.simTime}s` : "—";
    $("hero-meta").textContent = `仿真时刻 T=${data.simTime ?? "—"} · 新到达 ${data.newArrivals ?? 0} 人`;

    $("insight-rec").textContent =
      recIdx >= 0
        ? `推荐前往窗口 ${recIdx + 1}，当前队列 ${queues[recIdx]} 人`
        : "暂无窗口数据";

    $("insight-arrivals").textContent =
      data.newArrivals > 0
        ? `本时段新增 ${data.newArrivals} 人，系统已自动分配取餐窗口`
        : "当前无新到达顾客";

    const maxQ = Math.max(...queues, 1);
    const grid = $("windows-grid");
    grid.innerHTML = "";
    queues.forEach((len, i) => grid.appendChild(buildWindowCard(len, i, recIdx, maxQ)));

    $("last-sync").textContent = `上次同步 ${new Date().toLocaleTimeString("zh-CN")}`;
  }

  function renderDecision(payload) {
    if (!payload?.response?.allocation) return;
    const alloc = payload.response.allocation;
    const sum = alloc.reduce((a, b) => a + b, 0);
    addTimeline(
      `智能分配：${alloc.map((n, i) => `窗口${i + 1}+${n}`).join("，")}（共 ${sum} 人）`
    );
  }

  async function refresh() {
    try {
      await fetchJson("/health");
      setLive(true);

      const data = await fetchJson("/api/frontend/realtime/status");
      if (data) render(data);
    } catch {
      setLive(false);
    }
  }

  function connectWs() {
    const proto = location.protocol === "https:" ? "wss:" : "ws:";
    ws = new WebSocket(`${proto}//${location.host}/ws/simulation`);

    ws.onopen = () => setLive(true);
    ws.onclose = () => {
      setLive(false);
      setTimeout(connectWs, 4000);
    };
    ws.onmessage = (ev) => {
      try {
        const msg = JSON.parse(ev.data);
        if (msg.type === "decision") {
          renderDecision(msg);
          return;
        }
        render(msg);
        addTimeline("实时推送已更新");
      } catch {
        /* ignore */
      }
    };
  }

  function init() {
    $("btn-refresh").addEventListener("click", () => {
      refresh();
      addTimeline("手动刷新");
    });
    refresh();
    connectWs();
    pollTimer = setInterval(refresh, POLL_MS);
  }

  document.addEventListener("DOMContentLoaded", init);
})();
