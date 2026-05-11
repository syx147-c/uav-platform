<script setup>
/**
 * 驾驶舱主页 Dashboard
 * 包含：Cesium 3D 地球 + 实时遥测仪表盘 + 紧急控制面板
 */
import { ref, onMounted, watch } from 'vue';                          // Vue 3 API：响应式引用、生命周期钩子、侦听器
import { useWebSocket } from '../composables/useWebSocket';           // WebSocket 连接 Hook
import * as Cesium from 'cesium';                                     // Cesium 3D 地球引擎
import 'cesium/Build/Cesium/Widgets/widgets.css';                     // Cesium 必需样式表

// === Cesium 访问令牌（免费默认 token，仅用于基础 3D 地球） ===
Cesium.Ion.defaultAccessToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJqdGkiOiI5YjhlNDkyMi04MzQ1LTQ1YTUtYTYzZS1iNmM4YTg2YzEyZjIiLCJpZCI6NDMwMDI5LCJzdWIiOiJ4bWgxNDciLCJpc3MiOiJodHRwczovL2lvbi5jZXNpdW0uY29tIiwiYXVkIjoidWF2QWdlbnQiLCJpYXQiOjE3Nzg0OTYwMTV9.EHRjo5HeRFIdF-ledROkbgrAkRo8Eiwfkd8F4AY5LgA';

// === 初始化 WebSocket 连接 ===
const { telemetry, connected, connect } = useWebSocket();

// === 控制指令状态 ===
const sending = ref(false);                              // 是否正在发送指令（防重复点击）

// === Cesium 地图：无人机实体引用 ===
let droneEntity = null;                                  // Cesium Entity：表示无人机位置的点位标记

/**
 * 发送控制指令到后端
 * @param {string} command - 指令名称：takeoff / land / hold
 */
async function sendCommand(command) {
  sending.value = true;                                  // 锁定按钮，防止重复发送
  try {
    const res = await fetch(`/api/drone/${command}`, {   // 通过 Vite 代理发送到 Spring Boot 后端
      method: 'POST'
    });
    const text = await res.text();                       // 获取响应文本
    console.log(`${command} 响应:`, text);
  } catch (err) {
    console.error(`${command} 失败:`, err);
  } finally {
    setTimeout(() => { sending.value = false; }, 1000);  // 1 秒后解锁按钮
  }
}

/**
 * 页面挂载完成后初始化
 */
onMounted(() => {
  connect();                                             // 建立 WebSocket 连接

  // 初始化 Cesium Viewer（3D 地球）
  const viewer = new Cesium.Viewer('cesiumContainer', {
    animation: false,                                    // 隐藏底部动画控件
    timeline: false,                                     // 隐藏底部时间线控件
    baseLayerPicker: false,                              // 隐藏底图选择器
    fullscreenButton: false,                             // 隐藏全屏按钮
    homeButton: false,                                   // 隐藏 Home 按钮
    navigationHelpButton: false,                         // 隐藏帮助按钮
    sceneModePicker: false                               // 隐藏 2D/3D 切换器
  });

  // 添加无人机点位标记（初始位置）
  droneEntity = viewer.entities.add({
    position: Cesium.Cartesian3.fromDegrees(0, 0, 0),   // 经纬度 + 高度
    point: {
      pixelSize: 12,                                     // 点大小
      color: Cesium.Color.RED                            // 红色标记
    },
    label: {
      text: 'Drone',
      font: '14px sans-serif',
      verticalOrigin: Cesium.VerticalOrigin.BOTTOM,      // 标签在点的下方
      pixelOffset: new Cesium.Cartesian2(0, -10)         // 向下偏移 10px
    }
  });

  // 初始视角飞到中国上空
  viewer.camera.flyTo({
    destination: Cesium.Cartesian3.fromDegrees(116.4, 39.9, 100000) // 北京上空 100km
  });
});

/**
 * 监听遥测数据变化，实时更新无人机在地图上的位置
 */
watch(telemetry, (newData) => {
  if (!droneEntity || !newData.latitude) return;         // 数据无效或实体未创建时跳过
  droneEntity.position = Cesium.Cartesian3.fromDegrees(
    newData.longitude,
    newData.latitude,
    newData.altitude || 0                                // 相对高度（米）
  );
}, { deep: true });                                       // 深度监听：对象内部属性变化时也触发
</script>

<template>
  <div class="dashboard">
    <!-- 顶部状态栏 -->
    <header class="top-bar">
      <h1>UAV 飞行控制平台</h1>
      <span class="conn-status" :class="{ connected }">
        {{ connected ? '🟢 已连接' : '🔴 未连接' }}
      </span>
    </header>

    <div class="main-content">
      <!-- 左侧：3D 地球地图 -->
      <div class="map-panel">
        <div id="cesiumContainer"></div>
      </div>

      <!-- 右侧：遥测仪表盘 + 紧急控制 -->
      <aside class="side-panel">
        <!-- 遥测数据面板 -->
        <section class="telemetry-panel">
          <h2>实时遥测</h2>
          <div class="metric">
            <label>纬度</label>
            <span>{{ telemetry.latitude?.toFixed(6) ?? '--' }}°</span>
          </div>
          <div class="metric">
            <label>经度</label>
            <span>{{ telemetry.longitude?.toFixed(6) ?? '--' }}°</span>
          </div>
          <div class="metric">
            <label>高度</label>
            <span>{{ telemetry.altitude?.toFixed(2) ?? '--' }} m</span>
          </div>
          <div class="metric">
            <label>电池</label>
            <span>{{ telemetry.battery ?? '--' }}%</span>
          </div>
          <div class="metric">
            <label>飞行状态</label>
            <span>{{ telemetry.in_air ? '飞行中' : '地面' }}</span>
          </div>
        </section>

        <!-- 紧急控制面板 -->
        <section class="control-panel">
          <h2>飞控指令</h2>
          <button class="btn btn-takeoff" @click="sendCommand('takeoff')" :disabled="sending">
            起飞
          </button>
          <button class="btn btn-hold" @click="sendCommand('hold')" :disabled="sending">
            悬停
          </button>
          <button class="btn btn-land" @click="sendCommand('land')" :disabled="sending">
            降落
          </button>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
/* 根容器：全屏 flex 布局 */
.dashboard {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #1a1a2e;
  color: #e0e0e0;
}

/* 顶部状态栏 */
.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px;
  background: #16213e;
  border-bottom: 1px solid #0f3460;
}

.top-bar h1 {
  font-size: 20px;
  margin: 0;
}

/* 连接状态指示器 */
.conn-status { font-size: 14px; }
.conn-status.connected { color: #4caf50; }

/* 主内容区：地图 + 侧边栏 */
.main-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* 左侧地图面板 */
.map-panel {
  flex: 1;
  position: relative;
}

#cesiumContainer {
  width: 100%;
  height: 100%;
}

/* 右侧面板 */
.side-panel {
  width: 280px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
  background: #16213e;
  overflow-y: auto;
}

.side-panel h2 {
  font-size: 16px;
  margin: 0 0 10px 0;
  padding-bottom: 6px;
  border-bottom: 1px solid #0f3460;
}

/* 遥测指标 */
.metric {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 14px;
}

.metric label { color: #888; }
.metric span { font-weight: bold; color: #4fc3f7; }

/* 控制按钮 */
.control-panel button {
  width: 100%;
  padding: 12px;
  margin-bottom: 8px;
  border: none;
  border-radius: 4px;
  font-size: 15px;
  font-weight: bold;
  cursor: pointer;
  transition: opacity 0.2s;
}

.control-panel button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-takeoff { background: #4caf50; color: #fff; }
.btn-hold { background: #ff9800; color: #fff; }
.btn-land { background: #f44336; color: #fff; }
</style>
