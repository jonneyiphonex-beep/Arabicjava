const numberFormat = new Intl.NumberFormat('ar-EG');
const percentFormat = new Intl.NumberFormat('ar-EG', { maximumFractionDigits: 2 });
const state = {
  requests: [],
  ideIssues: [],
  discovery: null,
  filter: 'all',
  ideIssueFilter: 'all',
  refreshing: false,
  toastTimer: null,
  knownErrorIds: new Set(),
  errorBaselineReady: false,
  browserNotificationsEnabled: false
};

const elements = {
  connection: document.querySelector('#connection-state'),
  connectionLabel: document.querySelector('#connection-label'),
  totalRequests: document.querySelector('#total-requests'),
  totalErrors: document.querySelector('#total-errors'),
  errorRate: document.querySelector('#error-rate'),
  averageTime: document.querySelector('#average-time'),
  maxTime: document.querySelector('#max-time'),
  sampleSize: document.querySelector('#sample-size'),
  uptime: document.querySelector('#uptime'),
  rows: document.querySelector('#request-rows'),
  errorList: document.querySelector('#error-list'),
  errorCount: document.querySelector('#error-count'),
  ideIssueList: document.querySelector('#ide-issue-list'),
  ideIssueCount: document.querySelector('#ide-issue-count'),
  discoveryRoot: document.querySelector('#discovery-root'),
  discoverySummary: document.querySelector('#discovery-summary'),
  discoveredProjects: document.querySelector('#discovered-projects'),
  toolchainList: document.querySelector('#toolchain-list'),
  discoveryRefresh: document.querySelector('#discovery-refresh'),
  chart: document.querySelector('#latency-chart'),
  chartCount: document.querySelector('#chart-count'),
  toast: document.querySelector('#toast')
};

Object.assign(elements, {
  cpuValue: document.querySelector('#cpu-value'),
  cpuDetail: document.querySelector('#cpu-detail'),
  cpuBar: document.querySelector('#cpu-bar'),
  ramValue: document.querySelector('#ram-value'),
  ramDetail: document.querySelector('#ram-detail'),
  ramBar: document.querySelector('#ram-bar'),
  diskValue: document.querySelector('#disk-value'),
  diskDetail: document.querySelector('#disk-detail'),
  diskBar: document.querySelector('#disk-bar'),
  diskRead: document.querySelector('#disk-read'),
  diskWrite: document.querySelector('#disk-write'),
  networkRtt: document.querySelector('#network-rtt'),
  networkDownlink: document.querySelector('#network-downlink'),
  networkType: document.querySelector('#network-type'),
  boardName: document.querySelector('#board-name'),
  coolingState: document.querySelector('#cooling-state'),
  maxTemperature: document.querySelector('#max-temperature'),
  temperatures: document.querySelector('#temperature-sensors'),
  fans: document.querySelector('#fan-sensors'),
  coolingDevices: document.querySelector('#cooling-sensors')
});
elements.notificationToggle = document.querySelector('#notification-toggle');

function formatNumber(value) {
  return numberFormat.format(Number(value) || 0);
}

function showToast(message) {
  elements.toast.textContent = message;
  elements.toast.classList.add('visible');
  window.clearTimeout(state.toastTimer);
  state.toastTimer = window.setTimeout(() => elements.toast.classList.remove('visible'), 2600);
}

function setConnection(connected, label = connected ? 'متصل الآن' : 'تعذر الاتصال') {
  elements.connection.dataset.state = connected ? 'online' : 'offline';
  elements.connectionLabel.textContent = label;
}

async function getJson(path) {
  const response = await fetch(path, { headers: { Accept: 'application/json' } });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
}

function updateSummary(summary) {
  elements.totalRequests.textContent = formatNumber(summary.totalRequests);
  elements.totalErrors.textContent = formatNumber(summary.totalErrors);
  elements.errorRate.textContent = `${percentFormat.format(summary.errorRatePercent)}٪`;
  elements.averageTime.textContent = formatNumber(summary.averageNanos);
  elements.maxTime.textContent = formatNumber(summary.maxNanos);
  elements.sampleSize.textContent = formatNumber(summary.recentSampleSize);
  elements.uptime.textContent = `مدة التشغيل: ${formatNumber(summary.uptimeSeconds)} ثانية`;
}

function formatPercent(value) {
  return value === null || value === undefined || !Number.isFinite(Number(value))
    ? 'غير متاح'
    : `${percentFormat.format(value)}٪`;
}

function formatGigabytes(bytes) {
  if (!Number.isFinite(Number(bytes)) || Number(bytes) <= 0) return 'غير متاح';
  return `${new Intl.NumberFormat('en-US', { maximumFractionDigits: 1 }).format(Number(bytes) / 1_000_000_000)} GB`;
}

function formatBytesPerSecond(value) {
  if (value === null || value === undefined || !Number.isFinite(Number(value))) return 'غير متاح';
  const bytes = Number(value);
  if (bytes < 1_000_000) return `${new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 }).format(bytes / 1_000)} KB/s`;
  return `${new Intl.NumberFormat('en-US', { maximumFractionDigits: 1 }).format(bytes / 1_000_000)} MB/s`;
}

function setResourceBar(bar, value) {
  const numericValue = Number(value);
  bar.style.width = value === null || value === undefined || !Number.isFinite(numericValue)
    ? '0%'
    : `${Math.max(0, Math.min(100, numericValue))}%`;
}

function renderSensors(container, sensors, emptyMessage, formatter) {
  container.replaceChildren();
  if (!sensors.length) {
    const empty = document.createElement('span');
    empty.className = 'sensor-empty';
    empty.textContent = emptyMessage;
    container.append(empty);
    return;
  }
  for (const sensor of sensors) {
    const row = document.createElement('span');
    row.className = 'sensor-reading';
    const label = document.createElement('span');
    label.textContent = sensor.label;
    const value = document.createElement('b');
    value.textContent = formatter(sensor);
    row.append(label, value);
    container.append(row);
  }
}

function updateSystem(system, roundTripMs) {
  elements.cpuValue.textContent = formatPercent(system.cpuUsagePercent);
  elements.cpuDetail.textContent = `${formatNumber(system.logicalProcessors)} أنوية منطقية`;
  setResourceBar(elements.cpuBar, system.cpuUsagePercent);

  elements.ramValue.textContent = formatPercent(system.memoryUsagePercent);
  elements.ramDetail.textContent = `${formatGigabytes(system.usedMemoryBytes)} مستخدم من ${formatGigabytes(system.totalMemoryBytes)}`;
  setResourceBar(elements.ramBar, system.memoryUsagePercent);

  elements.diskValue.textContent = formatPercent(system.diskUsagePercent);
  elements.diskDetail.textContent = `${formatGigabytes(system.diskUsedBytes)} مستخدم من ${formatGigabytes(system.diskTotalBytes)}`;
  elements.diskRead.textContent = formatBytesPerSecond(system.diskReadBytesPerSecond);
  elements.diskWrite.textContent = formatBytesPerSecond(system.diskWriteBytesPerSecond);
  setResourceBar(elements.diskBar, system.diskUsagePercent);

  elements.networkRtt.textContent = `${new Intl.NumberFormat('en-US', { maximumFractionDigits: 1 }).format(roundTripMs)} ms`;
  const connection = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
  elements.networkDownlink.textContent = Number.isFinite(connection?.downlink)
    ? `${new Intl.NumberFormat('en-US', { maximumFractionDigits: 1 }).format(connection.downlink)} Mbps`
    : 'غير متاح';
  elements.networkType.textContent = connection?.effectiveType
    ? `شبكة ${connection.effectiveType.toUpperCase()}`
    : 'تقدير السرعة غير مدعوم في هذا المتصفح';

  elements.boardName.textContent = system.boardName || 'غير متاح من النظام';
  renderSensors(elements.temperatures, system.temperatures, 'غير متاحة في البيئة الافتراضية', (sensor) => `${sensor.value.toFixed(1)} °C`);
  renderSensors(elements.fans, system.fans, 'غير متاحة في البيئة الافتراضية', (sensor) => `${formatNumber(Math.round(sensor.value))} RPM`);
  renderSensors(elements.coolingDevices, system.coolingDevices, 'غير متاحة في البيئة الافتراضية', (sensor) => `${sensor.value.toFixed(0)}٪`);

  const temperatureValues = system.temperatures.map((sensor) => Number(sensor.value)).filter(Number.isFinite);
  if (temperatureValues.length) {
    const maximum = Math.max(...temperatureValues);
    elements.maxTemperature.textContent = `${maximum.toFixed(1)} °C`;
    elements.maxTemperature.classList.toggle('normal', maximum < 70);
    elements.coolingState.textContent = maximum >= 85 ? 'حرارة مرتفعة' : maximum >= 70 ? 'حرارة دافئة' : 'الحرارة ضمن الطبيعي';
  } else {
    elements.maxTemperature.textContent = 'غير متاح';
    elements.maxTemperature.classList.remove('normal');
    elements.coolingState.textContent = system.coolingDevices.length
      ? 'حساس الحرارة غير متاح، متحكم التبريد ظاهر أدناه'
      : 'لا تعرض البيئة الافتراضية حساسات الحرارة';
  }
}

function makeCell(text, className = '') {
  const cell = document.createElement('td');
  cell.textContent = text;
  if (className) cell.className = className;
  return cell;
}

function renderRequests() {
  const visibleRequests = state.filter === 'errors'
    ? state.requests.filter((request) => request.status >= 400)
    : state.requests;
  elements.rows.replaceChildren();

  if (visibleRequests.length === 0) {
    const row = document.createElement('tr');
    const empty = makeCell(state.filter === 'errors' ? 'لا توجد أخطاء في الطلبات الأخيرة.' : 'بانتظار أول طلب…', 'empty-cell');
    empty.colSpan = 5;
    row.append(empty);
    elements.rows.append(row);
    return;
  }

  for (const request of visibleRequests) {
    const row = document.createElement('tr');
    row.append(makeCell(request.path, 'path-cell'));

    const methodCell = document.createElement('td');
    const method = document.createElement('span');
    method.className = 'method-tag';
    method.textContent = request.method;
    methodCell.append(method);
    row.append(methodCell);

    const statusCell = document.createElement('td');
    const status = document.createElement('span');
    status.className = `status-tag${request.status >= 400 ? ' failed' : ''}`;
    status.textContent = `${request.status} · ${request.status >= 400 ? 'خطأ' : 'سليم'}`;
    statusCell.append(status);
    row.append(statusCell);

    row.append(makeCell(`${formatNumber(request.durationNanos)} ns`, 'duration-cell'));
    row.append(makeCell(new Date(request.timestamp).toLocaleTimeString('ar-EG')));
    elements.rows.append(row);
  }
}

function renderErrors(errors) {
  elements.errorCount.textContent = formatNumber(errors.length);
  elements.errorList.replaceChildren();
  if (errors.length === 0) {
    const empty = document.createElement('p');
    empty.className = 'empty-errors';
    empty.textContent = 'لا توجد أخطاء مسجلة.';
    elements.errorList.append(empty);
    return;
  }

  for (const error of errors) {
    const item = document.createElement('article');
    item.className = 'error-item';
    const icon = document.createElement('span');
    icon.className = 'error-icon';
    icon.textContent = '!';
    const copy = document.createElement('div');
    copy.className = 'error-copy';
    const message = document.createElement('strong');
    message.textContent = `${error.errorMessage || 'خطأ في الطلب'} · ${error.path}`;
    const detail = document.createElement('span');
    detail.textContent = `${error.method} · الحالة ${error.status} · ${formatNumber(error.durationNanos)} ns`;
    copy.append(message, detail);
    const time = document.createElement('time');
    time.className = 'error-time';
    time.dateTime = error.timestamp;
    time.textContent = new Date(error.timestamp).toLocaleTimeString('ar-EG');
    item.append(icon, copy, time);
    elements.errorList.append(item);
  }
}

function renderIdeIssues() {
  const visibleIssues = state.ideIssueFilter === 'all'
    ? state.ideIssues
    : state.ideIssues.filter((issue) => issue.source === state.ideIssueFilter);
  elements.ideIssueCount.textContent = formatNumber(visibleIssues.length);
  elements.ideIssueList.replaceChildren();
  if (visibleIssues.length === 0) {
    const empty = document.createElement('p');
    empty.className = 'empty-errors';
    empty.textContent = state.ideIssueFilter === 'all'
      ? 'لا توجد مشاكل مستلمة من مشاريع التطوير.'
      : 'لا توجد مشاكل لهذا المصدر.';
    elements.ideIssueList.append(empty);
    return;
  }

  const severityLabels = { error: 'خطأ', warning: 'تحذير', info: 'معلومة' };
  for (const issue of visibleIssues) {
    const item = document.createElement('article');
    item.className = 'ide-issue';
    item.dataset.severity = issue.severity;

    const top = document.createElement('div');
    top.className = 'ide-issue-top';
    const source = document.createElement('div');
    source.className = 'ide-issue-source';
    const sourceName = document.createElement('strong');
    sourceName.textContent = issue.source === 'xcode' ? 'Xcode' : 'Android Studio';
    const projectName = document.createElement('span');
    projectName.textContent = issue.project;
    source.append(sourceName, projectName);
    const severity = document.createElement('span');
    severity.className = `ide-severity ide-severity-${issue.severity}`;
    severity.textContent = severityLabels[issue.severity] || issue.severity;
    top.append(source, severity);

    const message = document.createElement('p');
    message.className = 'ide-issue-message';
    message.textContent = issue.code ? `[${issue.code}] ${issue.message}` : issue.message;

    const meta = document.createElement('div');
    meta.className = 'ide-issue-meta';
    const location = document.createElement('span');
    location.className = 'ide-issue-location';
    const locationCode = document.createElement('code');
    locationCode.textContent = issue.file
      ? `${issue.file}${issue.line ? `:${issue.line}` : ''}`
      : 'الموقع غير محدد';
    location.append(locationCode);
    const timestamp = document.createElement('time');
    timestamp.dateTime = issue.reportedAt;
    timestamp.textContent = new Date(issue.reportedAt).toLocaleString('ar-EG');
    meta.append(location, timestamp);
    item.append(top, message, meta);
    elements.ideIssueList.append(item);
  }
}

function renderDiscovery(discovery) {
  elements.discoveryRoot.textContent = discovery.root;
  elements.discoverySummary.textContent = `آخر فحص ${new Date(discovery.scannedAt).toLocaleTimeString('ar-EG')}`;
  elements.discoveredProjects.replaceChildren();
  if (discovery.projects.length === 0) {
    const empty = document.createElement('p');
    empty.className = 'empty-errors';
    empty.textContent = 'لم يتم العثور على مشاريع مدعومة في مساحة العمل.';
    elements.discoveredProjects.append(empty);
  } else {
    for (const project of discovery.projects) {
      const row = document.createElement('article');
      row.className = 'discovery-entry';
      const platform = document.createElement('span');
      platform.className = 'discovery-platform';
      platform.textContent = project.platform;
      const copy = document.createElement('div');
      copy.className = 'discovery-entry-copy';
      const name = document.createElement('strong');
      name.textContent = project.name;
      const path = document.createElement('span');
      path.textContent = project.path || '.';
      copy.append(name, path);
      const marker = document.createElement('span');
      marker.className = 'discovery-marker';
      marker.textContent = project.marker;
      row.append(platform, copy, marker);
      elements.discoveredProjects.append(row);
    }
  }

  elements.toolchainList.replaceChildren();
  for (const tool of discovery.toolchains) {
    const row = document.createElement('div');
    row.className = 'toolchain-row';
    const name = document.createElement('span');
    name.textContent = tool.name;
    const status = document.createElement('span');
    status.className = `toolchain-state${tool.available ? ' available' : ''}`;
    status.textContent = tool.available ? 'متاح' : 'غير موجود';
    row.append(name, status);
    elements.toolchainList.append(row);
  }
}

async function rescanWorkspace() {
  elements.discoveryRefresh.disabled = true;
  try {
    const discovery = await getJson('/api/monitor/discovery?refresh=true');
    state.discovery = discovery;
    renderDiscovery(discovery);
    showToast('اكتمل فحص مساحة العمل.');
  } catch {
    showToast('تعذر فحص مساحة العمل.');
  } finally {
    elements.discoveryRefresh.disabled = false;
  }
}

function alertForNewErrors(errors) {
  const currentIds = new Set(errors.map((error) => error.id));
  if (!state.errorBaselineReady) {
    state.knownErrorIds = currentIds;
    state.errorBaselineReady = true;
    return;
  }

  const newErrors = errors.filter((error) => !state.knownErrorIds.has(error.id));
  state.knownErrorIds = currentIds;
  if (newErrors.length === 0) return;

  const latestError = newErrors[0];
  const message = newErrors.length === 1
    ? `خطأ جديد: ${latestError.path}`
    : `تم تسجيل ${formatNumber(newErrors.length)} أخطاء جديدة`;
  showToast(message);

  if (state.browserNotificationsEnabled && 'Notification' in window && Notification.permission === 'granted') {
    new Notification('خطأ جديد في Spring Boot', {
      body: `${latestError.method} ${latestError.path} · HTTP ${latestError.status}`,
      tag: latestError.id
    });
  }
}

async function toggleBrowserNotifications() {
  if (!('Notification' in window)) {
    showToast('إشعارات المتصفح غير مدعومة هنا؛ سيظهر التنبيه داخل اللوحة.');
    return;
  }

  if (state.browserNotificationsEnabled) {
    state.browserNotificationsEnabled = false;
    elements.notificationToggle.textContent = 'تفعيل إشعارات الأخطاء';
    elements.notificationToggle.setAttribute('aria-pressed', 'false');
    showToast('تم إيقاف إشعارات المتصفح.');
    return;
  }

  const permission = Notification.permission === 'default'
    ? await Notification.requestPermission()
    : Notification.permission;
  if (permission !== 'granted') {
    showToast('لم تُمنح صلاحية إشعارات المتصفح.');
    return;
  }

  state.browserNotificationsEnabled = true;
  elements.notificationToggle.textContent = 'إشعارات الأخطاء مفعلة';
  elements.notificationToggle.setAttribute('aria-pressed', 'true');
  showToast('سيصلك إشعار عند تسجيل خطأ جديد.');
}

function drawChart(requests) {
  const canvas = elements.chart;
  const bounds = canvas.getBoundingClientRect();
  if (!bounds.width || !bounds.height) return;
  const ratio = window.devicePixelRatio || 1;
  canvas.width = Math.round(bounds.width * ratio);
  canvas.height = Math.round(bounds.height * ratio);
  const context = canvas.getContext('2d');
  context.setTransform(ratio, 0, 0, ratio, 0, 0);

  const width = bounds.width;
  const height = bounds.height;
  const inset = { top: 10, right: 5, bottom: 10, left: 5 };
  const chartWidth = width - inset.left - inset.right;
  const chartHeight = height - inset.top - inset.bottom;
  context.clearRect(0, 0, width, height);

  context.strokeStyle = '#edf0eb';
  context.lineWidth = 1;
  for (let line = 0; line < 4; line++) {
    const y = inset.top + (chartHeight * line) / 3;
    context.beginPath();
    context.moveTo(inset.left, y);
    context.lineTo(width - inset.right, y);
    context.stroke();
  }

  const values = requests.slice().reverse().map((request) => Number(request.durationNanos));
  elements.chartCount.textContent = values.length ? `${formatNumber(values.length)} طلب مرسوم` : 'لا توجد بيانات بعد';
  if (!values.length) return;

  const maximum = Math.max(...values, 1);
  const points = values.map((value, index) => ({
    x: inset.left + (values.length === 1 ? chartWidth / 2 : (chartWidth * index) / (values.length - 1)),
    y: inset.top + chartHeight - (value / maximum) * chartHeight
  }));

  context.beginPath();
  context.moveTo(points[0].x, height - inset.bottom);
  for (const point of points) context.lineTo(point.x, point.y);
  context.lineTo(points[points.length - 1].x, height - inset.bottom);
  context.closePath();
  context.fillStyle = 'rgba(199, 239, 120, 0.24)';
  context.fill();

  context.beginPath();
  points.forEach((point, index) => index === 0 ? context.moveTo(point.x, point.y) : context.lineTo(point.x, point.y));
  context.strokeStyle = '#65874b';
  context.lineWidth = 2;
  context.lineJoin = 'round';
  context.lineCap = 'round';
  context.stroke();

  if (points.length > 1) {
    const last = points[points.length - 1];
    context.beginPath();
    context.arc(last.x, last.y, 3.5, 0, Math.PI * 2);
    context.fillStyle = '#c95646';
    context.fill();
  }
}

async function refreshDashboard() {
  if (state.refreshing) return;
  state.refreshing = true;
  try {
    const telemetryRequest = (async () => {
      const startedAt = performance.now();
      const system = await getJson('/api/monitor/system');
      return { system, roundTripMs: performance.now() - startedAt };
    })();
    const [summary, requests, errors, ideIssues, discovery, health, telemetry] = await Promise.all([
      getJson('/api/monitor/summary'),
      getJson('/api/monitor/requests?limit=24'),
      getJson('/api/monitor/errors?limit=5'),
      getJson('/api/monitor/ide-issues?limit=100'),
      getJson('/api/monitor/discovery'),
      getJson('/actuator/health'),
      telemetryRequest
    ]);
    state.requests = requests;
    state.ideIssues = ideIssues;
    state.discovery = discovery;
    updateSummary(summary);
    renderRequests();
    renderErrors(errors);
    renderIdeIssues();
    renderDiscovery(discovery);
    alertForNewErrors(errors);
    drawChart(requests);
    updateSystem(telemetry.system, telemetry.roundTripMs);
    setConnection(health.status === 'UP', health.status === 'UP' ? 'النظام سليم' : 'الصحة تحتاج مراجعة');
  } catch {
    setConnection(false);
  } finally {
    state.refreshing = false;
  }
}

async function runAction(path, successMessage) {
  const buttons = document.querySelectorAll('.action-button');
  buttons.forEach((button) => { button.disabled = true; });
  try {
    const response = await fetch(path);
    if (path !== '/api/demo/error' && !response.ok) throw new Error(`HTTP ${response.status}`);
    showToast(successMessage);
  } catch {
    showToast('تعذر تنفيذ الطلب. تحقق من اتصال التطبيق.');
  } finally {
    buttons.forEach((button) => { button.disabled = false; });
    await refreshDashboard();
  }
}

document.querySelector('#ping-button').addEventListener('click', () => {
  runAction('/api/demo/ping', 'تم تسجيل طلب القياس.');
});
document.querySelector('#work-button').addEventListener('click', () => {
  runAction('/api/demo/work', 'اكتمل اختبار المعالجة وسُجل زمنه.');
});
document.querySelector('#error-button').addEventListener('click', () => {
  runAction('/api/demo/error', 'سُجل الخطأ التجريبي في سجل الأخطاء.');
});
elements.notificationToggle.addEventListener('click', toggleBrowserNotifications);
elements.discoveryRefresh.addEventListener('click', rescanWorkspace);

document.querySelectorAll('.filter-button').forEach((button) => {
  button.addEventListener('click', () => {
    state.filter = button.dataset.filter;
    document.querySelectorAll('.filter-button').forEach((filter) => filter.classList.toggle('selected', filter === button));
    renderRequests();
  });
});

document.querySelectorAll('.ide-filter-button').forEach((button) => {
  button.addEventListener('click', () => {
    state.ideIssueFilter = button.dataset.ideFilter;
    document.querySelectorAll('.ide-filter-button').forEach((filter) => {
      filter.classList.toggle('selected', filter === button);
    });
    renderIdeIssues();
  });
});

window.addEventListener('resize', () => drawChart(state.requests));

async function startDashboard() {
  try {
    await fetch('/api/demo/ping');
  } catch {
    setConnection(false);
  }
  await refreshDashboard();
  window.setInterval(refreshDashboard, 2500);
}

startDashboard();