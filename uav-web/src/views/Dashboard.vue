<script setup>
/**
 * 驾驶舱主页 — Cesium 3D 地球 + 飞控指令面板
 */
import { ref, onMounted, watch } from 'vue';                        // Vue 3 API
import { ElMessage } from 'element-plus';                           // Element Plus 消息提示
import * as Cesium from 'cesium';                                    // Cesium 3D 地球引擎
import 'cesium/Build/Cesium/Widgets/widgets.css';                    // Cesium 样式

// === 接收从 AppLayout 传入的遥测数据和连接状态 ===
const props = defineProps({
  telemetry: { type: Object, default: () => ({}) },                 // 当前遥测数据
  connected: { type: Boolean, default: false }                      // WebSocket 连接状态
});

// === Cesium Ion 访问令牌 ===
Cesium.Ion.defaultAccessToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJqdGkiOiI5YjhlNDkyMi04MzQ1LTQ1YTUtYTYzZS1iNmM4YTg2YzEyZjIiLCJpZCI6NDMwMDI5LCJzdWIiOiJ4bWgxNDciLCJpc3MiOiJodHRwczovL2lvbi5jZXNpdW0uY29tIiwiYXVkIjoidWF2QWdlbnQiLCJpYXQiOjE3Nzg0OTYwMTV9.EHRjo5HeRFIdF-ledROkbgrAkRo8Eiwfkd8F4AY5LgA';

// === 无人机 3D 标记实体 ===
let droneEntity = null;                                              // Cesium Entity 对象

// === 指令发送状态 ===
const sending = ref(false);                                          // 防重复点击

/**
 * 发送飞控指令到后端
 * @param {string} command - takeoff / land / hold
 */
async function sendCommand(command) {
  sending.value = true;                                              // 锁定按钮
  try {
    const res = await fetch(`/api/drone/${command}`, { method: 'POST' });
    ElMessage.success(`指令 ${command} 已发送`);                     // 成功提示
  } catch (e) {
    ElMessage.error(`指令 ${command} 发送失败`);                     // 失败提示
  } finally {
    setTimeout(() => (sending.value = false), 1500);                 // 1.5 秒后解锁
  }
}

/**
 * 页面挂载时初始化 Cesium Viewer
 */
onMounted(() => {
  const viewer = new Cesium.Viewer('cesiumContainer', {
    animation: false,                                                // 隐藏底部动画控件
    timeline: false,                                                 // 隐藏时间线
    baseLayerPicker: false,                                          // 隐藏底图选择器
    fullscreenButton: false,
    homeButton: false,
    navigationHelpButton: false,
    sceneModePicker: false,
    infoBox: false                                                   // 隐藏信息弹窗
  });

  // 添加红色标记点表示无人机位置
  droneEntity = viewer.entities.add({
    position: Cesium.Cartesian3.fromDegrees(0, 0, 0),               // 初始位置（后面会更新）
    point: { pixelSize: 12, color: Cesium.Color.RED },               // 红色 12px 圆点
    label: {
      text: 'Drone',
      font: '14px sans-serif',
      verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
      pixelOffset: new Cesium.Cartesian2(0, -12)                     // 标签在点上方
    }
  });

  // 摄像机飞到北京上空
  viewer.camera.flyTo({
    destination: Cesium.Cartesian3.fromDegrees(116.4, 39.9, 100000)
  });
});

/**
 * 监听遥测数据变化 → 实时更新无人机位置
 */
watch(
  () => props.telemetry,                                             // 监听传入的遥测 prop
  (data) => {
    if (!droneEntity || !data?.latitude) return;                     // 数据无效时跳过
    droneEntity.position = Cesium.Cartesian3.fromDegrees(
      data.longitude, data.latitude, data.altitude || 0              // 经纬度 + 高度
    );
  },
  { deep: true }                                                      // 深度监听对象内部属性变化
);
</script>

<template>
  <div class="dashboard">
    <!-- 左侧：Cesium 3D 地球 -->
    <div class="map-panel">
      <div id="cesiumContainer"></div>
    </div>

    <!-- 右侧：控制面板 -->
    <aside class="side-panel">
      <!-- 连接状态卡片 -->
      <el-card shadow="hover" class="panel-card">
        <div class="panel-header">连接状态</div>
        <el-tag :type="connected ? 'success' : 'danger'" size="large" effect="dark">
          {{ connected ? '已连接' : '未连接' }}
        </el-tag>
      </el-card>

      <!-- 实时遥测卡片 -->
      <el-card shadow="hover" class="panel-card">
        <div class="panel-header">实时遥测</div>
        <div class="telem-row"><span>纬度</span><strong>{{ telemetry?.latitude?.toFixed(6) ?? '--' }}°</strong></div>
        <div class="telem-row"><span>经度</span><strong>{{ telemetry?.longitude?.toFixed(6) ?? '--' }}°</strong></div>
        <div class="telem-row"><span>高度</span><strong>{{ telemetry?.altitude?.toFixed(2) ?? '--' }} m</strong></div>
        <div class="telem-row"><span>电池</span><strong>{{ telemetry?.battery ?? '--' }}%</strong></div>
        <div class="telem-row"><span>飞行状态</span><strong>{{ telemetry?.in_air ? '飞行中' : '地面' }}</strong></div>
      </el-card>

      <!-- 飞控指令卡片 -->
      <el-card shadow="hover" class="panel-card">
        <div class="panel-header">飞控指令</div>
        <el-button
          type="success" size="large" :loading="sending"
          @click="sendCommand('takeoff')" style="width:100%;margin-bottom:10px"
        > 🛫 起飞</el-button>
        <el-button
          type="warning" size="large" :loading="sending"
          @click="sendCommand('hold')" style="width:100%;margin-bottom:10px"
        > ✋ 悬停</el-button>
        <el-button
          type="danger" size="large" :loading="sending"
          @click="sendCommand('land')" style="width:100%"
        > 🛬 降落</el-button>
      </el-card>
    </aside>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  gap: 16px;
  height: 100%;
}

/* 左侧 3D 地图 */
.map-panel {
  flex: 1;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}
#cesiumContainer {
  width: 100%;
  height: 100%;
}

/* 右侧面板 */
.side-panel {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
}

/* Element Plus 卡片 */
.panel-card { border-radius: 8px; }
.panel-header {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}

/* 遥测数据行 */
.telem-row {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 13px;
  border-bottom: 1px dashed #f0f0f0;
}
.telem-row span { color: #999; }
.telem-row strong { color: #409EFF; }
</style>
