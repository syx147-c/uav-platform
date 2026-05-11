import { ref, onUnmounted } from 'vue';            // Vue 3 响应式 API

/**
 * WebSocket 遥测连接 Hook
 * 职责：建立与后端的 WebSocket 连接，接收实时遥测数据推送
 *
 * 使用方式：
 * const { telemetry, connected, connect, close } = useWebSocket();
 * telemetry 是一个响应式 ref，当后端推送数据时自动更新
 */
export function useWebSocket() {
  // 遥测数据（响应式 ref，数据变化时 Vue 自动更新 DOM）
  const telemetry = ref({
    latitude: 0,
    longitude: 0,
    altitude: 0,
    battery: 100,
    in_air: false
  });

  // WebSocket 连接状态
  const connected = ref(false);

  // WebSocket 实例
  let ws = null;

  // 心跳定时器 ID（用于 keep-alive）
  let pingInterval = null;

  /**
   * 建立 WebSocket 连接
   * 连接到后端 ws://localhost:5173/ws/telemetry（经 Vite 代理到 Spring Boot）
   */
  function connect() {
    // 如果已有连接先关闭
    close();

    // 创建 WebSocket（使用当前 host:port，Vite 代理会转发到后端）
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    ws = new WebSocket(`${protocol}//${window.location.host}/ws/telemetry`);

    ws.onopen = () => {
      connected.value = true;                           // 更新连接状态为已连接
      console.log('WebSocket 已连接');

      // 每 30 秒发送一次心跳，保持连接
      pingInterval = setInterval(() => {
        if (ws && ws.readyState === WebSocket.OPEN) {
          ws.send('ping');
        }
      }, 30000);
    };

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);            // 解析后端推送的 JSON 遥测数据
        telemetry.value = data;                         // 更新响应式数据，触发 UI 刷新
      } catch (e) {
        console.warn('解析遥测数据失败:', e);
      }
    };

    ws.onclose = () => {
      connected.value = false;                          // 更新连接状态为断开
      clearInterval(pingInterval);                      // 清除心跳定时器
      console.log('WebSocket 已断开，5 秒后重连...');
      setTimeout(connect, 5000);                         // 5 秒后自动重连
    };

    ws.onerror = (err) => {
      console.error('WebSocket 错误:', err);
    };
  }

  /**
   * 关闭 WebSocket 连接
   */
  function close() {
    clearInterval(pingInterval);                        // 清除心跳定时器
    if (ws) {
      ws.close();                                       // 关闭连接
      ws = null;
    }
  }

  // 组件卸载时自动关闭连接
  onUnmounted(() => {
    close();
  });

  // 暴露给外部使用
  return { telemetry, connected, connect, close };
}
