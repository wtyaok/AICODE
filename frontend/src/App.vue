<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { api } from './api';
import RulesPanel from './RulesPanel.vue';

const loading = ref(true);
const activeTab = ref('tasks');
const adminKey = ref(localStorage.getItem('admin-key') || '');
const error = ref('');
const tasks = ref([]);
const selectedTask = ref(null);
const severityFilter = ref('');
const showCreate = ref(false);
const createLoading = ref(false);
const filters = reactive({ q: '', status: '', page: 0, size: 20 });
const pageInfo = ref({ totalPages: 0, number: 0 });
const stats = ref({
  tasks: 0,
  findings: 0,
  open: 0,
  severities: { critical: 0, high: 0, medium: 0, low: 0 }
});
const form = reactive({
  title: '手动代码评审',
  projectId: '',
  mrIid: '',
  diff: '',
  postComment: false
});
const statusText = {
  queued: '排队中',
  processing: '处理中',
  completed: '已完成',
  failed: '失败',
  skipped: '已跳过'
};
const severityText = {
  critical: '严重',
  high: '高',
  medium: '中',
  low: '低'
};
const hasPrevious = computed(() => filters.page > 0);
const hasNext = computed(
  () => filters.page + 1 < pageInfo.value.totalPages
);

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const [statsResponse, tasksResponse] = await Promise.all([
      api.stats(),
      api.tasks(filters)
    ]);
    stats.value = statsResponse.data;
    tasks.value = tasksResponse.data.content;
    pageInfo.value = tasksResponse.data;
  } catch (reason) {
    error.value = reason.response?.data?.error || reason.message;
  } finally {
    loading.value = false;
  }
}

async function openTask(id) {
  try {
    selectedTask.value = (await api.task(id)).data;
  } catch (reason) {
    error.value = reason.response?.data?.error || reason.message;
  }
}

function saveKey() {
  localStorage.setItem('admin-key', adminKey.value);
  load();
}

const visibleFindings = computed(() => {
  const findings = selectedTask.value?.findings || [];
  return severityFilter.value
    ? findings.filter(item => item.severity === severityFilter.value)
    : findings;
});

async function submitFeedback(finding, status) {
  await api.feedback(finding.id, { status });
  await openTask(selectedTask.value.task.id);
  await load();
}

async function createReview() {
  createLoading.value = true;
  try {
    await api.createReview(
      {
        ...form,
        mrIid: form.mrIid ? Number(form.mrIid) : null
      },
      crypto.randomUUID()
    );
    showCreate.value = false;
    Object.assign(form, {
      title: '手动代码评审',
      projectId: '',
      mrIid: '',
      diff: '',
      postComment: false
    });
    await load();
  } catch (reason) {
    error.value = reason.response?.data?.error || reason.message;
  } finally {
    createLoading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="brand">
        <span class="brand-mark">CR</span>
        <div>
          <strong>代码评审助手</strong>
          <small>AI R&amp;D QUALITY ASSISTANT</small>
        </div>
      </div>
      <div class="top-actions">
        <input
          v-model="adminKey"
          class="key-input"
          placeholder="Admin Key"
          type="password"
          @change="saveKey"
        />
        <button class="primary-button" @click="showCreate = true">新建评审</button>
      </div>
    </header>

    <main class="content">
      <section class="intro">
        <div>
          <p class="eyebrow">ENGINEERING QUALITY</p>
          <h1>让每一次代码变更<br /><em>都可追踪、可改进</em></h1>
          <p class="intro-copy">
            连接 GitLab Merge Request，结合确定性规则与智能分析，及时发现风险并沉淀反馈。
          </p>
        </div>
        <div class="service-state">
          <span class="state-dot"></span>
          <strong>Spring Boot API</strong>
          <small>MySQL · Redis · Python Analyzer</small>
        </div>
      </section>

      <p v-if="error" class="error-banner">{{ error }}</p>

      <section class="stats-grid">
        <article class="stat-card">
          <span>评审任务</span>
          <strong>{{ stats.tasks }}</strong>
          <small>全部任务</small>
        </article>
        <article class="stat-card">
          <span>发现问题</span>
          <strong>{{ stats.findings }}</strong>
          <small>规则 + AI</small>
        </article>
        <article class="stat-card">
          <span>待处理</span>
          <strong>{{ stats.open }}</strong>
          <small>等待反馈</small>
        </article>
        <article class="stat-card">
          <span>风险分布</span>
          <div class="risk-pills">
            <i
              v-for="(count, level) in stats.severities"
              :key="level"
              :class="['risk-pill', level]"
            >
              {{ severityText[level] }} {{ count }}
            </i>
          </div>
          <small>按严重等级统计</small>
        </article>
      </section>

      <nav class="view-tabs" aria-label="管理视图">
        <button
          :class="{ active: activeTab === 'tasks' }"
          @click="activeTab = 'tasks'"
        >
          评审任务
        </button>
        <button
          :class="{ active: activeTab === 'rules' }"
          @click="activeTab = 'rules'"
        >
          规则库
        </button>
      </nav>

      <section v-if="activeTab === 'tasks'" class="queue">
        <div class="section-heading">
          <div>
            <p class="eyebrow">REVIEW QUEUE</p>
            <h2>评审任务</h2>
          </div>
          <div class="filters">
            <input
              v-model="filters.q"
              placeholder="搜索项目或任务"
              @keyup.enter="filters.page = 0; load()"
            />
            <select
              v-model="filters.status"
              @change="filters.page = 0; load()"
            >
              <option value="">全部状态</option>
              <option value="queued">排队中</option>
              <option value="processing">处理中</option>
              <option value="completed">已完成</option>
              <option value="failed">失败</option>
            </select>
            <button class="secondary-button" @click="load">刷新</button>
          </div>
        </div>

        <div v-if="loading" class="empty-state">正在读取任务...</div>
        <div v-else-if="!tasks.length" class="empty-state">暂无评审任务</div>
        <div v-else class="task-list">
          <button
            v-for="task in tasks"
            :key="task.id"
            class="task-row"
            @click="openTask(task.id)"
          >
            <span class="task-main">
              <strong>{{ task.title }}</strong>
              <small>
                {{ task.projectId || '手动任务' }}
                <template v-if="task.mrIid"> · MR !{{ task.mrIid }}</template>
                · {{ new Date(task.createdAt).toLocaleString() }}
              </small>
            </span>
            <span class="task-meta">
              <b :class="['status', task.status]">
                {{ statusText[task.status] || task.status }}
              </b>
              <em>{{ task.findingCount }} 个问题</em>
            </span>
          </button>
        </div>

        <div class="pagination">
          <button :disabled="!hasPrevious" @click="filters.page--; load()">
            上一页
          </button>
          <span>
            第 {{ pageInfo.number + 1 }} 页 / 共 {{ pageInfo.totalPages || 1 }} 页
          </span>
          <button :disabled="!hasNext" @click="filters.page++; load()">
            下一页
          </button>
        </div>
      </section>
      <RulesPanel v-else />
    </main>

    <div
      v-if="showCreate"
      class="modal-backdrop"
      @click.self="showCreate = false"
    >
      <form class="modal" @submit.prevent="createReview">
        <div class="modal-heading">
          <h2>新建评审任务</h2>
          <button
            type="button"
            class="icon-button"
            @click="showCreate = false"
          >
            ×
          </button>
        </div>
        <label>
          任务标题
          <input v-model="form.title" required />
        </label>
        <label>
          GitLab 项目 ID 或路径
          <input v-model="form.projectId" placeholder="group/project 或数字 ID" />
        </label>
        <label>
          Merge Request IID
          <input
            v-model="form.mrIid"
            type="number"
            placeholder="配置 GitLab 后可使用"
          />
        </label>
        <label>
          Git diff
          <textarea
            v-model="form.diff"
            placeholder="如填写项目 ID 和 MR IID，可不填写"
          ></textarea>
        </label>
        <label class="checkbox">
          <input v-model="form.postComment" type="checkbox" />
          评审完成后回写 GitLab 评论
        </label>
        <div class="modal-actions">
          <button
            type="button"
            class="secondary-button"
            @click="showCreate = false"
          >
            取消
          </button>
          <button class="primary-button" :disabled="createLoading">
            {{ createLoading ? '提交中...' : '创建评审' }}
          </button>
        </div>
      </form>
    </div>

    <div
      v-if="selectedTask"
      class="modal-backdrop"
      @click.self="selectedTask = null"
    >
      <section class="modal detail-modal">
        <div class="modal-heading">
          <h2>{{ selectedTask.task.title }}</h2>
          <button class="icon-button" @click="selectedTask = null">×</button>
        </div>
        <p class="detail-meta">
          {{ statusText[selectedTask.task.status] || selectedTask.task.status }}
          · {{ selectedTask.task.commentStatus }}
          · {{ selectedTask.findings.length }} 个问题
        </p>
        <p v-if="selectedTask.task.errorMessage" class="error-banner">
          {{ selectedTask.task.errorMessage }}
        </p>
        <p v-if="selectedTask.task.llmError" class="error-banner">
          LLM 分析失败：{{ selectedTask.task.llmError }}
        </p>
        <div class="filters detail-filters">
          <select v-model="severityFilter">
            <option value="">全部等级</option>
            <option value="critical">严重</option>
            <option value="high">高</option>
            <option value="medium">中</option>
            <option value="low">低</option>
          </select>
        </div>
        <div v-if="!selectedTask.findings.length" class="empty-state">
          未发现问题
        </div>
        <article
          v-for="finding in visibleFindings"
          :key="finding.id"
          class="finding"
        >
          <div class="finding-title">
            <span :class="['risk-pill', finding.severity]">
              {{ severityText[finding.severity] }}
            </span>
            <strong>{{ finding.category }}</strong>
            <code>{{ finding.filePath }}:{{ finding.lineNumber || '-' }}</code>
          </div>
          <p>{{ finding.message }}</p>
          <small>建议：{{ finding.suggestion }}</small>
          <div class="finding-actions">
            <span class="feedback-state">
              {{ finding.feedbackStatus === 'open' ? '待处理' : finding.feedbackStatus }}
            </span>
            <button @click="submitFeedback(finding, 'accepted')">采纳</button>
            <button @click="submitFeedback(finding, 'false_positive')">
              误报
            </button>
            <button @click="submitFeedback(finding, 'ignored')">忽略</button>
          </div>
        </article>
        <div v-if="selectedTask.feedback.length" class="feedback-history">
          <h3>反馈记录</h3>
          <p v-for="record in selectedTask.feedback" :key="record.id">
            #{{ record.findingId }} · {{ record.status }} ·
            {{ new Date(record.createdAt).toLocaleString() }}
          </p>
        </div>
      </section>
    </div>
  </div>
</template>
