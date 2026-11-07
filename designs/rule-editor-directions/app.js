const directions = {
  a: {
    title: "晨雾，给专注留白。",
    description: "浅色、呼吸感与柔和刻度。保留安静的氛围，也让每次设置都轻松直接。",
  },
  b: {
    title: "静夜，让世界暂时安静。",
    description: "深绿、月光与半透明面板。更接近禅定空间的沉浸感，首页和设置页保持同一种情绪。",
  },
  c: {
    title: "秩序，简单得刚刚好。",
    description: "紧凑、精确而现代。用清晰的数字控制和快捷选项，让高频设置变得毫不费力。",
  },
};

const state = {
  direction: localStorage.getItem("jiexian-direction") || "a",
  type: "daily",
  minutes: 30,
};

function statusBar() {
  return `<div class="status-bar"><span>9:41</span><div class="status-icons"><i class="signal"></i><i class="wifi"></i><i class="battery"></i></div></div>`;
}

function brand() {
  return `<div class="brand-lockup"><span class="mini-brand"></span><div><strong>界限</strong><small>JIEXIAN · 安静有度</small></div></div>`;
}

function appIcons(size = "") {
  return `<div class="app-icons">
    <img class="app-icon ${size}" src="assets/chrome.png" alt="Chrome">
    <img class="app-icon ${size}" src="assets/camera.png" alt="相机">
    <img class="app-icon ${size}" src="assets/calendar.png" alt="日历">
  </div>`;
}

function editableApps(size = "large") {
  return `<div class="app-icons">
    <img class="app-icon ${size}" src="assets/chrome.png" alt="Chrome">
    <img class="app-icon ${size}" src="assets/camera.png" alt="相机">
    <img class="app-icon ${size}" src="assets/calendar.png" alt="日历">
    <span class="app-add">＋</span>
  </div>`;
}

function homeIndicator() {
  return `<span class="home-indicator"></span>`;
}

function device(direction, page, content) {
  const label = page === "home" ? "01 · 首页" : "02 · 规则设置";
  const english = page === "home" ? "HOME" : "RULE EDITOR";
  return `<div class="device-wrap">
    <div class="device-label"><strong>${label}</strong><span>${english}</span></div>
    <div class="phone"><div class="screen ${direction}" data-screen-label="${direction}-${page}">${statusBar()}${content}${homeIndicator()}</div></div>
  </div>`;
}

function typeSwitch() {
  return `<div class="type-switch">
    <button class="type-option ${state.type === "daily" ? "active" : ""}" data-type="daily">每日限额</button>
    <button class="type-option ${state.type === "window" ? "active" : ""}" data-type="window">禁用时段</button>
  </div>`;
}

function settingsHeader() {
  return `<div class="settings-header"><button class="icon-button" aria-label="返回">‹</button><h2>新建界限</h2></div>`;
}

function cta(caption = "未来可调整 · 已开始的限制按原时间结束") {
  return `<div class="settings-cta"><button class="primary-cta">创建界限</button><div class="cta-caption">${caption}</div></div>`;
}

function presetButtons() {
  return `<div class="duration-presets">
    ${[15, 30, 60, 120].map((value) => `<button class="preset ${state.minutes === value ? "active" : ""}" data-minutes="${value}">${value < 60 ? `${value} 分钟` : `${value / 60} 小时`}</button>`).join("")}
  </div>`;
}

function homeA() {
  return `<div class="home-content">
    <div class="home-header">${brand()}<button class="icon-button">＋</button></div>
    <section class="home-hero">
      <span class="hero-orbit"></span>
      <div class="hero-status"><i class="green-dot"></i>界限守护中</div>
      <h1>把时间，<br>还给自己。</h1>
      <p>不被打扰，也不必用力坚持</p>
      <div class="hero-stats"><span><strong>2</strong>条界限</span><span><strong>3</strong>个应用</span></div>
    </section>
    <div class="section-line home-section"><h3>我的界限</h3><span>2 条规则</span></div>
    <section class="home-rule">
      <div class="rule-top"><span class="rule-symbol">◷</span><div><h3>睡前免打扰</h3><p>每天 · 禁用时段</p></div><span class="pill"><i class="green-dot"></i>限制中</span></div>
      ${appIcons()}<span class="app-caption">3 个应用</span>
      <div class="time-row"><strong>23:00 — 09:00</strong><span>09:00 恢复</span></div>
    </section>
  </div>`;
}

function homeB() {
  const hour = new Date().getHours();
  const copy = hour < 5 || hour >= 23
    ? ["夜深了，<br>让世界暂时安静。", "留下此刻的宁静，<br>其他事情可以等到明天。"]
    : hour < 11
      ? ["天亮了，<br>慢一点开始。", "先留一点安静给自己，<br>再走向今天的喧嚣。"]
      : hour < 18
        ? ["此刻，<br>让世界慢下来。", "不必时刻回应世界，<br>专注本身就是答案。"]
        : ["夜幕渐深，<br>把喧嚣留在身后。", "今天已经足够忙碌，<br>现在，把时间还给自己。"];
  return `<div class="ambient"></div><div class="home-content">
    <div class="home-header">${brand()}<button class="icon-button">＋</button></div>
    <section class="night-sky">
      <span class="moon"></span>
      <span class="pill"><i class="green-dot"></i>此刻，安静正好</span>
      <h1>${copy[0]}</h1>
      <p>${copy[1]}</p>
    </section>
    <div class="night-count"><strong>1</strong><span>条界限正在守护</span></div>
    <section class="home-rule">
      <span class="pill">今晚 · 09:00 恢复</span>
      <h3>睡前免打扰</h3>
      <div class="schedule">23:00 — 09:00</div>
      <div class="sub">3 个应用正在本次限制中</div>
      ${appIcons("small")}
    </section>
  </div>`;
}

function homeC() {
  return `<div class="home-content">
    <div class="home-header">${brand()}<button class="icon-button">＋</button></div>
    <div class="home-greeting"><small>11 月 5 日 · 星期四</small><h1>今天，也留一点<br>时间给自己。</h1></div>
    <section class="order-summary">
      <div class="summary-label"><i class="green-dot"></i>界限守护中</div>
      <h2>一切，张弛有度。</h2><p>没有需要立即查看的事情</p>
      <div class="summary-bottom"><span><strong>2</strong>条界限</span><span><strong>3</strong>个应用</span></div>
    </section>
    <div class="section-line home-section"><h3>我的界限</h3><span>管理 ›</span></div>
    <section class="home-rule">
      <div class="rule-top"><div><h3>日间留白</h3><p>每个应用 · 每日 30 分钟</p></div><span class="pill">进行中</span></div>
      ${appIcons("small")}
      <div class="quota-row"><span>今日最少剩余</span><strong>18 分钟</strong></div><div class="quota-track"></div>
    </section>
  </div>`;
}

function timePanelA() {
  return `<div class="time-panel"><div class="time-values"><div><small>开始时间</small><strong>23:00</strong></div><span class="time-dash"></span><div><small>结束时间</small><strong>09:00</strong></div></div><div class="time-arc"></div><p class="helper">每天重复 · 次日恢复使用</p></div>`;
}

function dailyPanelA() {
  const progress = Math.min(270, Math.max(12, (state.minutes / 120) * 270));
  return `<div class="dial-panel"><div class="dial-title">每个应用，每天可以使用</div>
    <div class="dial" style="--progress:${progress}deg"><div class="dial-inner"><strong>${state.minutes}</strong><span>分钟</span></div></div>
    ${presetButtons()}<p class="helper">各应用独立计时，次日 00:00 恢复</p></div>`;
}

function settingsA() {
  return `<div class="settings">${settingsHeader()}<div class="settings-body">
    <label class="field-label">规则名称</label><input class="rule-name" value="睡前免打扰" aria-label="规则名称">
    <div class="settings-apps"><div class="section-line"><h3>应用</h3><span>3 个已选</span></div>${editableApps()}</div>
    ${typeSwitch()}${state.type === "daily" ? dailyPanelA() : timePanelA()}
  </div>${cta()}</div>`;
}

function timePanelB() {
  return `<div class="night-panel"><div class="dial-title">每天的安静时刻</div><div class="night-time-values"><div><small>开始</small><strong>23:00</strong></div><div><small>结束</small><strong>09:00</strong></div></div><div class="moon-arc"></div><p class="helper">跨越午夜 · 次日 09:00 自动恢复</p></div>`;
}

function dailyPanelB() {
  return `<div class="night-panel"><div class="dial-title">每个应用，每天可以使用</div>
    <div class="night-number"><strong>${state.minutes}</strong><span>分钟</span></div>
    <div class="night-range"><span class="night-range-fill" style="width:${Math.min(100, state.minutes / 1.2)}%"></span><span class="night-range-thumb" style="left:calc(${Math.min(100, state.minutes / 1.2)}% - 6px)"></span></div>
    <div class="range-labels"><span>5 分钟</span><span>12 小时</span></div>${presetButtons()}
  </div>`;
}

function settingsB() {
  return `<div class="ambient"></div><div class="settings">${settingsHeader()}<div class="settings-body">
    <div class="name-caption">为这段安静起个名字</div><input class="rule-name" value="睡前免打扰" aria-label="规则名称">
    <div class="settings-apps"><div class="section-line"><h3>一起安静的应用</h3><span>3 个</span></div>${editableApps()}</div>
    ${typeSwitch()}${state.type === "daily" ? dailyPanelB() : timePanelB()}
  </div>${cta("开始之后，本次限制没有临时加时")}</div>`;
}

function timePanelC() {
  return `<div class="order-panel"><div class="order-panel-title">每天禁用时段</div><div class="order-time-grid"><div class="order-time"><small>开始时间</small><strong>23:00</strong></div><div class="order-time"><small>结束时间</small><strong>09:00</strong></div></div><div class="preset-grid"><button class="preset active">睡前</button><button class="preset">工作时间</button><button class="preset">自定义</button></div><p class="helper">支持跨午夜，结束后自动恢复</p></div>`;
}

function dailyPanelC() {
  return `<div class="order-panel"><div class="order-panel-title">每个应用 · 每日使用上限</div>
    <div class="stepper"><button class="step-button" data-step="-5">−</button><div class="step-value"><strong>${state.minutes}</strong><span>分钟</span></div><button class="step-button" data-step="5">＋</button></div>
    <div class="preset-grid">${[15, 30, 60, 90, 120, 180].map((value) => `<button class="preset ${state.minutes === value ? "active" : ""}" data-minutes="${value}">${value < 60 ? `${value} 分钟` : `${value / 60} 小时`}</button>`).join("")}</div>
    <p class="helper">分别计时 · 次日 00:00 恢复</p></div>`;
}

function settingsC() {
  return `<div class="settings">${settingsHeader()}<div class="settings-body">
    <span class="field-label">规则名称</span><div class="name-field"><input class="rule-name" value="睡前免打扰" aria-label="规则名称"></div>
    <div class="settings-apps"><div class="section-line"><h3>包含应用</h3><span>管理 ›</span></div>${editableApps()}</div>
    ${typeSwitch()}${state.type === "daily" ? dailyPanelC() : timePanelC()}
  </div>${cta()}</div>`;
}

const homes = { a: homeA, b: homeB, c: homeC };
const settings = { a: settingsA, b: settingsB, c: settingsC };

function render() {
  const direction = state.direction;
  document.getElementById("direction-title").textContent = directions[direction].title;
  document.getElementById("direction-description").textContent = directions[direction].description;
  const screens = device(direction, "home", homes[direction]()) + device(direction, "settings", settings[direction]());
  document
    .getElementById("device-pair")
    .replaceChildren(document.createRange().createContextualFragment(screens));
  document.querySelectorAll(".direction-button").forEach((button) => {
    const selected = button.dataset.direction === direction;
    button.classList.toggle("active", selected);
    button.setAttribute("aria-selected", String(selected));
  });
  localStorage.setItem("jiexian-direction", direction);
}

document.addEventListener("click", (event) => {
  const directionButton = event.target.closest("[data-direction]");
  if (directionButton) {
    state.direction = directionButton.dataset.direction;
    render();
    return;
  }
  const typeButton = event.target.closest("[data-type]");
  if (typeButton) {
    state.type = typeButton.dataset.type;
    render();
    return;
  }
  const presetButton = event.target.closest("[data-minutes]");
  if (presetButton) {
    state.minutes = Number(presetButton.dataset.minutes);
    render();
    return;
  }
  const stepButton = event.target.closest("[data-step]");
  if (stepButton) {
    state.minutes = Math.min(720, Math.max(5, state.minutes + Number(stepButton.dataset.step)));
    render();
  }
});

render();
