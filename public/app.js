const $ = selector => document.querySelector(selector);

function escapeHtml(value) {
  return String(value == null ? '' : value).replace(
    /[&<>"']/g,
    character =>
      ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
      })[character]
  );
}

async function requestApi(url, options) {
  const response = await fetch(url, options);
  const data = await response.json();

  if (!response.ok) {
    throw new Error(data.error || '请求失败');
  }

  return data;
}

function formatDate(value) {
  return value
    ? new Date(value).toLocaleString('zh-CN', { hour12: false })
    : '-';
}

async function refreshStats() {
  const stats = await requestApi('/api/stats');

  $('#stat-tasks').textContent = stats.tasks;
  $('#stat-findings').textContent = stats.findings;
  $('#stat-open').textContent = stats.open;

  for (const level of ['critical', 'high', 'medium', 'low']) {
    $('#' + level).textContent = stats.severities[level];
  }
}

function renderTask(task) {
  const statusLabels = {
    completed: ['已完成', ''],
    processing: ['处理中', ''],
    failed: ['失败', 'fail']
  };
  const [statusText, statusClass] =
    statusLabels[task.status] || [task.status, ''];
  const project = task.projectId || '手动任务';
  const mergeRequest = task.iid ? ' · MR !' + escapeHtml(task.iid) : '';

  return (
    '<article class="task" data-id="' +
    escapeHtml(task.id) +
    '">' +
    '<div><h3>' +
    escapeHtml(task.title) +
    '</h3><div class="meta">' +
    escapeHtml(project) +
    mergeRequest +
    ' · ' +
    formatDate(task.createdAt) +
    '</div></div>' +
    '<div class="badges"><span class="badge ' +
    statusClass +
    '">' +
    statusText +
    '</span><span class="count">' +
    (task.findingCount || 0) +
    ' 个问题</span></div></article>'
  );
}

async function loadTasks() {
  const query = encodeURIComponent($('#search').value);
  const status = encodeURIComponent($('#status').value);
  const tasks = await requestApi('/api/tasks?q=' + query + '&status=' + status);

  $('#tasks').innerHTML = tasks.length
    ? tasks.map(renderTask).join('')
    : '<div class="empty">暂无评审任务。点击右上角开始一次评审。</div>';

  document.querySelectorAll('.task').forEach(taskElement => {
    taskElement.onclick = () => showTaskDetail(taskElement.dataset.id);
  });
}

function renderFinding(finding) {
  return (
    '<div class="finding"><h4><span class="risk ' +
    escapeHtml(finding.severity) +
    '">' +
    escapeHtml(finding.severity) +
    '</span>' +
    escapeHtml(finding.type) +
    ' <span class="meta">' +
    escapeHtml(finding.file) +
    ':' +
    escapeHtml(finding.line || '-') +
    '</span></h4><p>' +
    escapeHtml(finding.message) +
    '</p><p><b>建议：</b>' +
    escapeHtml(finding.suggestion) +
    '</p><p><button class="secondary feedback" data-id="' +
    escapeHtml(finding.id) +
    '" data-status="accepted">采纳</button> <button class="secondary feedback" data-id="' +
    escapeHtml(finding.id) +
    '" data-status="false_positive">误报</button> <button class="secondary feedback" data-id="' +
    escapeHtml(finding.id) +
    '" data-status="ignored">忽略</button></p></div>'
  );
}

async function showTaskDetail(taskId) {
  const task = await requestApi('/api/tasks/' + encodeURIComponent(taskId));

  $('#detail-title').textContent = task.title;

  let detailHtml =
    '<p class="meta">' +
    escapeHtml(task.status) +
    ' · 创建于 ' +
    formatDate(task.createdAt) +
    ' · ' +
    (task.diffChars || 0) +
    ' 字符 diff</p>';

  if (task.error) {
    detailHtml +=
      '<p class="finding"><b>任务错误：</b>' +
      escapeHtml(task.error) +
      '</p>';
  }

  if (task.llmError) {
    detailHtml +=
      '<p class="finding"><b>LLM 提示：</b>' +
      escapeHtml(task.llmError) +
      '</p>';
  }

  detailHtml += task.findings.length
    ? task.findings.map(renderFinding).join('')
    : '<div class="empty">未发现问题</div>';

  $('#detail').innerHTML = detailHtml;
  $('#detail-dialog').showModal();

  document.querySelectorAll('.feedback').forEach(button => {
    button.onclick = async () => {
      await requestApi(
        '/api/findings/' + button.dataset.id + '/feedback',
        {
          method: 'POST',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify({ status: button.dataset.status })
        }
      );

      await showTaskDetail(taskId);
      await refreshStats();
    };
  });
}

$('#new-review').onclick = () => $('#review-dialog').showModal();
$('#detail-close').onclick = () => $('#detail-dialog').close();
$('#search').oninput = loadTasks;
$('#status').onchange = loadTasks;

$('#review-form').onsubmit = async event => {
  event.preventDefault();

  const form = new FormData(event.target);
  const data = Object.fromEntries(form.entries());
  data.postComment = form.get('postComment') === 'on';

  try {
    await requestApi('/api/reviews', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(data)
    });

    $('#review-dialog').close();
    event.target.reset();
    await refreshStats();
    await loadTasks();
  } catch (error) {
    alert(error.message);
  }
};

async function bootstrap() {
  try {
    const health = await requestApi('/api/health');

    $('#health-dot').style.background = '#41a56d';
    $('#health').textContent = '服务运行正常';
    $('#health-time').textContent = formatDate(health.time);

    await refreshStats();
    await loadTasks();
  } catch (error) {
    $('#health').textContent = '服务不可用';
    $('#health-time').textContent = error.message;
  }
}

bootstrap();
