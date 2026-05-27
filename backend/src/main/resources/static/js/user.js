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

  // ── Floor Plan ──────────────────────────────
  const TABLE_ROWS = 5;
  const TABLE_COLS = 10; // total seats per row
  const SEATS_PER_GROUP = 2;
  const TOTAL_CAPACITY = TABLE_ROWS * TABLE_COLS;

  // Persistent seat occupancy state (survives across renders)
  let occupiedSeats = new Set();

  function seededRandom(seed) {
    let s = seed | 0;
    return function () {
      s = (s * 1103515245 + 12345) & 0x7fffffff;
      return s / 0x7fffffff;
    };
  }

  // Pick `n` random items from an array, deterministically seeded
  function pickRandom(arr, n, seed) {
    if (n <= 0) return [];
    const rng = seededRandom(seed);
    const pool = [...arr];
    for (let i = pool.length - 1; i > 0; i--) {
      const j = Math.floor(rng() * (i + 1));
      [pool[i], pool[j]] = [pool[j], pool[i]];
    }
    return pool.slice(0, Math.min(n, pool.length));
  }

  // Update occupiedSeats to match target count.
  // New occupants fill random free seats; leavers vacate random occupied seats.
  function syncOccupiedSeats(targetCount, simTime) {
    const currentCount = occupiedSeats.size;
    if (targetCount === currentCount) return;

    const all = Array.from({ length: TOTAL_CAPACITY }, (_, i) => i);

    if (targetCount > currentCount) {
      // Need more occupants — pick from free seats
      const free = all.filter(i => !occupiedSeats.has(i));
      const newcomers = pickRandom(free, targetCount - currentCount, simTime);
      for (const s of newcomers) occupiedSeats.add(s);
    } else {
      // Need fewer occupants — evict from occupied seats
      const taken = [...occupiedSeats];
      const leavers = pickRandom(taken, currentCount - targetCount, simTime + 1);
      for (const s of leavers) occupiedSeats.delete(s);
    }
  }

  function renderFloorPlan(data) {
    const servingArea = $("serving-area");
    const diningArea = $("dining-area");
    const waitingArea = $("waiting-area");

    if (!data || !servingArea) return;

    const queues = data.queueLengths || [];
    const windowCount = data.windowCount || queues.length || 4;
    const availableSeats = data.availableSeats ?? 0;
    const waitingForSeat = data.waitingForSeat ?? 0;
    const occupiedNow = Math.max(0, TOTAL_CAPACITY - availableSeats);
    const simTime = data.simTime ?? 0;
    const totalInQueue = queues.reduce((a, b) => a + b, 0);

    const recIdx = pickRecommendedWindow(queues);
    const maxQ = Math.max(...queues, 1);

    const caption = $("floor-caption");
    if (caption) {
      caption.textContent =
        `座位 ${TOTAL_CAPACITY} · 占用 ${occupiedNow} · 空闲 ${availableSeats}` +
        (waitingForSeat > 0 ? ` · 等座 ${waitingForSeat} 人` : "");
    }

    // Serving windows — clean data display
    servingArea.innerHTML = "";
    for (let i = 0; i < windowCount; i++) {
      const qLen = queues[i] || 0;
      const win = document.createElement("div");
      win.className = "serving-window" + (i === recIdx ? " rec" : "");

      const loadLevel = maxQ > 0 ? qLen / maxQ : 0;
      let loadLabel = "畅通";
      if (loadLevel > 0.7) loadLabel = "繁忙";
      else if (loadLevel > 0.3) loadLabel = "适中";

      win.innerHTML =
        `<div class="sw-name">窗口 ${i + 1}</div>` +
        `<div class="sw-queue-num">${qLen}</div>` +
        `<div class="sw-count">人排队 · ${loadLabel}</div>`;
      servingArea.appendChild(win);
    }

    // Flow indicator: 窗口 → 等座 → 入座
    let flowEl = document.getElementById("flow-indicator");
    if (!flowEl) {
      flowEl = document.createElement("div");
      flowEl.id = "flow-indicator";
      flowEl.className = "flow-info";
      servingArea.insertAdjacentElement("afterend", flowEl);
    }
    flowEl.innerHTML =
      `<span>排队 <strong>${totalInQueue}</strong> 人</span>` +
      `<span class="flow-arrow">→</span>` +
      `<span style="color:#f5c542;">等座 <strong>${waitingForSeat}</strong> 人</span>` +
      `<span class="flow-arrow">→</span>` +
      `<span style="color:#a78bfa;">用餐 <strong>${occupiedNow}</strong> 人</span>`;

    // Dining seat grid — persistent occupancy, seats stick until freed
    syncOccupiedSeats(occupiedNow, simTime);
    diningArea.innerHTML = "";
    let seatIdx = 0;
    for (let row = 0; row < TABLE_ROWS; row++) {
      const rowEl = document.createElement("div");
      rowEl.className = "table-row";

      const groupsPerRow = TABLE_COLS / SEATS_PER_GROUP;
      for (let g = 0; g < groupsPerRow; g++) {
        const groupEl = document.createElement("div");
        groupEl.className = "table-group";

        for (let s = 0; s < SEATS_PER_GROUP; s++) {
          const seat = document.createElement("div");
          const occupied = occupiedSeats.has(seatIdx);
          seat.className = "seat " + (occupied ? "occupied" : "free");
          seat.title = occupied ? "已占用" : "空闲";
          groupEl.appendChild(seat);
          seatIdx++;
        }

        rowEl.appendChild(groupEl);
      }

      diningArea.appendChild(rowEl);
    }

    // Waiting area
    if (waitingForSeat > 0) {
      waitingArea.innerHTML =
        `<span>等座区 · <strong style="color:#f5c542;">${waitingForSeat}</strong> 人</span>`;
    } else {
      waitingArea.innerHTML = "<span>当前无人等座</span>";
    }
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

    const occupiedNow = Math.max(0, TOTAL_CAPACITY - (data.availableSeats || 0));
    const util = TOTAL_CAPACITY > 0 ? Math.round((occupiedNow / TOTAL_CAPACITY) * 100) : null;
    $("stat-util").textContent = util != null ? `${util}%` : "—";

    renderFloorPlan(data);

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
        if (msg.type === "reset") {
          occupiedSeats.clear();
          render(null);
          updateSimStatusBadge("running");
          addTimeline("仿真数据已重置");
          return;
        }
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

  // ── Simulation Control ──────────────────────

  function generateInitialQueueInputs() {
    const container = $("initial-queue-inputs");
    if (!container) return;
    container.innerHTML = "";
    for (let i = 0; i < 4; i++) {
      const wrap = document.createElement("div");
      wrap.style.display = "flex";
      wrap.style.flexDirection = "column";
      wrap.style.gap = "0.2rem";
      const label = document.createElement("span");
      label.style.fontSize = "0.72rem";
      label.style.color = "var(--text-dim)";
      label.textContent = `窗口 ${i + 1} 初始队列`;
      const input = document.createElement("input");
      input.type = "number";
      input.className = "ctrl-num";
      input.value = "0";
      input.min = "0";
      input.id = `init-q-${i}`;
      wrap.append(label, input);
      container.appendChild(wrap);
    }
  }

  async function controlSimulation(action) {
    const box = $("ctrl-response");
    box.textContent = `正在执行 ${action}…`;

    // Read initial state from inputs
    const queueLengths = [];
    for (let i = 0; i < 4; i++) {
      const el = document.getElementById(`init-q-${i}`);
      queueLengths.push(el ? parseInt(el.value, 10) || 0 : 0);
    }
    const occupiedSeats = parseInt($("initial-seats")?.value, 10) || 0;

    // Read simulation parameters
    const params = {
      initialQueueLengths: queueLengths,
      initialOccupiedSeats: occupiedSeats,
      arrivalRatePerTick: parseFloat($("param-arrival")?.value) || 1.2,
      avgServiceTimeSec: parseInt($("param-service")?.value, 10) || 3,
      avgEatTimeSec: parseInt($("param-eat")?.value, 10) || 45,
      totalTicks: parseInt($("param-ticks")?.value, 10) || 180,
    };

    try {
      const res = await fetch(`/api/frontend/simulation/${action}`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify(params),
      });
      const body = await res.json();
      box.textContent = JSON.stringify({ status: res.status, body }, null, 2);
      if (res.ok) {
        const labels = { start: "启动", stop: "停止", reset: "重置" };
        addTimeline(`仿真${labels[action] || action}成功`);
        updateSimStatusBadge(action === "stop" ? "stopped" : "running");
      } else {
        addTimeline(`仿真控制失败 (${res.status})`);
      }
    } catch (e) {
      box.textContent = e.message;
    }
  }

  function updateSimStatusBadge(status) {
    const badge = $("sim-status-badge");
    if (!badge) return;
    const dot = badge.querySelector(".dot");
    const label = badge.querySelector(".label");
    if (status === "running") {
      badge.className = "live-pill online";
      if (dot) dot.style.background = "var(--green)";
      if (label) label.textContent = "运行中";
    } else {
      badge.className = "live-pill";
      if (dot) dot.style.background = "var(--red)";
      if (label) label.textContent = "已停止";
    }
  }

  function init() {
    $("btn-refresh").addEventListener("click", () => {
      refresh();
      addTimeline("手动刷新");
    });

    generateInitialQueueInputs();
    $("btn-ctrl-start").addEventListener("click", () => controlSimulation("start"));
    $("btn-ctrl-stop").addEventListener("click", () => controlSimulation("stop"));
    $("btn-ctrl-reset").addEventListener("click", () => controlSimulation("reset"));

    refresh();
    connectWs();
    pollTimer = setInterval(refresh, POLL_MS);
  }

  document.addEventListener("DOMContentLoaded", init);
})();
