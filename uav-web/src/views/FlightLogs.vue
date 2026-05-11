<script setup>
/**
 * 飞行日志页面 — 展示每次飞行的关键事件记录
 */
import { ref, onMounted, computed } from 'vue';            // Vue 3 API

// === 日志列表 ===
const logs = ref([]);                                    // 日志记录数组
const loading = ref(false);                              // 表格加载状态
const filterEvent = ref('');                             // 事件类型筛选

// === 事件类型映射 ===
const eventMap = {
  ARM:     { label: '解锁',   color: '#909399' },
  TAKEOFF: { label: '起飞',   color: '#409EFF' },
  WAYPOINT:{ label: '航点',   color: '#67C23A' },
  HOVER:   { label: '悬停',   color: '#E6A23C' },
  RTL:     { label: '返航',   color: '#F56C6C' },
  LAND:    { label: '降落',   color: '#67C23A' },
  HOLD:    { label: '急停',   color: '#F56C6C' }
};

/**
 * 从后端加载日志
 */
async function loadLogs() {
  loading.value = true;
  try {
    const res = await fetch('/api/drone/logs');
    logs.value = await res.json();
  } catch (e) {
    // 后端 API 未实现则用模拟数据
    logs.value = [
      { id: 1, missionId: 1, eventType: 'ARM', source: 'MANUAL',
        eventData: JSON.stringify({ lat: 47.397, lon: 8.545 }), createdAt: '2026-05-11 14:30:01' },
      { id: 2, missionId: 1, eventType: 'TAKEOFF', source: 'AGENT',
        eventData: JSON.stringify({ altitude: 5 }), createdAt: '2026-05-11 14:30:10' },
      { id: 3, missionId: 1, eventType: 'HOVER', source: 'AGENT',
        eventData: JSON.stringify({ duration: 30 }), createdAt: '2026-05-11 14:30:45' },
      { id: 4, missionId: 1, eventType: 'RTL', source: 'AGENT',
        eventData: JSON.stringify({}), createdAt: '2026-05-11 14:31:15' },
      { id: 5, missionId: 1, eventType: 'LAND', source: 'AGENT',
        eventData: JSON.stringify({}), createdAt: '2026-05-11 14:31:30' },
      { id: 6, missionId: 2, eventType: 'HOLD', source: 'EMERGENCY',
        eventData: JSON.stringify({ reason: '低电量保护' }), createdAt: '2026-05-11 15:00:00' }
    ];
  } finally {
    loading.value = false;
  }
}

// === 页面初始化 ===
onMounted(loadLogs);

/**
 * 获取事件标签和颜色
 */
function eventLabel(type) { return eventMap[type]?.label || type; }
function eventColor(type) { return eventMap[type]?.color || '#909399'; }

/**
 * 按类型筛选日志
 */
const filteredLogs = computed(() => {
  if (!filterEvent.value) return logs.value;
  return logs.value.filter(l => l.eventType === filterEvent.value);
});
</script>

<template>
  <div class="log-page">
    <!-- 工具栏 -->
    <div class="toolbar">
      <h2>飞行日志</h2>
      <div style="display: flex; gap: 12px;">
        <el-select v-model="filterEvent" placeholder="筛选事件类型" clearable size="small" style="width: 140px">
          <el-option v-for="(info, key) in eventMap" :key="key" :label="info.label" :value="key" />
        </el-select>
        <el-button size="small" @click="loadLogs">刷新</el-button>
      </div>
    </div>

    <!-- 日志表格 -->
    <el-card shadow="hover">
      <el-table :data="filteredLogs" v-loading="loading" stripe border style="width: 100%" max-height="520">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="missionId" label="任务ID" width="80" />
        <el-table-column prop="eventType" label="事件类型" width="90">
          <template #default="{ row }">
            <el-tag :color="eventColor(row.eventType)" effect="dark" size="small" style="border:none;color:#fff">
              {{ eventLabel(row.eventType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="eventData" label="事件详情" min-width="200" show-overflow-tooltip />
        <el-table-column prop="source" label="来源" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.source === 'EMERGENCY' ? 'danger' : row.source === 'MANUAL' ? '' : 'success'">
              {{ row.source === 'AGENT' ? '智能体' : row.source === 'MANUAL' ? '手动' : '紧急' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="170" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.log-page { display: flex; flex-direction: column; gap: 16px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.toolbar h2 { font-size: 18px; color: #333; }
</style>
