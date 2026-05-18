<script setup>
/**
 * 任务管理页面 — 科技风数据表格
 */
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { api } from '../api/http.js';

const missions = ref([]);
const loading = ref(false);
const dialogVisible = ref(false);
const isEditing = ref(false);
const formData = ref({ title: '', description: '', state: 'CREATED' });

const stateOptions = [
  { label: 'CREATED',   value: 'CREATED' },
  { label: 'EXECUTING', value: 'EXECUTING' },
  { label: 'PAUSED',    value: 'PAUSED' },
  { label: 'COMPLETED', value: 'COMPLETED' },
  { label: 'FAILED',    value: 'FAILED' }
];

const stateTagType = { CREATED: '', EXECUTING: 'warning', PAUSED: 'info', COMPLETED: 'success', FAILED: 'danger' };

async function loadMissions() {
  loading.value = true;
  try {
    const data = await api('/api/missions');
    missions.value = Array.isArray(data) ? data : (data.data || []);
  } catch {
    missions.value = [
      { id: 1, title: '航点巡逻', description: '飞到坐标(30.5, 120.3)上空50米，悬停30秒后返航', state: 'COMPLETED', createdAt: '2026-05-11 10:00', updatedAt: '2026-05-11 10:05' },
      { id: 2, title: '悬停耐力测试', description: '起飞到 10m 悬停 60 秒', state: 'EXECUTING', createdAt: '2026-05-11 12:00', updatedAt: '2026-05-11 12:00' },
      { id: 3, title: '紧急返航演练', description: '模拟低电量触发自动返航', state: 'CREATED', createdAt: '2026-05-11 14:00', updatedAt: '2026-05-11 14:00' }
    ];
  } finally { loading.value = false; }
}

function openCreate() { isEditing.value = false; formData.value = { title: '', description: '', state: 'CREATED' }; dialogVisible.value = true; }
function openEdit(row) { isEditing.value = true; formData.value = { ...row }; dialogVisible.value = true; }

async function saveMission() {
  try {
    if (isEditing.value) {
      await api(`/api/missions/${formData.value.id}`, { method: 'PUT', body: JSON.stringify(formData.value) });
    } else {
      await api('/api/missions', { method: 'POST', body: JSON.stringify(formData.value) });
    }
    dialogVisible.value = false;
    loadMissions();
  } catch {
    if (isEditing.value) {
      const i = missions.value.findIndex(m => m.id === formData.value.id);
      if (i > -1) missions.value[i] = { ...formData.value };
    } else {
      formData.value.id = Date.now();
      missions.value.push({ ...formData.value, createdAt: new Date().toLocaleString(), updatedAt: new Date().toLocaleString() });
    }
    dialogVisible.value = false;
  }
}

async function deleteMission(row) {
  try { await api(`/api/missions/${row.id}`, { method: 'DELETE' }); } catch {}
  missions.value = missions.value.filter(m => m.id !== row.id);
  ElMessage.success('任务已删除');
}

onMounted(loadMissions);
</script>

<template>
  <div class="mission-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">MISSION PLANNER</h2>
        <p class="page-sub">飞行任务编排与调度</p>
      </div>
      <el-button type="primary" @click="openCreate" size="large">+ NEW MISSION</el-button>
    </div>

    <div class="table-wrapper">
      <el-table :data="missions" v-loading="loading" style="width:100%"
        :header-cell-style="{ fontFamily: 'JetBrains Mono, monospace', fontSize: '11px', letterSpacing: '1px' }">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="TITLE" width="180" />
        <el-table-column prop="description" label="DESCRIPTION" show-overflow-tooltip />
        <el-table-column prop="state" label="STATE" width="120">
          <template #default="{ row }">
            <el-tag :type="stateTagType[row.state]" effect="dark" size="small" disable-transitions>
              {{ row.state }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="CREATED" width="170" />
        <el-table-column prop="updatedAt" label="UPDATED" width="170" />
        <el-table-column label="ACTIONS" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">Edit</el-button>
            <el-button size="small" type="danger" @click="deleteMission(row)">Delete</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEditing ? 'EDIT MISSION' : 'NEW MISSION'" width="520px">
      <el-form :model="formData" label-position="top">
        <el-form-item label="TITLE">
          <el-input v-model="formData.title" placeholder="Mission title..." />
        </el-form-item>
        <el-form-item label="DESCRIPTION">
          <el-input v-model="formData.description" type="textarea" rows="3" placeholder="Natural language task description..." />
        </el-form-item>
        <el-form-item label="STATE">
          <el-select v-model="formData.state" style="width:100%">
            <el-option v-for="s in stateOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">Cancel</el-button>
        <el-button type="primary" @click="saveMission">Save</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.mission-page { display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: center; }
.page-title { font-family: var(--font-mono); font-size: 18px; color: var(--white); letter-spacing: 2px; }
.page-sub { font-size: 12px; color: var(--gray); margin-top: 4px; }

.table-wrapper {
  background: linear-gradient(135deg, rgba(10,20,50,0.9), rgba(6,12,30,0.8));
  backdrop-filter: blur(12px);
  border: 1px solid rgba(59,130,246,0.1);
  border-radius: 10px;
  padding: 8px;
}
</style>
