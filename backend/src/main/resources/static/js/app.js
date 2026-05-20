(() => {
  const API = "";
  const POLL_MS = 2000;

  const $ = (id) => document.getElementById(id);

  const state = {
    ws: null,
    pollTimer: null,
  };

  const defaultSimulationPayload = {
    schemaVersion: "v1",
    simTime: 42,
    queueLengths: [3, 2, 4, 1],
    windowCount: 4,
    availableSeats: 26,
    waitingForSeat: 1,
    totalArrived: 155,
    totalServed: 120,
    totalSeated: 118,
    totalFinishedDining: 92,
    newArrivals: 5,
  };

  const defaultDecisionPayload = {
    schemaVersion: "v1",
    simTime: 42,
    queueLengths: [3, 2, 4, 1],
    windowCount: 4,
    availableSeats: 26,
    waitingForSeat: 1,
    newArrivals: 5,
  };

  function log(message, level = "info") {
    const el = $("event-log");
    const line = document.createElement("div");
    line.className = `log-entry ${level}`;
    const ts = new Date().toLocaleTimeString();
    line.innerHTML = `<span class="ts">[${ts}]</span> ${escapeHtml(message)}`;
    el.prepend(line);
    while (el.children.length > 120) {
      el.removeChild(el.lastChild);
    }
  }

  function escapeHtml(str) {
    return String(str)
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;");
  }

  function setBadge(id, ok, label) {
    const el = $(id);
    el.textContent = label;
    el.className = `badge ${ok ? "ok" : "err"}`;
  }

  function wsUrl() {
    const proto = location.protocol === "https:" ? "wss:" : "ws:";
    return `${proto}//${location.host}/ws/simulation`;
  }

  function connectWebSocket() {
    if (state.ws) {
      state.ws.close();
    }

    const url = wsUrl();
    log(`连接 WebSocket: ${url}`);
    const ws = new WebSocket(url);

    ws.onopen = () => {
      setBadge("ws-badge", true, "WebSocket 已连接");
      log("WebSocket 连接成功", "success");
    };

    ws.onclose = () => {
      setBadge("ws-badge", false, "WebSocket 断开");
      log("WebSocket 已断开，5 秒后重连…", "error");
      setTimeout(connectWebSocket, 5000);
    };

    ws.onerror = () => {
      setBadge("ws-badge", false, "WebSocket 异常");
    };

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        if (data.type === "decision") {
          renderDecision(data.response, data.request);
          log(`收到决策推送: ${JSON.stringify(data.response?.allocation)}`, "success");
        } else {
          renderSimulation(data);
          log(`收到仿真推送: t=${data.simTime}`, "success");
        }
      } catch {
        log(`WebSocket 原始消息: ${event.data}`);
      }
    };

    state.ws = ws;
  }

  async function fetchJson(path, options) {
    const res = await fetch(`${API}${path}`, {
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      ...options,
    });
    const text = await res.text();
    let body = null;
    try {
      body = text ? JSON.parse(text) : null;
    } catch {
      body = text;
    }
    return { ok: res.ok, status: res.status, body };
  }

  async function checkHealth() {
    try {
      const { ok, body } = await fetchJson("/health");
      setBadge("api-badge", ok, ok ? "后端在线" : "后端异常");
      if (ok) {
        log(`健康检查通过: ${JSON.stringify(body)}`, "success");
      }
      return ok;
    } catch (e) {
      setBadge("api-badge", false, "后端离线");
      log(`健康检查失败: ${e.message}`, "error");
      return false;
    }
  }

  async function pollRealtime() {
    try {
      const { ok, body } = await fetchJson("/api/frontend/realtime/status");
      if (ok && body) {
        renderSimulation(body);
        $("last-update").textContent = `更新于 ${new Date().toLocaleTimeString()}`;
      }
    } catch (e) {
      log(`轮询失败: ${e.message}`, "error");
    }

    try {
      const { ok, body } = await fetchJson("/api/frontend/last-decision");
      if (ok && body?.response) {
        renderDecision(body.response, body.request);
      }
    } catch {
      /* ignore */
    }
  }

  function renderSimulation(data) {
    if (!data) {
      $("metrics-empty").hidden = false;
      return;
    }
    $("metrics-empty").hidden = true;

    const set = (id, val) => {
      const el = $(id);
      if (el) el.textContent = val ?? "—";
    };

    set("m-sim-time", data.simTime);
    set("m-new-arrivals", data.newArrivals);
    set("m-available-seats", data.availableSeats);
    set("m-waiting", data.waitingForSeat);
    set("m-arrived", data.totalArrived);
    set("m-served", data.totalServed);
    set("m-seated", data.totalSeated);
    set("m-finished", data.totalFinishedDining);

    const avg =
      data.avgQueueLength ??
      (Array.isArray(data.queueLengths) && data.queueLengths.length
        ? (
            data.queueLengths.reduce((a, b) => a + b, 0) / data.queueLengths.length
          ).toFixed(1)
        : "—");
    set("m-avg-queue", avg);

    renderQueueBars(data.queueLengths || []);
  }

  function renderQueueBars(lengths) {
    const container = $("queue-bars");
    container.innerHTML = "";
    if (!lengths.length) {
      container.innerHTML =
        '<p class="empty-state">暂无队列数据，请发送仿真上报或运行 C++ 引擎。</p>';
      return;
    }
    const max = Math.max(...lengths, 1);
    lengths.forEach((len, i) => {
      const row = document.createElement("div");
      row.className = "queue-row";

      const label = document.createElement("span");
      label.textContent = `窗口 ${i + 1}`;

      const track = document.createElement("div");
      track.className = "queue-track";
      const fill = document.createElement("div");
      fill.className = "queue-fill";
      fill.style.width = `${Math.round((len / max) * 100)}%`;
      track.appendChild(fill);

      const count = document.createElement("strong");
      count.textContent = String(len);

      row.append(label, track, count);
      container.appendChild(row);
    });
  }

  function renderDecision(response, request) {
    const container = $("allocation-pills");
    if (!response?.allocation) {
      container.innerHTML = '<p class="empty-state">暂无 AI 决策数据。</p>';
      return;
    }
    container.innerHTML = response.allocation
      .map(
        (n, i) =>
          `<span class="pill">窗口 ${i + 1}: +${n}</span>`
      )
      .join("");

    const meta = [];
    if (response.source) meta.push(`来源: ${response.source}`);
    if (response.decisionMode) meta.push(`模式: ${response.decisionMode}`);
    if (request?.newArrivals != null) meta.push(`新到达: ${request.newArrivals}`);
    $("decision-meta").textContent = meta.join(" · ");
  }

  async function sendSimulation() {
    const raw = $("sim-json").value;
    try {
      const payload = JSON.parse(raw);
      const { ok, status, body } = await fetchJson("/api/simulation/data", {
        method: "POST",
        body: JSON.stringify(payload),
      });
      $("sim-response").textContent = JSON.stringify({ status, body }, null, 2);
      if (ok) {
        log("状态上报成功", "success");
        renderSimulation(payload);
      } else {
        log(`状态上报失败 (${status})`, "error");
      }
    } catch (e) {
      $("sim-response").textContent = e.message;
      log(`JSON 解析失败: ${e.message}`, "error");
    }
  }

  async function sendDecision() {
    const raw = $("decision-json").value;
    try {
      const payload = JSON.parse(raw);
      const { ok, status, body } = await fetchJson("/api/ai/decision", {
        method: "POST",
        body: JSON.stringify(payload),
      });
      $("decision-response").textContent = JSON.stringify({ status, body }, null, 2);
      if (ok) {
        log(`AI 决策成功: ${JSON.stringify(body.allocation)}`, "success");
        renderDecision(body, payload);
      } else {
        log(`AI 决策失败 (${status})`, "error");
      }
    } catch (e) {
      $("decision-response").textContent = e.message;
      log(`JSON 解析失败: ${e.message}`, "error");
    }
  }

  function setupTabs() {
    document.querySelectorAll(".tab").forEach((tab) => {
      tab.addEventListener("click", () => {
        document.querySelectorAll(".tab").forEach((t) => t.classList.remove("active"));
        document.querySelectorAll(".tab-panel").forEach((p) => p.hidden = true);
        tab.classList.add("active");
        $(`panel-${tab.dataset.tab}`).hidden = false;
      });
    });
  }

  function tickSimTime() {
    const input = $("sim-json");
    const decisionInput = $("decision-json");
    try {
      const sim = JSON.parse(input.value);
      sim.simTime = (sim.simTime || 0) + 1;
      input.value = JSON.stringify(sim, null, 2);
      const dec = JSON.parse(decisionInput.value);
      dec.simTime = sim.simTime;
      decisionInput.value = JSON.stringify(dec, null, 2);
    } catch {
      /* ignore */
    }
  }

  function init() {
    $("sim-json").value = JSON.stringify(defaultSimulationPayload, null, 2);
    $("decision-json").value = JSON.stringify(defaultDecisionPayload, null, 2);

    $("btn-send-sim").addEventListener("click", sendSimulation);
    $("btn-send-decision").addEventListener("click", sendDecision);
    $("btn-tick-time").addEventListener("click", tickSimTime);
    $("btn-refresh").addEventListener("click", () => {
      checkHealth();
      pollRealtime();
    });

    setupTabs();
    checkHealth();
    pollRealtime();
    connectWebSocket();
    state.pollTimer = setInterval(pollRealtime, POLL_MS);

    log("控制台已就绪。可模拟 C++ 请求或等待仿真引擎推送。");
  }

  document.addEventListener("DOMContentLoaded", init);
})();
