<script setup>
/**
 * 数据图表页面 — 霓虹主题实时遥测可视化
 */
import { ref, watch, computed } from 'vue';
import VChart from 'vue-echarts';
import { use } from 'echarts/core';
import { LineChart, GaugeChart } from 'echarts/charts';
import { GridComponent, TitleComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
use([LineChart, GaugeChart, GridComponent, TitleComponent, TooltipComponent, CanvasRenderer]);

const props = defineProps({
  telemetry: { type: Object, default: () => ({}) },
  connected: { type: Boolean, default: false }
});

const MAX = 80;
const altHistory  = ref([]);
const battHistory = ref([]);
const spdHistory  = ref([]);

// 浅蓝色 ECharts 通用主题
const chartTheme = {
  textStyle: { color: '#64748b' },
  title: { textStyle: { color: '#1e293b', fontSize: 13, fontWeight: 600 } },
  tooltip: { backgroundColor: 'rgba(255,255,255,0.95)', borderColor: 'rgba(59,130,246,0.3)', textStyle: { color: '#1e293b' } },
};

watch(() => props.telemetry, (d) => {
  if (!d?.latitude) return;
  const t = new Date().toLocaleTimeString('zh-CN', { hour12: false });
  altHistory.value.push({ time: t, val: d.altitude ?? 0 });
  battHistory.value.push({ time: t, val: d.battery ?? 0 });
  const s = Math.sqrt((d.velocityX||0)**2 + (d.velocityY||0)**2 + (d.velocityZ||0)**2);
  spdHistory.value.push({ time: t, val: +s.toFixed(2) });
  [altHistory, battHistory, spdHistory].forEach(a => { if (a.value.length > MAX) a.value.shift(); });
}, { deep: true });

function lineOpt(data, title, unit, color) {
  return {
    ...chartTheme,
    title: { ...chartTheme.title, text: title },
    grid: { top: 36, right: 16, bottom: 28, left: 48 },
    xAxis: { type: 'category', data: data.map(d => d.time),
      axisLine: { lineStyle: { color: 'rgba(59,130,246,0.15)' } },
      axisLabel: { color: '#64748b', fontSize: 9, rotate: 30 } },
    yAxis: { type: 'value', name: unit,
      splitLine: { lineStyle: { color: 'rgba(59,130,246,0.06)' } },
      axisLine: { lineStyle: { color: 'rgba(59,130,246,0.15)' } },
      axisLabel: { color: '#64748b', fontSize: 10 } },
    series: [{
      data: data.map(d => d.val), type: 'line', smooth: true, showSymbol: false,
      lineStyle: { color, width: 2.5, shadowBlur: 10, shadowColor: color },
      areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [{ offset: 0, color: color + '30' }, { offset: 1, color: 'transparent' }] } }
    }]
  };
}

const altOpt  = computed(() => lineOpt(altHistory.value,  'ALTITUDE / 高度', 'm',   '#3b82f6'));
const battOpt = computed(() => lineOpt(battHistory.value, 'BATTERY / 电量',  '%',   '#3b82f6'));
const spdOpt  = computed(() => lineOpt(spdHistory.value,  'SPEED / 速度',    'm/s', '#6366f1'));

const gaugeOpt = computed(() => ({
  ...chartTheme,
  series: [{
    type: 'gauge', center: ['50%', '55%'], radius: '85%',
    startAngle: 210, endAngle: -30, min: 0, max: 100,
    progress: { show: true, width: 16, itemStyle: { color: { type: 'linear', x: 0, y:0, x2: 1, y2: 0,
      colorStops: [{ offset: 0, color: '#10b981'}, {offset: 0.3, color: '#3b82f6'}, {offset: 0.7, color: '#f59e0b'}, {offset: 1, color: '#f43f5e'}] } } },
    axisLine: { lineStyle: { width: 16, color: [[0.3, '#10b981'],[0.7, '#f59e0b'],[1, '#f43f5e']] } },
    axisTick: { show: false },
    axisLabel: { color: '#64748b', fontSize: 10 },
    pointer: { itemStyle: { color: '#3b82f6' } },
    detail: { formatter: '{value}%', fontSize: 24, color: '#3b82f6', offsetCenter: [0, '75%'],
      fontFamily: 'JetBrains Mono, monospace' },
    data: [{ value: props.telemetry?.battery ?? 0, name: 'BATTERY' }]
  }]
}));
</script>

<template>
  <div class="charts-page">
    <!-- 顶部指标卡 -->
    <div class="kpi-row">
      <div class="kpi-card">
        <div class="kpi-label">ALTITUDE</div>
        <div class="kpi-val">{{ telemetry?.altitude?.toFixed(2) ?? '--' }}<span>m</span></div>
        <div class="kpi-trend">REALTIME</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">BATTERY</div>
        <div class="kpi-val" :class="{ warn: (telemetry?.battery ?? 100) < 30 }">{{ telemetry?.battery ?? '--' }}<span>%</span></div>
        <div class="kpi-trend">{{ (telemetry?.battery ?? 100) < 30 ? 'LOW' : 'NOMINAL' }}</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">SIGNAL</div>
        <div class="kpi-val" :style="{ color: connected ? 'var(--emerald)' : 'var(--rose)' }">{{ connected ? 'ON' : 'OFF' }}</div>
        <div class="kpi-trend">{{ connected ? 'LIVE' : 'LOST' }}</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">GPS FIX</div>
        <div class="kpi-val">{{ telemetry?.gps_fix ?? '--' }}</div>
        <div class="kpi-trend">3D LOCK</div>
      </div>
    </div>

    <!-- 折线图网格 -->
    <div class="chart-grid">
      <div class="chart-box">
        <VChart :option="altOpt" autoresize style="height:280px" />
      </div>
      <div class="chart-box">
        <VChart :option="spdOpt" autoresize style="height:280px" />
      </div>
      <div class="chart-box">
        <VChart :option="battOpt" autoresize style="height:280px" />
      </div>
      <div class="chart-box">
        <VChart :option="gaugeOpt" autoresize style="height:280px" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.charts-page { display: flex; flex-direction: column; gap: 16px; }

/* ===== KPI 卡片 ===== */
.kpi-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.kpi-card {
  background: linear-gradient(135deg, rgba(255,255,255,0.9), rgba(248,250,252,0.8));
  backdrop-filter: blur(12px);
  border: 1px solid rgba(59,130,246,0.1);
  border-radius: 10px;
  padding: 16px;
  text-align: center;
  position: relative;
  overflow: hidden;
  transition: all 0.3s;
}
.kpi-card:hover {
  border-color: rgba(59,130,246,0.3);
  box-shadow: var(--glow-cyan);
  transform: translateY(-2px);
}
.kpi-card::before {
  content: ''; position: absolute; top: 0; left: 0; right: 0; height: 2px;
  background: linear-gradient(90deg, transparent, var(--cyan), transparent);
  opacity: 0.5;
}
.kpi-label { font-family: var(--font-mono); font-size: 9px; color: var(--gray); letter-spacing: 2px; margin-bottom: 6px; }
.kpi-val { font-family: var(--font-mono); font-size: 30px; font-weight: 800; color: var(--cyan); }
.kpi-val span { font-size: 14px; color: var(--gray); font-weight: 400; margin-left: 4px; }
.kpi-val.warn { color: var(--rose); }
.kpi-trend { font-family: var(--font-mono); font-size: 9px; color: var(--gray); letter-spacing: 1.5px; margin-top: 4px; opacity: 0.6; }

/* ===== 图表网格 ===== */
.chart-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.chart-box {
  background: linear-gradient(135deg, rgba(255,255,255,0.9), rgba(248,250,252,0.8));
  backdrop-filter: blur(12px);
  border: 1px solid rgba(59,130,246,0.1);
  border-radius: 10px;
  padding: 12px;
  transition: all 0.3s;
}
.chart-box:hover {
  border-color: rgba(59,130,246,0.2);
  box-shadow: 0 0 20px rgba(59,130,246,0.06);
}
</style>
