<script setup>
/**
 * 数据图表页面 — 实时遥测可视化
 * 使用 ECharts 绘制高度、电池、速度等实时图表
 */
import { ref, onMounted, onUnmounted, watch, computed } from 'vue';  // Vue 3 API
import VChart from 'vue-echarts';                                     // ECharts Vue 封装组件
import { use } from 'echarts/core';                                   // ECharts 核心
import { LineChart, BarChart, GaugeChart } from 'echarts/charts';     // 图表类型
import { GridComponent, TitleComponent, TooltipComponent } from 'echarts/components'; // 组件
import { CanvasRenderer } from 'echarts/renderers';                   // 渲染器

// 注册 ECharts 模块（按需引入，减小打包体积）
use([LineChart, BarChart, GaugeChart, GridComponent, TitleComponent, TooltipComponent, CanvasRenderer]);

// === Props：从 AppLayout 传入的遥测数据 ===
const props = defineProps({
  telemetry: { type: Object, default: () => ({}) },                  // 当前遥测数据
  connected: { type: Boolean, default: false }                       // WebSocket 连接状态
});

// === 历史数据缓存（用于绘制折线图） ===
const MAX_POINTS = 60;                                               // 最多保留 60 个数据点（2 秒 × 60 = 2 分钟）
const altitudeHistory = ref([]);                                     // 高度历史
const batteryHistory = ref([]);                                      // 电量历史
const speedHistory = ref([]);                                        // 速度历史

// === 监听遥测数据变化，追加到历史数组 ===
watch(() => props.telemetry, (newVal) => {
  if (!newVal || !newVal.latitude) return;                           // 数据无效跳过
  const time = new Date().toLocaleTimeString();                      // 当前时间戳
  altitudeHistory.value.push({ time, value: newVal.altitude ?? 0 });
  batteryHistory.value.push({ time, value: newVal.battery ?? 0 });
  const speed = Math.sqrt(
    (newVal.velocityX || 0) ** 2 + (newVal.velocityY || 0) ** 2 + (newVal.velocityZ || 0) ** 2
  );
  speedHistory.value.push({ time, value: speed.toFixed(2) });
  // 超过最大点数时移除最早的
  [altitudeHistory, batteryHistory, speedHistory].forEach(arr => {
    if (arr.value.length > MAX_POINTS) arr.value.shift();
  });
}, { deep: true });

// === 通用折线图选项生成器 ===
function lineOption(data, title, unit, color) {
  return {
    title: { text: title, left: 'center', textStyle: { fontSize: 14, color: '#666' } },
    tooltip: { trigger: 'axis' },
    grid: { top: 40, right: 20, bottom: 30, left: 50 },
    xAxis: { type: 'category', data: data.map(d => d.time), axisLabel: { rotate: 45, fontSize: 10 } },
    yAxis: { type: 'value', name: unit, nameTextStyle: { fontSize: 11 } },
    series: [{
      data: data.map(d => d.value), type: 'line', smooth: true, showSymbol: false,
      lineStyle: { color, width: 2 }, areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [{ offset: 0, color }, { offset: 1, color: 'rgba(255,255,255,0)' }] } }
    }]
  };
}

// === 计算各图表配置项 ===
const altitudeOption = computed(() => lineOption(altitudeHistory.value, '高度（米）', 'm', '#409EFF'));
const batteryOption = computed(() => lineOption(batteryHistory.value, '电量（%）', '%', '#67C23A'));
const speedOption = computed(() => lineOption(speedHistory.value, '速度（m/s）', 'm/s', '#E6A23C'));

// === 仪表盘：当前电量 ===
const gaugeOption = computed(() => ({
  series: [{
    type: 'gauge', center: ['50%', '60%'], radius: '80%',
    startAngle: 200, endAngle: -20,
    min: 0, max: 100,
    axisLine: { lineStyle: { width: 20, color: [[0.3, '#67C23A'], [0.7, '#E6A23C'], [1, '#F56C6C']] } },
    pointer: { itemStyle: { color: '#333' } },
    detail: { formatter: '{value}%', fontSize: 28, offsetCenter: [0, '70%'] },
    data: [{ value: props.telemetry?.battery ?? 0, name: '电池电量' }]
  }]
}));
</script>

<template>
  <div class="charts-page">
    <!-- 顶部状态卡片 -->
    <div class="cards-row">
      <el-card class="stat-card" shadow="hover">
        <span class="card-label">高度</span>
        <span class="card-value">{{ telemetry?.altitude?.toFixed(2) ?? '--' }} <small>m</small></span>
      </el-card>
      <el-card class="stat-card" shadow="hover">
        <span class="card-label">电池</span>
        <span class="card-value">{{ telemetry?.battery ?? '--' }} <small>%</small></span>
      </el-card>
      <el-card class="stat-card" shadow="hover">
        <span class="card-label">GPS 卫星</span>
        <span class="card-value">{{ telemetry?.gps_fix ?? '--' }}</span>
      </el-card>
      <el-card class="stat-card" shadow="hover">
        <span class="card-label">连接状态</span>
        <span class="card-value" :style="{ color: connected ? '#67C23A' : '#F56C6C' }">
          {{ connected ? '在线' : '离线' }}
        </span>
      </el-card>
    </div>

    <!-- 图表区域 -->
    <div class="charts-grid">
      <el-card shadow="hover" class="chart-card">
        <VChart :option="altitudeOption" autoresize style="height: 280px" />
      </el-card>
      <el-card shadow="hover" class="chart-card">
        <VChart :option="speedOption" autoresize style="height: 280px" />
      </el-card>
      <el-card shadow="hover" class="chart-card">
        <VChart :option="batteryOption" autoresize style="height: 280px" />
      </el-card>
      <el-card shadow="hover" class="chart-card">
        <VChart :option="gaugeOption" autoresize style="height: 280px" />
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.charts-page { display: flex; flex-direction: column; gap: 16px; }

/* 顶部统计卡片 */
.cards-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.stat-card { text-align: center; }
.card-label { display: block; font-size: 13px; color: #999; margin-bottom: 4px; }
.card-value { font-size: 28px; font-weight: bold; color: #333; }
.card-value small { font-size: 14px; color: #999; font-weight: normal; }

/* 图表网格 */
.charts-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
.chart-card { min-height: 300px; }
</style>
