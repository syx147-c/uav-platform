<script setup>
/**
 * 任务管理页面 — 飞行任务的 CRUD 表格
 */
import { ref, onMounted } from 'vue';                         // Vue 3 API

// === 任务列表数据 ===
const missions = ref([]);                                      // 所有任务记录
const loading = ref(false);                                    // 表格加载状态
const dialogVisible = ref(false);                              // 新建/编辑弹窗是否可见
const isEditing = ref(false);                                  // 当前是否是编辑模式

// === 表单数据 ===
const formData = ref({ title: '', description: '', state: 'CREATED' });

// === 任务状态选项 ===
const stateOptions = [
  { label: '已创建',  value: 'CREATED' },
  { label: '执行中',  value: 'EXECUTING' },
  { label: '已暂停',  value: 'PAUSED' },
  { label: '已完成',  value: 'COMPLETED' },
  { label: '已失败',  value: 'FAILED' }
];

/**
 * 从后端加载所有任务
 */
async function loadMissions() {
  loading.value = true;
  try {
    const res = await fetch('/api/drone/missions');
    missions.value = await res.json();
  } catch (e) {
    // 后端 API 尚未实现则用模拟数据
    missions.value = [
      { id: 1, title: '测试任务1', description: '飞到坐标(30.5, 120.3)', state: 'COMPLETED',
        createdAt: '2026-05-11 10:00', updatedAt: '2026-05-11 10:05' },
      { id: 2, title: '悬停测试', description: '起飞后悬停30秒', state: 'CREATED',
        createdAt: '2026-05-11 12:00', updatedAt: '2026-05-11 12:00' }
    ];
  } finally {
    loading.value = false;
  }
}

/**
 * 打开新建任务弹窗
 */
function openCreate() {
  isEditing.value = false;
  formData.value = { title: '', description: '', state: 'CREATED' };
  dialogVisible.value = true;
}

/**
 * 打开编辑任务弹窗
 */
function openEdit(row) {
  isEditing.value = true;
  formData.value = { ...row };
  dialogVisible.value = true;
}

/**
 * 保存任务（新建或更新）
 */
async function saveMission() {
  try {
    if (isEditing.value) {
      await fetch(`/api/drone/missions/${formData.value.id}`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData.value)
      });
    } else {
      await fetch('/api/drone/missions', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData.value)
      });
    }
    dialogVisible.value = false;
    loadMissions();
  } catch (e) {
    // 后端 API 未实现时，直接更新本地数据
    if (isEditing.value) {
      const idx = missions.value.findIndex(m => m.id === formData.value.id);
      if (idx > -1) missions.value[idx] = { ...formData.value };
    } else {
      formData.value.id = Date.now();
      missions.value.push({ ...formData.value, createdAt: new Date().toLocaleString(), updatedAt: new Date().toLocaleString() });
    }
    dialogVisible.value = false;
  }
}

/**
 * 删除任务
 */
async function deleteMission(row) {
  try {
    await fetch(`/api/drone/missions/${row.id}`, { method: 'DELETE' });
  } catch (e) {}
  missions.value = missions.value.filter(m => m.id !== row.id);
}

// === 页面加载时拉取数据 ===
onMounted(loadMissions);

/**
 * 格式化状态显示
 */
function stateTagType(state) {
  const map = { CREATED: 'info', EXECUTING: 'warning', PAUSED: '', COMPLETED: 'success', FAILED: 'danger' };
  return map[state] || 'info';
}
function stateLabel(state) {
  return stateOptions.find(s => s.value === state)?.label || state;
}
</script>

<template>
  <div class="mission-page">
    <!-- 工具栏 -->
    <div class="toolbar">
      <h2>任务管理</h2>
      <el-button type="primary" @click="openCreate">+ 新建任务</el-button>
    </div>

    <!-- 任务表格 -->
    <el-card shadow="hover">
      <el-table :data="missions" v-loading="loading" stripe border style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" width="180" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="state" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="stateTagType(row.state)" size="small">{{ stateLabel(row.state) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column prop="updatedAt" label="更新时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除？" @confirm="deleteMission(row)">
              <template #reference>
                <el-button size="small" type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEditing ? '编辑任务' : '新建任务'" width="500px">
      <el-form :model="formData" label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="formData.title" placeholder="输入任务标题" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" rows="3" placeholder="输入自然语言任务描述" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="formData.state">
            <el-option v-for="s in stateOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveMission">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.mission-page { display: flex; flex-direction: column; gap: 16px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.toolbar h2 { font-size: 18px; color: #333; }
</style>
