#pragma once
#include <pgmspace.h>

const char index_html[] PROGMEM = R"rawliteral(
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <title>TWIP Robot Dashboard</title>
  <style>
    :root { color-scheme: dark; font-family: system-ui, -apple-system, sans-serif; background: #0b1220; color: #e5edf8; }
    * { box-sizing: border-box; }
    body { max-width: 1300px; margin: 0 auto; padding: 12px; }
    header, .row { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
    header { justify-content: space-between; margin-bottom: 12px; padding-bottom: 8px; border-bottom: 1px solid #23344d; }
    h1 { font-size: 1.25rem; margin: 0; color: #38bdf8; }
    h2 { font-size: 0.95rem; margin: 0 0 8px 0; color: #93c5fd; }
    h3 { font-size: 0.85rem; margin: 6px 0; color: #38bdf8; text-transform: uppercase; letter-spacing: 0.5px; }
    .layout { display: grid; grid-template-columns: 330px 1fr; gap: 14px; }
    .stack { display: grid; gap: 12px; align-content: start; }
    .panel, .card { background: #142034; border: 1px solid #30435f; border-radius: 10px; padding: 12px; }
    .badge { padding: 5px 10px; border-radius: 6px; font-size: 0.8rem; font-weight: 600; }
    .good { background: #166534; color: #4ade80; }
    .bad { background: #991b1b; color: #f87171; }
    .note { color: #94a3b8; font-size: 0.8rem; line-height: 1.4; margin: 4px 0; }
    button, input { padding: 8px 10px; border: 1px solid #475569; border-radius: 6px; background: #1e293b; color: white; font-size: 0.85rem; }
    button { cursor: pointer; font-weight: 600; transition: background 0.15s; }
    button:hover { background: #334155; }
    button:active { background: #0284c7; }
    button.btn-primary { background: #0284c7; border-color: #38bdf8; color: #fff; }
    button.btn-primary:hover { background: #0369a1; }
    input[type=number], input[type=text] { width: 100%; min-width: 0; text-align: center; }
    input[type=range] { width: 100%; accent-color: #38bdf8; }
    .pid-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 6px; margin-bottom: 8px; }
    .pid-cell { display: flex; flex-direction: column; align-items: center; }
    .pid-cell label { font-size: 0.72rem; color: #94a3b8; margin-bottom: 2px; }
    #pad { width: 220px; height: 220px; margin: 10px auto; border-radius: 50%; border: 2px solid #475569;
           position: relative; touch-action: none; user-select: none; background: #0f172a; box-shadow: inset 0 0 20px rgba(0,0,0,0.6); }
    #knob { position: absolute; left: 50%; top: 50%; width: 60px; height: 60px; border-radius: 50%;
            background: #38bdf8; pointer-events: none; transform: translate(-50%,-50%); box-shadow: 0 4px 12px rgba(56,189,248,0.5); }
    #cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 8px; }
    .card { padding: 8px 10px; }
    .card .label { color: #94a3b8; font-size: 0.75rem; }
    .card .value { font-size: 1.05rem; font-weight: 700; margin-top: 3px; font-variant-numeric: tabular-nums; }
    .plots { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-top: 12px; }
    .plot-panel { background: #142034; border: 1px solid #30435f; border-radius: 10px; padding: 10px; }
    canvas { display: block; width: 100%; height: 160px; }
    .legend { font-size: 0.75rem; margin-top: 4px; display: flex; gap: 8px; }
    @media(max-width: 860px) { .layout { grid-template-columns: 1fr; } }
    @media(max-width: 540px) { .plots { grid-template-columns: 1fr; } }
  </style>
</head>
<body>
  <header>
    <h1>TWIP Stepper Robot</h1>
    <div class="row">
      <span id="connection" class="badge bad">Connecting...</span>
      <span id="state" class="badge bad">Unknown</span>
      <span id="heap" class="badge" style="background:#334155;color:#94a3b8">Heap: --</span>
    </div>
  </header>

  <div class="layout">
    <!-- LEFT COLUMN: CONTROLS & TUNING -->
    <aside class="stack">
      <!-- DRIVE JOYSTICK -->
      <section class="panel">
        <h2>Remote Drive</h2>
        <div style="display:flex; justify-content:space-between; margin-bottom:4px;">
          <span class="note">Sensitivity Scale</span>
          <strong id="scaleValue" style="color:#38bdf8; font-size:0.85rem">40%</strong>
        </div>
        <input id="scale" type="range" min="10" max="100" value="40">
        <div id="pad"><div id="knob"></div></div>
        <div id="driveText" class="note" style="text-align:center; font-weight:600; color:#cbd5e1">V: 0 &middot; S: 0 steps/s</div>
      </section>

      <!-- PID & SYSTEM SETTINGS -->
      <section class="panel">
        <h2>Live Tuning</h2>
        
        <h3>Inner Loop (Balance)</h3>
        <div class="pid-grid">
          <div class="pid-cell"><label>Kp</label><input type="text" id="kp1" inputmode="decimal"></div>
          <div class="pid-cell"><label>Ki</label><input type="text" id="ki1" inputmode="decimal"></div>
          <div class="pid-cell"><label>Kd</label><input type="text" id="kd1" inputmode="decimal"></div>
        </div>

        <h3>Outer Loop (Speed)</h3>
        <div class="pid-grid">
          <div class="pid-cell"><label>Kp</label><input type="text" id="kp2" inputmode="decimal"></div>
          <div class="pid-cell"><label>Ki</label><input type="text" id="ki2" inputmode="decimal"></div>
          <div class="pid-cell"><label>Kd</label><input type="text" id="kd2" inputmode="decimal"></div>
        </div>

        <h3>System Configuration</h3>
        <div class="pid-grid">
          <div class="pid-cell"><label>Offset (&deg;)</label><input type="text" id="ao" inputmode="decimal"></div>
          <div class="pid-cell"><label>Max Accel</label><input type="text" id="ma" inputmode="decimal"></div>
          <div class="pid-cell"><label>Max Spd</label><input type="text" id="msps" inputmode="decimal"></div>
        </div>

        <div class="row" style="margin-top:10px;">
          <button id="btnRead" style="flex:1" onclick="loadPID()">Read Current</button>
          <button id="btnUpdate" class="btn-primary" style="flex:2" onclick="sendPID()">UPDATE SETTINGS</button>
        </div>
        <p id="pidStatus" class="note" style="text-align:center; min-height:1.2em;"></p>
      </section>
    </aside>

    <!-- RIGHT COLUMN: TELEMETRY & CHARTS -->
    <main class="stack">
      <!-- TELEMETRY CARDS -->
      <section class="panel">
        <div class="row" style="justify-content:space-between; margin-bottom:10px;">
          <h2>Live Telemetry</h2>
          <div class="row">
            <button id="pause">Pause charts</button>
            <button id="clear">Clear</button>
            <button id="csv" class="btn-primary">Download CSV</button>
          </div>
        </div>
        <div id="cards"></div>
      </section>

      <!-- LIVE CANVAS CHARTS -->
      <div id="plots" class="plots"></div>
    </main>
  </div>

  <script>
  'use strict';
  const $ = id => document.getElementById(id);
  const finite = v => typeof v === 'number' && Number.isFinite(v);

  // --- Telemetry Cards Configuration ---
  const fields = [
    ['pitch', 'Pitch Angle', '&deg;', 2],
    ['setpoint', 'Angle Setpoint', '&deg;', 2],
    ['offset', 'Angle Offset', '&deg;', 2],
    ['motorOut', 'Motor Output', 'steps/s', 0],
    ['speed', 'Speed (Est)', 'steps/s', 0],
    ['targetSpeed', 'Target Speed', 'steps/s', 0],
    ['steer', 'Steer Offset', 'steps/s', 0],
    ['encL', 'Encoder Left', 'cts', 0],
    ['encR', 'Encoder Right', 'cts', 0]
  ];

  for (const [key, label] of fields) {
    const card = document.createElement('div'); card.className = 'card';
    const name = document.createElement('div'); name.className = 'label'; name.innerHTML = label;
    const value = document.createElement('div'); value.className = 'value';
    value.id = 'v-' + key; value.textContent = '--';
    card.append(name, value);
    $('cards').append(card);
  }

  // --- Chart Scopes Configuration ---
  const colors = ['#38bdf8', '#fbbf24', '#a78bfa', '#f87171'];
  const definitions = [
    ['anglePlot', 'Balance Angle & Setpoint (&deg;)', ['pitch', 'setpoint']],
    ['speedPlot', 'Speed & Target (steps/s)', ['speed', 'targetSpeed']],
    ['motorPlot', 'Motor Output (steps/s)', ['motorOut']],
    ['steerPlot', 'Steer Offset (steps/s)', ['steer']],
    ['encPlot', 'Virtual Encoders (steps)', ['encL', 'encR']]
  ];

  for (const [id, title, keys] of definitions) {
    const panel = document.createElement('section'); panel.className = 'plot-panel';
    const heading = document.createElement('h2'); heading.innerHTML = title;
    const canvas = document.createElement('canvas'); canvas.id = id;
    const legend = document.createElement('div'); legend.className = 'legend';
    keys.forEach((key, i) => {
      const item = document.createElement('span');
      item.style.color = colors[i];
      item.textContent = (i ? ' / ' : '') + key;
      legend.append(item);
    });
    panel.append(heading, canvas, legend);
    $('plots').append(panel);
  }

  // --- WebSocket & Telemetry Recording ---
  let ws;
  let history = [];
  let paused = false, dirty = true, lastDraw = 0;
  const gateway = `ws://${window.location.hostname}/ws`;

  function setBadge(id, text, good) {
    $(id).textContent = text;
    $(id).className = 'badge ' + (good ? 'good' : 'bad');
  }

  function initWebSocket() {
    ws = new WebSocket(gateway);
    ws.onopen = () => {
      setBadge('connection', 'Connected', true);
    };
    ws.onclose = () => {
      setBadge('connection', 'Disconnected', false);
      setBadge('state', 'Unknown', false);
      setTimeout(initWebSocket, 2000);
    };
    ws.onmessage = (event) => {
      let d;
      try { d = JSON.parse(event.data); } catch { return; }
      if (d.type !== 'telemetry') return;

      setBadge('state', d.state, d.state === 'BALANCING');
      if (d.heap) $('heap').textContent = `Heap: ${Math.round(d.heap / 1024)} KB`;

      for (const [key,, unit, places] of fields) {
        const value = d[key];
        const el = $('v-' + key);
        if (el) {
          el.innerHTML = finite(value) ? `${value.toFixed(places)} <small style="font-size:0.75rem;color:#94a3b8">${unit}</small>` : '--';
        }
      }

      history.push({ ...d, browserTime: new Date().toISOString() });
      if (history.length > 1200) history.shift();
      dirty = true;
    };
  }

  // --- REST PID Fetch & Update ---
  function loadPID() {
    $('pidStatus').textContent = 'Fetching settings...';
    $('pidStatus').style.color = '#38bdf8';
    fetch('/get_pid')
      .then(r => r.json())
      .then(d => {
        $('kp1').value = d.kp1; $('ki1').value = d.ki1; $('kd1').value = d.kd1;
        $('kp2').value = d.kp2; $('ki2').value = d.ki2; $('kd2').value = d.kd2;
        $('ao').value = d.ao;   $('ma').value = d.ma;   $('msps').value = d.msps;
        $('pidStatus').textContent = 'Settings loaded.';
        $('pidStatus').style.color = '#4ade80';
      }).catch(e => {
        $('pidStatus').textContent = 'Fetch failed.';
        $('pidStatus').style.color = '#f87171';
      });
  }

  async function sendPID() {
    const btn = $('btnUpdate');
    btn.innerText = 'UPDATING...'; btn.style.background = '#d97706';
    const p1 = $('kp1').value, i1 = $('ki1').value, d1 = $('kd1').value;
    const p2 = $('kp2').value, i2 = $('ki2').value, d2 = $('kd2').value;
    const ao = $('ao').value,  ma = $('ma').value,  msps = $('msps').value;

    try {
      const resp = await fetch(`/set_pid?kp1=${p1}&ki1=${i1}&kd1=${d1}&kp2=${p2}&ki2=${i2}&kd2=${d2}&ao=${ao}&ma=${ma}&msps=${msps}`);
      if (resp.ok) {
        btn.innerText = 'UPDATED OK!'; btn.style.background = '#16a34a';
        $('pidStatus').textContent = 'Parameters updated successfully in RAM!';
        $('pidStatus').style.color = '#4ade80';
      } else {
        throw new Error('Not OK');
      }
    } catch (e) {
      btn.innerText = 'FAILED!'; btn.style.background = '#dc2626';
      $('pidStatus').textContent = 'Update failed. Check connection.';
      $('pidStatus').style.color = '#f87171';
    } finally {
      setTimeout(() => { btn.innerText = 'UPDATE SETTINGS'; btn.style.background = '#0284c7'; }, 1800);
    }
  }

  // --- Virtual Joystick ---
  const pad = $('pad');
  const knob = $('knob');
  let pointer = null;
  let joyX = 0, joyY = 0;
  const MAX_RAW_SPEED = 3000;
  const MAX_RAW_STEER = 800;

  function calcExpo(val) {
    return Math.sign(val) * Math.pow(Math.abs(val), 1.4);
  }

  function renderJoy() {
    knob.style.transform = `translate(-50%,-50%) translate(${joyX * 75}px, ${joyY * 75}px)`;
    const scale = Number($('scale').value) / 100;
    const curV = Math.round(-calcExpo(joyY) * MAX_RAW_SPEED * scale);
    const curS = Math.round(calcExpo(joyX) * MAX_RAW_STEER * scale);
    $('driveText').textContent = `V: ${curV} \u00B7 S: ${curS} steps/s`;
  }

  let driveInterval = null;

  function transmitDrive() {
    if (ws && ws.readyState === WebSocket.OPEN) {
      const scale = Number($('scale').value) / 100;
      const curV = Math.round(-calcExpo(joyY) * MAX_RAW_SPEED * scale);
      const curS = Math.round(calcExpo(joyX) * MAX_RAW_STEER * scale);
      ws.send(`V:${curV},S:${curS}`);
    }
  }

  function moveJoy(e) {
    if (e.pointerId !== pointer) return;
    const rect = pad.getBoundingClientRect();
    let x = (e.clientX - rect.left - rect.width / 2) / 75;
    let y = (e.clientY - rect.top - rect.height / 2) / 75;
    const dist = Math.hypot(x, y);
    if (dist > 1) { x /= dist; y /= dist; }
    joyX = x; joyY = y;
    renderJoy();
    transmitDrive();
  }

  function stopJoy(e) {
    if (pointer !== null && (e ? e.pointerId === pointer : true)) {
      if (pointer !== null && pad.hasPointerCapture(pointer)) {
        try { pad.releasePointerCapture(pointer); } catch (err) {}
      }
      pointer = null;
      if (driveInterval) {
        clearInterval(driveInterval);
        driveInterval = null;
      }
      joyX = 0; joyY = 0;
      renderJoy();
      transmitDrive(); // Send neutral stop
    }
  }

  pad.onpointerdown = (e) => {
    if (pointer !== null || (e.pointerType === 'mouse' && e.button !== 0)) return;
    e.preventDefault();
    pointer = e.pointerId;
    try { pad.setPointerCapture(pointer); } catch (err) {}
    moveJoy(e);
    transmitDrive();
    if (!driveInterval) {
      driveInterval = setInterval(transmitDrive, 50);
    }
  };
  pad.onpointermove = moveJoy;
  pad.onpointerup = stopJoy;
  pad.onpointercancel = stopJoy;
  window.addEventListener('blur', () => stopJoy(null));
  window.addEventListener('pagehide', () => stopJoy(null));
  document.addEventListener('visibilitychange', () => { if (document.hidden) stopJoy(null); });

  $('scale').oninput = () => {
    $('scaleValue').textContent = $('scale').value + '%';
    renderJoy();
  };

  // --- Real-time Canvas Graphing ---
  function drawPlot(id, keys) {
    const canvas = $(id);
    if (!canvas) return;
    const w = canvas.clientWidth, h = canvas.clientHeight;
    if (!w || !h) return;
    const ratio = window.devicePixelRatio || 1;
    canvas.width = Math.round(w * ratio);
    canvas.height = Math.round(h * ratio);
    const ctx = canvas.getContext('2d');
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    ctx.clearRect(0, 0, w, h);

    if (!history.length) return;
    const end = history[history.length - 1].ms;
    const age = row => (end - row.ms) >>> 0;
    const rows = history.filter(row => age(row) <= 10000);

    let low = Infinity, high = -Infinity;
    for (const row of rows) {
      for (const key of keys) {
        if (finite(row[key])) {
          low = Math.min(low, row[key]);
          high = Math.max(high, row[key]);
        }
      }
    }
    if (!Number.isFinite(low)) { low = -1; high = 1; }
    const padVal = Math.max((high - low) * 0.15, 0.5);
    low -= padVal; high += padVal;

    const left = 55, right = w - 8, top = 8, bottom = h - 20;
    const x = row => right - (age(row) / 10000) * (right - left);
    const y = val => bottom - ((val - low) / (high - low)) * (bottom - top);

    // Grid lines & labels
    ctx.font = '10px system-ui';
    for (let i = 0; i <= 3; i++) {
      const val = low + (high - low) * i / 3;
      const yy = y(val);
      ctx.strokeStyle = '#1e293b';
      ctx.beginPath(); ctx.moveTo(left, yy); ctx.lineTo(right, yy); ctx.stroke();
      ctx.fillStyle = '#64748b';
      ctx.fillText(val.toFixed(1), 2, yy + 3);
    }
    ctx.fillText('-10s', left, h - 4);
    ctx.fillText('Now', right - 22, h - 4);

    // Data curves
    keys.forEach((key, idx) => {
      ctx.strokeStyle = colors[idx];
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      let prev = null;
      for (const row of rows) {
        if (!finite(row[key])) { prev = null; continue; }
        const gap = prev ? ((row.ms - prev.ms) >>> 0) : 0;
        if (!prev || gap > 250) ctx.moveTo(x(row), y(row[key]));
        else ctx.lineTo(x(row), y(row[key]));
        prev = row;
      }
      ctx.stroke();
    });
  }

  function animate(now) {
    if (!paused && dirty && now - lastDraw >= 80) {
      definitions.forEach(([id,, keys]) => drawPlot(id, keys));
      lastDraw = now;
      dirty = false;
    }
    requestAnimationFrame(animate);
  }

  // --- Buttons: Pause, Clear, CSV ---
  $('pause').onclick = () => {
    paused = !paused;
    $('pause').textContent = paused ? 'Resume charts' : 'Pause charts';
    dirty = true;
  };
  $('clear').onclick = () => {
    history = [];
    dirty = true;
  };
  $('csv').onclick = () => {
    if (!history.length) { alert('No samples recorded yet.'); return; }
    const columns = [...new Set(history.flatMap(row => Object.keys(row)))];
    const escape = val => '"' + String(val ?? '').replaceAll('"', '""') + '"';
    const lines = [
      columns.map(escape).join(','),
      ...history.map(row => columns.map(key => escape(row[key])).join(','))
    ];
    const blob = new Blob([lines.join('\r\n')], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'twip-telemetry-' + Date.now() + '.csv';
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  };

  window.addEventListener('resize', () => { dirty = true; });

  window.onload = () => {
    initWebSocket();
    loadPID();
    renderJoy();
    requestAnimationFrame(animate);
  };
  </script>
</body>
</html>
)rawliteral";

