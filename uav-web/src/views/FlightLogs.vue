<script setup>
/**
 * 飞行日志页面 — 终端风格事件记录器
 */
import { ref, onMounted, computed } from 'vue';
import { api } from '../api/http.js';

const logs = ref([]);
const loading = ref(false);
const filterEvent = ref('');

const eventMap = {
  ARM:      { label: 'ARM',      color: '#64748b', icon: '⚡' },
  TAKEOFF:  { label: 'TAKEOFF',  color: '#3b82f6', icon: '▲' },
  WAYPOINT: { label: 'WAYPOINT', color: '#10b981', icon: '◇' },
  HOVER:    { label: 'HOVER',    color: '#f59e0b', icon: '◈' },
  RTL:      { label: 'RTL',      color: '#6366f1', icon: '↺' },
  LAND:     { label: 'LAND',     color: '#10b981', icon: '▼' },
  HOLD:     { label: 'HOLD',     color: '#f43f5e', icon: '■' }
};

const sourceLabel = { AGENT: 'AI', MANUAL: 'MAN', EMERGENCY: 'EMG' };
const sourceColor = { AGENT: '#3b82f6', MANUAL: '#64748b', EMERGENCY: '#f43f5e' };

async function loadLogs() {
  loading.value = true;
  try {
    const data = await api('/api/flight-logs');
    logs.value = Array.isArray(data) ? data : (data.data || []);
  } catch {
    logs.value = [
      { id: 1, missionId: 1, eventType: 'ARM', source: 'MANUAL',
        eventData: '{"lat":47.397,"lon":8.545}', createdAt: '2026-05-11 14:30:01' },
      { id: 2, missionId: 1, eventType: 'TAKEOFF', source: 'AGENT',
        eventData: '{"altitude":5}', createdAt: '2026-05-11 14:30:10' },
      { id: 3, missionId: 1, eventType: 'WAYPOINT', source: 'AGENT',
        eventData: '{"lat":47.398,"lon":8.546,"alt":10}', createdAt: '2026-05-11 14:30:25' },
      { id: 4, missionId: 1, eventType: 'HOVER', source: 'AGENT',
        eventData: '{"duration":30}', createdAt: '2026-05-11 14:30:45' },
      { id: 5, missionId: 1, eventType: 'RTL', source: 'AGENT',
        eventData: '{}', createdAt: '2026-05-11 14:31:15' },
      { id: 6, missionId: 1, eventType: 'LAND', source: 'AGENT',
        eventData: '{}', createdAt: '2026-05-11 14:31:30' },
      { id: 7, missionId: 2, eventType: 'HOLD', source: 'EMERGENCY',
        eventData: '{"reason":"battery<20%"}', createdAt: '2026-05-11 15:00:00' }
    ];
  } finally { loading.value = false; }
}

onMounted(loadLogs);

const filteredLogs = computed(() =>
  filterEvent.value ? logs.value.filter(l => l.eventType === filterEvent.value) : logs.value
);
</script>

<template>
  <div class="log-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">FLIGHT DATA RECORDER</h2>
        <p class="page-sub">飞行事件日志</p>
      </div>
      <div class="header-actions">
        <el-select v-model="filterEvent" placeholder="FILTER EVENT" clearable size="small" style="width:160px">
          <el-option v-for="(v, k) in eventMap" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-button size="small" @click="loadLogs" :loading="loading">⟳ REFRESH</el-button>
      </div>
    </div>

    <!-- 日志列表（终端风格） -->
    <div class="log-terminal">
      <div class="terminal-header">
        <span class="term-dot r"></span>
        <span class="term-dot y"></span>
        <span class="term-dot g"></span>
        <span class="term-title">UAV_FLIGHT_LOG — RECORDER v1.0</span>
      </div>
      <div class="log-list">
        <div v-if="filteredLogs.length === 0" class="log-empty">NO DATA</div>
        <div v-for="log in filteredLogs" :key="log.id" class="log-row">
          <span class="log-time">{{ log.createdAt }}</span>
          <span class="log-tag" :style="{ color: eventMap[log.eventType]?.color }">
            {{ eventMap[log.eventType]?.icon }} {{ eventMap[log.eventType]?.label || log.eventType }}
          </span>
          <span class="log-source" :style="{ color: sourceColor[log.source] }">
            {{ sourceLabel[log.source] || log.source }}
          </span>
          <span class="log-data">{{ log.eventData }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.log-page { display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: center; }
.page-title { font-family: var(--font-mono); font-size: 18px; color: var(--white); letter-spacing: 2px; }
.page-sub { font-size: 12px; color: var(--gray); margin-top: 4px; }
.header-actions { display: flex; gap: 8px; }

/* ===== 终端风格日志面板 ===== */
.log-terminal {
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid rgba(59,130,246,0.12);
  border-radius: 10px;
  overflow: hidden;
}

.terminal-header {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 16px;
  background: rgba(0,0,0,0.05);
  border-bottom: 1px solid rgba(59,130,246,0.08);
}
.term-dot {
  width: 10px; height: 10px; border-radius: 50%;
}
.term-dot.r { background: #f43f5e; }
.term-dot.y { background: #f59e0b; }
.term-dot.g { background: #10b981; }
.term-title {
  font-family: var(--font-mono); font-size: 10px;
  color: var(--gray); margin-left: 8px; letter-spacing: 1px;
}

/* 日志列表 */
.log-list {
  max-height: 500px;
  overflow-y: auto;
  padding: 4px 0;
  font-family: var(--font-mono);
}
.log-empty {
  text-align: center; padding: 40px;
  color: var(--gray); font-size: 14px; letter-spacing: 2px;
}

.log-row {
  display: flex; align-items: center; gap: 12px;
  padding: 8px 16px;
  border-bottom: 1px solid rgba(59,130,246,0.05);
  font-size: 11px;
  transition: background 0.15s;
}
.log-row:hover { background: rgba(59,130,246,0.05); }

.log-time { color: var(--gray); min-width: 160px; }
.log-tag {
  min-width: 100px; font-weight: 700; letter-spacing: 1px;
}
.log-source {
  min-width: 40px;
  padding: 2px 6px;
  border: 1px solid currentColor;
  border-radius: 3px;
  font-size: 9px;
  text-align: center;
  opacity: 0.8;
}
.log-data {
  flex: 1;
  color: rgba(100,116,139,0.85);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
</style>
