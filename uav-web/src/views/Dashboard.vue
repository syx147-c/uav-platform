<script setup>
/**
 * 驾驶舱主页 — 3D 战场视图 + 霓虹控制面板
 */
import { ref, onMounted, watch } from 'vue';
import { ElMessage } from 'element-plus';
import * as Cesium from 'cesium';
import 'cesium/Build/Cesium/Widgets/widgets.css';
import { api } from '../api/http.js';

const props = defineProps({
  telemetry: { type: Object, default: () => ({}) },
  connected: { type: Boolean, default: false }
});

Cesium.Ion.defaultAccessToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJqdGkiOiI5YjhlNDkyMi04MzQ1LTQ1YTUtYTYzZS1iNmM4YTg2YzEyZjIiLCJpZCI6NDMwMDI5LCJzdWIiOiJ4bWgxNDciLCJpc3MiOiJodHRwczovL2lvbi5jZXNpdW0uY29tIiwiYXVkIjoidWF2QWdlbnQiLCJpYXQiOjE3Nzg0OTYwMTV9.EHRjo5HeRFIdF-ledROkbgrAkRo8Eiwfkd8F4AY5LgA';

let droneEntity = null;
let viewer = null;
const sending = ref(false);

async function sendCommand(command) {
  sending.value = true;
  try {
    await api(`/api/drone/${command}`, { method: 'POST' });
    ElMessage({ message: `指令 ${command.toUpperCase()} 已发送`, type: 'success',
      customClass: 'el-message-tech', duration: 1500 });
  } catch (e) {
    ElMessage({ message: `发送失败`, type: 'error', customClass: 'el-message-tech' });
  } finally {
    setTimeout(() => sending.value = false, 1500);
  }
}

onMounted(() => {
  viewer = new Cesium.Viewer('cesiumContainer', {
    animation: false, timeline: false, baseLayerPicker: false,
    fullscreenButton: false, homeButton: false, navigationHelpButton: false,
    sceneModePicker: false, infoBox: false, geocoder: false,
  });
  // 暗色底图
  viewer.scene.globe.baseColor = Cesium.Color.fromCssColorString('#060b1a');
  viewer.scene.backgroundColor = Cesium.Color.fromCssColorString('#060b1a');

  droneEntity = viewer.entities.add({
    position: Cesium.Cartesian3.fromDegrees(0, 0, 0),
    point: { pixelSize: 14, color: Cesium.Color.CYAN, outlineColor: Cesium.Color.WHITE, outlineWidth: 1 },
    label: { text: '● DRONE', font: '11px monospace', fillColor: Cesium.Color.CYAN,
      verticalOrigin: Cesium.VerticalOrigin.BOTTOM, pixelOffset: new Cesium.Cartesian2(0, -14) }
  });

  viewer.camera.flyTo({
    destination: Cesium.Cartesian3.fromDegrees(116.4, 39.9, 80000)
  });
});

watch(() => props.telemetry, (data) => {
  if (!droneEntity || !data?.latitude) return;
  droneEntity.position = Cesium.Cartesian3.fromDegrees(data.longitude, data.latitude, data.altitude || 0);
}, { deep: true });
</script>

<template>
  <div class="dashboard">
    <!-- 3D 地球 -->
    <div class="map-wrapper">
      <div class="map-frame">
        <div class="map-corner tl"></div><div class="map-corner tr"></div>
        <div class="map-corner bl"></div><div class="map-corner br"></div>
        <div id="cesiumContainer"></div>
      </div>
      <div class="map-label">◈ MISSION LIVE VIEW</div>
    </div>

    <!-- 右侧面板 -->
    <aside class="control-panel">
      <!-- 连接状态 -->
      <section class="panel-section conn-section">
        <div class="section-label">// SYSTEM STATUS</div>
        <div class="conn-indicator" :class="{ live: connected }">
          <span class="conn-ring"></span>
          <span class="conn-text">{{ connected ? 'LIVE FEED ACTIVE' : 'NO SIGNAL' }}</span>
        </div>
      </section>

      <!-- 遥测矩阵 -->
      <section class="panel-section">
        <div class="section-label">// TELEMETRY MATRIX</div>
        <div class="telem-grid">
          <div class="telem-cell">
            <span class="cell-label">LAT</span>
            <span class="cell-val">{{ telemetry?.latitude?.toFixed(6) ?? '--' }}</span>
          </div>
          <div class="telem-cell">
            <span class="cell-label">LON</span>
            <span class="cell-val">{{ telemetry?.longitude?.toFixed(6) ?? '--' }}</span>
          </div>
          <div class="telem-cell">
            <span class="cell-label">ALT</span>
            <span class="cell-val">{{ telemetry?.altitude?.toFixed(2) ?? '--' }}<small>m</small></span>
          </div>
          <div class="telem-cell">
            <span class="cell-label">BATT</span>
            <span class="cell-val" :class="{ warn: (telemetry?.battery ?? 100) < 30 }">{{ telemetry?.battery ?? '--' }}<small>%</small></span>
          </div>
          <div class="telem-cell">
            <span class="cell-label">GPS</span>
            <span class="cell-val">{{ telemetry?.gps_fix ?? '--' }}</span>
          </div>
          <div class="telem-cell">
            <span class="cell-label">MODE</span>
            <span class="cell-val" :style="{ color: telemetry?.in_air ? 'var(--cyan)' : 'var(--gray)' }">
              {{ telemetry?.in_air ? 'AIR' : 'GND' }}
            </span>
          </div>
        </div>
      </section>

      <!-- 飞控指令 -->
      <section class="panel-section">
        <div class="section-label">// FLIGHT COMMAND</div>
        <div class="cmd-grid">
          <button class="cmd-btn takeoff" @click="sendCommand('takeoff')" :disabled="sending">
            <span class="cmd-icon">▲</span>
            <span class="cmd-text">TAKEOFF</span>
          </button>
          <button class="cmd-btn hold" @click="sendCommand('hold')" :disabled="sending">
            <span class="cmd-icon">■</span>
            <span class="cmd-text">HOLD</span>
          </button>
          <button class="cmd-btn land" @click="sendCommand('land')" :disabled="sending">
            <span class="cmd-icon">▼</span>
            <span class="cmd-text">LAND</span>
          </button>
        </div>
      </section>

      <!-- 视觉装饰 -->
      <div class="decor-line"></div>
      <div class="decor-text">SYS::READY</div>
    </aside>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  gap: 16px;
  height: 100%;
}

/* ===== 3D 地图 ===== */
.map-wrapper { flex: 1; display: flex; flex-direction: column; gap: 8px; }
.map-frame {
  flex: 1;
  position: relative;
  border: 1px solid rgba(59,130,246,0.2);
  border-radius: 8px;
  overflow: hidden;
  box-shadow: var(--glow-cyan);
}
#cesiumContainer { width: 100%; height: 100%; }

/* 四角边框装饰 */
.map-corner {
  position: absolute; width: 20px; height: 20px; z-index: 100; pointer-events: none;
  border-color: var(--cyan);
}
.map-corner.tl { top: 0; left: 0; border-top: 2px solid; border-left: 2px solid; }
.map-corner.tr { top: 0; right: 0; border-top: 2px solid; border-right: 2px solid; }
.map-corner.bl { bottom: 0; left: 0; border-bottom: 2px solid; border-left: 2px solid; }
.map-corner.br { bottom: 0; right: 0; border-bottom: 2px solid; border-right: 2px solid; }

.map-label {
  font-family: var(--font-mono); font-size: 10px;
  color: var(--cyan); letter-spacing: 3px; text-align: center;
  opacity: 0.5;
}

/* ===== 控制面板 ===== */
.control-panel {
  width: 280px; flex-shrink: 0;
  display: flex; flex-direction: column; gap: 12px;
}
.panel-section {
  background: linear-gradient(135deg, rgba(255,255,255,0.9), rgba(248,250,252,0.8));
  backdrop-filter: blur(12px);
  border: 1px solid rgba(59,130,246,0.12);
  border-radius: 10px;
  padding: 16px;
}

.section-label {
  font-family: var(--font-mono); font-size: 9px;
  color: var(--gray); letter-spacing: 2px;
  margin-bottom: 12px;
  opacity: 0.7;
}

/* 连接指示灯 */
.conn-section { display: flex; flex-direction: column; align-items: center; }
.conn-indicator {
  display: flex; align-items: center; gap: 10px;
  padding: 8px 16px;
  border: 1px solid rgba(244,63,94,0.3);
  border-radius: 20px;
}
.conn-indicator.live {
  border-color: rgba(16,185,129,0.4);
  box-shadow: 0 0 16px rgba(16,185,129,0.1);
}
.conn-ring {
  width: 8px; height: 8px; border-radius: 50%;
  background: var(--rose);
  box-shadow: 0 0 6px var(--rose);
}
.conn-indicator.live .conn-ring {
  background: var(--emerald);
  box-shadow: 0 0 8px var(--emerald);
  animation: pulse-glow 2s ease-in-out infinite;
}
.conn-text {
  font-family: var(--font-mono); font-size: 11px;
  color: var(--rose); letter-spacing: 1px;
}
.conn-indicator.live .conn-text { color: var(--emerald); }

/* 遥测矩阵 */
.telem-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 6px;
}
.telem-cell {
  padding: 8px 10px;
  background: rgba(59,130,246,0.03);
  border: 1px solid rgba(59,130,246,0.06);
  border-radius: 6px;
}
.cell-label {
  display: block; font-size: 8px; color: var(--gray);
  letter-spacing: 1.5px; font-family: var(--font-mono);
}
.cell-val {
  font-family: var(--font-mono); font-size: 14px; font-weight: 700;
  color: var(--cyan);
  margin-top: 2px;
}
.cell-val small { font-size: 10px; color: var(--gray); font-weight: 400; margin-left: 2px; }
.cell-val.warn { color: var(--rose); }

/* 飞控按钮 */
.cmd-grid { display: flex; flex-direction: column; gap: 8px; }
.cmd-btn {
  display: flex; align-items: center; gap: 12px;
  width: 100%; padding: 14px 16px;
  border: 1px solid; border-radius: 8px;
  background: transparent;
  cursor: pointer;
  transition: all 0.25s;
  font-family: var(--font-mono);
}
.cmd-btn:disabled { opacity: 0.4; cursor: not-allowed; }

.cmd-btn.takeoff {
  border-color: rgba(16,185,129,0.3);
  color: var(--emerald);
}
.cmd-btn.takeoff:hover:not(:disabled) {
  background: rgba(16,185,129,0.1);
  box-shadow: 0 0 20px rgba(16,185,129,0.15);
}

.cmd-btn.hold {
  border-color: rgba(245,158,11,0.3);
  color: var(--amber);
}
.cmd-btn.hold:hover:not(:disabled) {
  background: rgba(245,158,11,0.1);
  box-shadow: 0 0 20px rgba(245,158,11,0.15);
}

.cmd-btn.land {
  border-color: rgba(244,63,94,0.3);
  color: var(--rose);
}
.cmd-btn.land:hover:not(:disabled) {
  background: rgba(244,63,94,0.1);
  box-shadow: 0 0 20px rgba(244,63,94,0.15);
}

.cmd-icon { font-size: 18px; width: 24px; text-align: center; }
.cmd-text { font-size: 13px; font-weight: 700; letter-spacing: 2px; }

/* 装饰 */
.decor-line {
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(59,130,246,0.2), transparent);
}
.decor-text {
  font-family: var(--font-mono); font-size: 9px;
  color: var(--cyan); letter-spacing: 3px; text-align: center;
  opacity: 0.4;
}
</style>
