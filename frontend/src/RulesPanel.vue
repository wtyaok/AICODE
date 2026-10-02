<script setup>
import { onMounted, reactive, ref } from 'vue';
import { api } from './api';

const rules = ref([]);
const error = ref('');
const saving = ref(false);
const editId = ref('');
const form = reactive({
  name: '',
  severity: 'medium',
  pattern: '',
  suggestion: '',
  enabled: true
});

async function loadRules() {
  try {
    rules.value = (await api.rules()).data;
    error.value = '';
  } catch (reason) {
    error.value = reason.response?.data?.error || reason.message;
  }
}

function edit(rule) {
  editId.value = rule.id;
  Object.assign(form, rule);
}

function newRule() {
  editId.value = '';
  Object.assign(form, {
    name: '',
    severity: 'medium',
    pattern: '',
    suggestion: '',
    enabled: true
  });
}

async function save() {
  saving.value = true;
  try {
    const id = editId.value || prompt('规则 ID：小写字母、数字和连字符');
    if (!id) return;
    await api.saveRule(id, form);
    newRule();
    await loadRules();
  } catch (reason) {
    error.value = reason.response?.data?.error || reason.message;
  } finally {
    saving.value = false;
  }
}

onMounted(loadRules);
</script>

<template>
  <section class="rules-view">
    <div class="section-heading">
      <div>
        <p class="eyebrow">RULE LIBRARY</p>
        <h2>规则库</h2>
      </div>
      <button class="secondary-button" @click="loadRules">刷新</button>
    </div>
    <p v-if="error" class="error-banner">{{ error }}</p>
    <div class="rules-layout">
      <div class="rule-list">
        <button
          v-for="rule in rules"
          :key="rule.id"
          class="rule-row"
          @click="edit(rule)"
        >
          <strong>{{ rule.name }}</strong>
          <span>{{ rule.id }} · {{ rule.severity }} · {{ rule.enabled ? '启用' : '停用' }}</span>
        </button>
      </div>
      <form class="rule-editor" @submit.prevent="save">
        <h3>{{ editId ? '编辑规则：' + editId : '新增规则' }}</h3>
        <label>名称<input v-model="form.name" required /></label>
        <label>
          严重等级
          <select v-model="form.severity">
            <option value="critical">严重</option>
            <option value="high">高</option>
            <option value="medium">中</option>
            <option value="low">低</option>
          </select>
        </label>
        <label>正则表达式<input v-model="form.pattern" required /></label>
        <label>修改建议<textarea v-model="form.suggestion" required></textarea></label>
        <label class="checkbox"><input v-model="form.enabled" type="checkbox" />启用</label>
        <div class="modal-actions">
          <button type="button" class="secondary-button" @click="newRule">重置</button>
          <button class="primary-button" :disabled="saving">保存规则</button>
        </div>
      </form>
    </div>
  </section>
</template>
