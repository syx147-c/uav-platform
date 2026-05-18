<script setup>
/**
 * AI 控制台 — 终端风格自然语言无人机操控面板
 *
 * 功能：
 * - 自然语言飞行指令输入
 * - 工具执行步骤实时展示
 * - 对话历史持久化
 * - 快捷指令按钮
 */
import { ref, nextTick, onMounted, watch } from 'vue';
import { api } from '../api/http.js'; // 自动携带 JWT 的 HTTP 封装

// ===== 会话管理 =====
const sessionId = ref(localStorage.getItem('uav_agent_sid') || createId());
watch(sessionId, v => localStorage.setItem('uav_agent_sid', v));
function createId() { return 'UAV' + Date.now().toString(36).toUpperCase(); }

// ===== 状态 =====
const messages = ref([]);
const inputText = ref('');
const sending = ref(false);
const chatRef = ref(null);
const agentState = ref('IDLE');

// ===== 取消任务 =====
async function cancelMission() {
  try {
    await api('/api/agent/cancel', {
      method: 'POST',
      body: JSON.stringify({ sessionId: sessionId.value })
    });
    messages.value.push({ role: 'agent', text: '[已取消] 当前任务已被用户中断。', time: now(), steps: [], cancelled: true });
  } catch {
    messages.value.push({ role: 'agent', text: '[取消失败] 无法连接到后端。', time: now(), steps: [], error: true });
  }
  scrollDown();
}

// ===== 快捷指令 =====
const quicks = [
  { label: '查询状态',  cmd: '查询当前无人机状态' },
  { label: '起飞 10m',  cmd: '起飞到10米高度并悬停' },
  { label: '起飞 50m',  cmd: '起飞到50米高度' },
  { label: '降落',      cmd: '立即降落' },
  { label: '紧急悬停',  cmd: '紧急悬停！' },
  { label: '返航',      cmd: '立即返航回到起飞点' },
];

// ===== 发送消息 =====
async function sendMessage() {
  const text = inputText.value.trim();
  if (!text || sending.value) return;
  inputText.value = '';

  // 用户消息
  messages.value.push({ role: 'user', text, time: now() });
  // 加载占位
  const loader = { role: 'agent', text: '', time: now(), loading: true, steps: [] };
  messages.value.push(loader);
  scrollDown();
  sending.value = true;

  try {
    const data = await api('/api/agent/chat', {
      method: 'POST',
      body: JSON.stringify({ message: text, sessionId: sessionId.value })
    });
    agentState.value = data.state || 'IDLE';
    // 替换占位
    const idx = messages.value.indexOf(loader);
    messages.value[idx] = {
      role: 'agent',
      text: data.reply || data.error || '(无响应)',
      time: now(),
      steps: data.steps || [],
      error: !!data.error
    };
  } catch {
    const idx = messages.value.indexOf(loader);
    messages.value[idx] = {
      role: 'agent',
      text: '连接失败：后端服务不可用。请检查 Spring Boot 是否已启动。',
      time: now(),
      steps: [],
      error: true
    };
  } finally {
    sending.value = false;
    scrollDown();
  }
}

function quickSend(cmd) { inputText.value = cmd; sendMessage(); }

function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(); }
}

function clearChat() {
  messages.value = [];
  sessionId.value = createId();
}

function now() { return new Date().toLocaleTimeString('zh-CN', { hour12: false }); }

function scrollDown() {
  nextTick(() => {
    const el = chatRef.value;
    if (el) el.scrollTop = el.scrollHeight;
  });
}

onMounted(() => {
  messages.value.push({
    role: 'agent', text: 'UAV.AI 控制台 v3.0 已就绪。\n输入自然语言飞行指令，如：\n"起飞到 50 米悬停 30 秒后返航"\n"飞到坐标 (47.398, 8.546) 上空 20 米"',
    time: now(), steps: []
  });
  scrollDown();
});
</script>

<template>
  <div class="chat-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">AI COMMAND CENTER</h2>
        <p class="page-sub">自然语言无人机操控 &middot; DeepSeek Agent</p>
      </div>
      <div class="header-right">
        <span class="state-badge" :class="{ busy: agentState === 'EXECUTING' }">{{ agentState }}</span>
        <span class="sid-badge">SESS: {{ sessionId }}</span>
        <el-button size="small" text @click="cancelMission" :disabled="sending">■ 取消</el-button>
        <el-button size="small" text @click="clearChat">⟳ 新会话</el-button>
      </div>
    </div>

    <div class="term-window">
      <!-- 终端标题栏 -->
      <div class="term-bar">
        <span class="dot r"></span><span class="dot y"></span><span class="dot g"></span>
        <span class="term-label">UAV_AGENT_TERMINAL — v3.0 // {{ sessionId }}</span>
      </div>

      <!-- 消息列表 -->
      <div class="msg-list" ref="chatRef">
        <template v-for="(m, i) in messages" :key="i">
          <!-- 用户消息 -->
          <div v-if="m.role === 'user'" class="row user">
            <span class="time">{{ m.time }}</span>
            <span class="pfx user">></span>
            <span class="txt">{{ m.text }}</span>
          </div>
          <!-- Agent 消息 -->
          <div v-else class="row agent" :class="{ err: m.error }">
            <span class="time">{{ m.time }}</span>
            <span class="pfx agent">◆</span>
            <div class="body">
              <!-- 思考中 -->
              <div v-if="m.loading" class="think">
                <span class="t-dot"></span><span class="t-dot"></span><span class="t-dot"></span>
                AI 决策中...
              </div>
              <!-- 工具步骤 -->
              <div v-if="m.steps && m.steps.length" class="steps">
                <div class="steps-tag">▸ 工具调用记录</div>
                <div v-for="(s, j) in m.steps" :key="j" class="step" :class="{ fail: s.status !== 'ok' }">
                  <span class="s-ok">{{ s.status === 'ok' ? '✓' : '✗' }}</span>
                  <span class="s-tool">{{ s.tool }}</span>
                  <span class="s-arrow">→</span>
                  <span class="s-result">{{ s.result }}</span>
                </div>
              </div>
              <!-- 回复正文 -->
              <span v-if="!m.loading" class="txt">{{ m.text }}</span>
            </div>
          </div>
        </template>
      </div>

      <!-- 输入区域 -->
      <div class="input-area">
        <div class="quick-row">
          <span v-for="q in quicks" :key="q.label" class="chip" @click="quickSend(q.cmd)">{{ q.label }}</span>
        </div>
        <div class="input-row">
          <span class="prompt">></span>
          <input v-model="inputText" class="cmd" placeholder="输入飞行指令... Enter 发送"
            @keydown="onKeydown" :disabled="sending" autofocus />
          <button class="btn" @click="sendMessage" :disabled="sending || !inputText.trim()">
            {{ sending ? '执行' : '发送' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-page { display: flex; flex-direction: column; height: calc(100vh - 96px); gap: 12px; }
.page-header { display: flex; justify-content: space-between; align-items: center; flex-shrink: 0; }
.page-title { font-family: var(--font-mono); font-size: 18px; color: var(--white); letter-spacing: 2px; }
.page-sub { font-size: 12px; color: var(--gray); margin-top: 2px; font-family: var(--font-mono); }
.header-right { display: flex; align-items: center; gap: 12px; }
.sid-badge {
  font-family: var(--font-mono); font-size: 9px; color: rgba(59,130,246,0.5);
  padding: 4px 10px; border: 1px solid rgba(59,130,246,0.12); border-radius: 4px; letter-spacing: 1px;
}
.state-badge {
  font-family: var(--font-mono); font-size: 9px; font-weight: 700;
  padding: 3px 8px; border-radius: 4px; letter-spacing: 1px;
  background: rgba(16,185,129,0.08); color: var(--emerald);
  border: 1px solid rgba(16,185,129,0.2);
}
.state-badge.busy {
  background: rgba(245,158,11,0.08); color: var(--amber);
  border: 1px solid rgba(245,158,11,0.2);
  animation: pulse-glow 1.5s ease-in-out infinite;
}

/* === 终端窗口 === */
.term-window {
  flex: 1; display: flex; flex-direction: column; min-height: 0;
  background: rgba(255,255,255,0.95); border: 1px solid rgba(59,130,246,0.12);
  border-radius: 10px; overflow: hidden;
}
.term-bar {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 16px; background: rgba(0,0,0,0.05);
  border-bottom: 1px solid rgba(59,130,246,0.08);
}
.dot { width: 10px; height: 10px; border-radius: 50%; }
.dot.r { background: #f43f5e; } .dot.y { background: #f59e0b; } .dot.g { background: #10b981; }
.term-label { font-family: var(--font-mono); font-size: 10px; color: var(--gray); margin-left: 8px; letter-spacing: 1px; }

/* === 消息列表 === */
.msg-list {
  flex: 1; overflow-y: auto; padding: 12px 16px;
  font-family: var(--font-mono); display: flex; flex-direction: column; gap: 4px;
}
.row { display: flex; align-items: flex-start; gap: 8px; padding: 4px 8px; border-radius: 4px; font-size: 12px; line-height: 1.6; }
.row.user { background: rgba(59,130,246,0.03); }
.row.agent { background: rgba(16,185,129,0.02); }
.row.err { background: rgba(244,63,94,0.04); }
.time { color: rgba(100,116,139,0.45); font-size: 10px; min-width: 70px; flex-shrink: 0; }
.pfx { font-weight: 700; min-width: 14px; flex-shrink: 0; }
.pfx.user { color: var(--cyan); }
.pfx.agent { color: var(--emerald); }
.txt { color: var(--white); word-break: break-word; white-space: pre-wrap; }
.body { flex: 1; display: flex; flex-direction: column; gap: 6px; min-width: 0; }

/* === 思考指示 === */
.think { display: flex; align-items: center; gap: 4px; font-size: 10px; color: var(--gray); letter-spacing: 1px; }
.t-dot { width: 5px; height: 5px; border-radius: 50%; background: var(--cyan); animation: td 1.4s infinite; }
.t-dot:nth-child(2) { animation-delay: 0.2s; }
.t-dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes td { 0%,100% { opacity: 0.15; } 50% { opacity: 1; } }

/* === 工具步骤 === */
.steps {
  padding: 8px 10px; background: rgba(0,0,0,0.04);
  border: 1px solid rgba(59,130,246,0.06); border-radius: 6px;
}
.steps-tag { font-size: 9px; color: var(--gray); letter-spacing: 1px; margin-bottom: 6px; text-transform: uppercase; }
.step { display: flex; align-items: center; gap: 6px; padding: 2px 0; font-size: 10px; border-bottom: 1px solid rgba(59,130,246,0.03); }
.step:last-child { border-bottom: none; }
.s-ok { width: 12px; text-align: center; font-size: 9px; color: var(--emerald); font-weight: bold; }
.step.fail .s-ok { color: var(--rose); }
.s-tool { color: var(--purple); min-width: 100px; font-weight: 600; }
.s-arrow { color: var(--gray); font-size: 8px; }
.s-result { color: rgba(60,70,85,0.8); flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* === 输入区 === */
.input-area { border-top: 1px solid rgba(59,130,246,0.06); padding: 8px 14px 10px; flex-shrink: 0; }
.quick-row { display: flex; gap: 6px; margin-bottom: 8px; flex-wrap: wrap; }
.chip {
  font-family: var(--font-mono); font-size: 9px; padding: 3px 10px;
  background: rgba(59,130,246,0.05); border: 1px solid rgba(59,130,246,0.1);
  border-radius: 4px; color: var(--gray); cursor: pointer; transition: all 0.2s; letter-spacing: 0.5px; white-space: nowrap;
}
.chip:hover { background: rgba(59,130,246,0.12); color: var(--cyan); border-color: rgba(59,130,246,0.25); }
.input-row { display: flex; align-items: center; gap: 8px; }
.prompt { font-family: var(--font-mono); font-size: 16px; color: var(--cyan); font-weight: bold; flex-shrink: 0; }
.cmd { flex: 1; background: transparent; border: none; outline: none; color: var(--white); font-family: var(--font-mono); font-size: 12px; padding: 6px 0; }
.cmd::placeholder { color: rgba(100,116,139,0.45); letter-spacing: 0.5px; }
.cmd:disabled { opacity: 0.4; }
.btn {
  padding: 6px 18px; background: rgba(59,130,246,0.08); border: 1px solid rgba(59,130,246,0.25);
  border-radius: 6px; color: var(--cyan); font-family: var(--font-mono); font-size: 12px; font-weight: 700;
  letter-spacing: 1px; cursor: pointer; transition: all 0.2s; flex-shrink: 0;
}
.btn:hover:not(:disabled) { background: rgba(59,130,246,0.18); box-shadow: 0 0 14px rgba(59,130,246,0.12); }
.btn:disabled { opacity: 0.35; cursor: not-allowed; }

/* scrollbar */
.msg-list::-webkit-scrollbar { width: 4px; }
.msg-list::-webkit-scrollbar-track { background: transparent; }
.msg-list::-webkit-scrollbar-thumb { background: rgba(59,130,246,0.1); border-radius: 2px; }
</style>
