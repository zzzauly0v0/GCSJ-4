<template>
  <div class="agent-root">

    <!-- Header -->
    <header class="agent-head">
      <div class="head-left">
        <div class="ai-mark">✦</div>
        <div>
          <div class="head-title">灾害助手</div>
          <div class="head-sub">出行风险研判 · 历史灾害数据</div>
        </div>
      </div>
      <div class="head-status">
        <span class="status-ring" :class="{ pulse: busy }"></span>
        <span class="status-label">{{ busy ? '分析中' : '就绪' }}</span>
      </div>
    </header>

    <!-- Messages -->
    <div class="agent-body" ref="bodyRef">
      <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">

        <!-- AI：无气泡，直接文本流 -->
        <div v-if="m.role === 'assistant'" class="ai-msg" :class="{ streaming: m.streaming }">

          <div v-if="m.thinking" class="think-box">
            <button class="think-toggle" @click="m.thinkOpen = !m.thinkOpen">
              <span class="think-chevron" :class="{ open: m.thinkOpen }">›</span>
              思考过程
            </button>
            <div v-show="m.thinkOpen" class="think-body">{{ m.thinking }}</div>
          </div>

          <div v-if="m.tool" class="tool-row">
            <span class="tool-spinner"></span>
            <span class="tool-label">{{ m.tool }}</span>
          </div>

          <div v-if="m.content" class="ai-content" :class="{ err: m.error }" v-html="renderMd(m.content)"></div>

          <div v-if="m.streaming && !m.content && !m.tool" class="typing-dots">
            <span></span><span></span><span></span>
          </div>
        </div>

        <!-- 用户：浅色圆角气泡 -->
        <div v-else class="user-bubble">{{ m.content }}</div>

      </div>
    </div>

    <!-- 快捷提问 -->
    <div class="agent-quick">
      <button
        v-for="q in quickAsks" :key="q"
        class="quick-chip"
        :disabled="busy"
        @click="sendQuick(q)"
      >{{ q }}</button>
    </div>

    <!-- 输入区 -->
    <div class="agent-input">
      <el-input
        v-model="draft"
        type="textarea"
        :rows="2"
        resize="none"
        placeholder="例如：我 9 月想去九寨沟玩，安全吗？（Enter 发送）"
        @keydown.enter.exact.prevent="send"
        :disabled="busy"
      />
      <button
        class="send-btn"
        :class="{ loading: busy }"
        :disabled="busy || !draft.trim()"
        @click="send"
      >
        <span v-if="busy" class="send-spinner"></span>
        <span v-else class="send-arrow">↑</span>
      </button>
    </div>

  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { streamChat } from '@/api/agent'

const messages = ref([
  { role: 'assistant', content: '你好！告诉我你想去的地点和时间（如「我 9 月想去九寨沟玩」），我会结合当地历史灾害数据帮你研判出行风险。' },
])
const draft = ref('')
const busy = ref(false)
const bodyRef = ref(null)

const quickAsks = [
  '我 9 月想去九寨沟玩，安全吗？',
  '暑假去峨眉山要注意什么灾害？',
  '雨季去都江堰安全吗？',
]

function renderMd(text) {
  if (!text) return ''
  let s = text
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  s = s.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  s = s.replace(/(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)/g, '<em>$1</em>')
  s = s.replace(/`([^`\n]+)`/g, '<code class="icode">$1</code>')
  s = s.replace(/^#{1,3} (.+)$/gm, (_, t) => `<span class="md-h">${t}</span>`)
  s = s.replace(/^[-•] (.+)$/gm, '<li>$1</li>')
  s = s.replace(/(<li>.*<\/li>\n?)+/g, m => `<ul>${m}</ul>`)
  s = s.replace(/\n/g, '<br>')
  return s
}

async function scrollToBottom() {
  await nextTick()
  if (bodyRef.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight
}

function sendQuick(q) {
  if (busy.value) return
  draft.value = q
  send()
}

async function send() {
  const text = draft.value.trim()
  if (!text || busy.value) return
  draft.value = ''
  busy.value = true

  messages.value.push({ role: 'user', content: text })
  const assistant = {
    role: 'assistant', content: '', tool: '', streaming: true, error: false,
    thinking: '', thinkOpen: false,
  }
  messages.value.push(assistant)
  scrollToBottom()

  // 排除刚 push 的空 assistant 占位（数组最后一项），再过滤空消息。
  // 注意不能用 m === assistant 判断：Vue 的 reactive 数组返回 proxy，恒不等于原对象。
  const history = messages.value
    .slice(0, -1)
    .filter(m => m.content)
    .map(m => ({ role: m.role, content: m.content }))

  await streamChat(history, {
    onTool: (ev) => {
      if (assistant.content) {
        assistant.thinking += (assistant.thinking ? '\n' : '') + assistant.content
        assistant.content = ''
      }
      assistant.tool = ev.status
      scrollToBottom()
    },
    onToken: (t) => { assistant.tool = ''; assistant.content += t; scrollToBottom() },
    onDone: () => { assistant.streaming = false; assistant.tool = ''; busy.value = false },
    onError: (msg) => {
      assistant.streaming = false; assistant.tool = ''
      assistant.error = true
      assistant.content = assistant.content || msg
      busy.value = false
    },
  })
}
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&display=swap');

/* ── 根 ── */
.agent-root {
  font-family: 'Inter', system-ui, -apple-system, sans-serif;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #FAF9F5;
  border-radius: 12px;
  border: 1px solid #E5E4DF;
  overflow: hidden;
  color: #141413;
}

/* ── Header ── */
.agent-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 15px 20px 14px;
  background: #FAF9F5;
  border-bottom: 1px solid #E5E4DF;
  flex-shrink: 0;
}
.head-left   { display: flex; align-items: center; gap: 10px; }
.ai-mark {
  font-size: 18px;
  color: #D97757;
  line-height: 1;
  user-select: none;
}
.head-title  { font-size: 15px; font-weight: 600; color: #141413; letter-spacing: -.2px; }
.head-sub    { font-size: 11px; color: #6B6B6B; margin-top: 1px; }

.head-status { display: flex; align-items: center; gap: 6px; }
.status-ring {
  width: 7px; height: 7px; border-radius: 50%;
  background: #A8C5A0;
  flex-shrink: 0;
}
.status-ring.pulse {
  background: #D97757;
  animation: status-pulse 1.6s ease-in-out infinite;
}
@keyframes status-pulse {
  0%, 100% { opacity: 1; }
  50%       { opacity: .35; }
}
.status-label { font-size: 12px; color: #6B6B6B; }

/* ── Body ── */
.agent-body {
  flex: 1;
  overflow-y: auto;
  padding: 28px 24px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  scroll-behavior: smooth;
}
.agent-body::-webkit-scrollbar       { width: 3px; }
.agent-body::-webkit-scrollbar-thumb { background: #D9D8D3; border-radius: 3px; }

/* ── Message rows ── */
.msg-row        { display: flex; }
.msg-row.user   { justify-content: flex-end; }

/* ── AI 消息（无气泡） ── */
.ai-msg {
  max-width: 82%;
  font-size: 14.5px;
  line-height: 1.75;
  color: #141413;
}

.ai-content { white-space: pre-wrap; word-break: break-word; }
.ai-content.err { color: #B54A2F; }

/* Markdown */
.ai-content :deep(strong) { font-weight: 600; }
.ai-content :deep(em)     { font-style: italic; color: #3A3A38; }
.ai-content :deep(.icode) {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12.5px;
  padding: 1px 5px;
  background: #F0EDE6;
  color: #C2622A;
  border-radius: 4px;
}
.ai-content :deep(.md-h) {
  display: block;
  font-weight: 600;
  font-size: 15px;
  color: #141413;
  margin: 10px 0 4px;
}
.ai-content :deep(ul) {
  margin: 8px 0;
  padding-left: 18px;
  list-style-type: disc;
}
.ai-content :deep(li) { margin: 4px 0; }

/* 流式光标 */
.ai-msg.streaming .ai-content::after {
  content: '';
  display: inline-block;
  width: 2px;
  height: 0.85em;
  background: #D97757;
  border-radius: 1px;
  margin-left: 2px;
  vertical-align: text-bottom;
  animation: blink-cursor .9s step-end infinite;
}
@keyframes blink-cursor {
  0%, 100% { opacity: 1; }
  50%       { opacity: 0; }
}

/* ── 用户气泡 ── */
.user-bubble {
  max-width: 72%;
  padding: 11px 16px;
  background: #EEECE4;
  border-radius: 12px 12px 3px 12px;
  font-size: 14.5px;
  line-height: 1.65;
  color: #141413;
  word-break: break-word;
  white-space: pre-wrap;
}

/* ── 工具调用 ── */
.tool-row {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 10px;
}
.tool-spinner {
  width: 13px; height: 13px;
  border: 1.5px solid #E5E4DF;
  border-top-color: #D97757;
  border-radius: 50%;
  animation: spin .8s linear infinite;
  flex-shrink: 0;
}
@keyframes spin { to { transform: rotate(360deg); } }
.tool-label { font-size: 12px; color: #6B6B6B; }

/* ── 思考过程 ── */
.think-box   { margin-bottom: 10px; }
.think-toggle {
  display: inline-flex; align-items: center; gap: 4px;
  background: none; border: none; cursor: pointer;
  font-size: 12px; color: #9A9A98; padding: 0;
  font-family: inherit;
  transition: color .15s;
}
.think-toggle:hover { color: #6B6B6B; }
.think-chevron {
  display: inline-block;
  font-size: 14px; line-height: 1;
  transition: transform .2s ease-out;
}
.think-chevron.open { transform: rotate(90deg); }
.think-body {
  margin-top: 8px; padding: 10px 14px;
  background: #F4F2EC;
  border-left: 2px solid #E5E4DF;
  border-radius: 0 6px 6px 0;
  font-size: 12.5px; color: #9A9A98;
  line-height: 1.6;
  white-space: pre-wrap; word-break: break-word;
}

/* ── 打字动效 ── */
.typing-dots {
  display: flex; align-items: center; gap: 4px;
  padding: 6px 0 2px;
}
.typing-dots span {
  width: 5px; height: 5px;
  background: #C4C2BA;
  border-radius: 50%;
  animation: dot-rise 1.3s ease-in-out infinite;
}
.typing-dots span:nth-child(2) { animation-delay: .16s; }
.typing-dots span:nth-child(3) { animation-delay: .32s; }
@keyframes dot-rise {
  0%, 70%, 100% { transform: translateY(0); opacity: .5; }
  35%           { transform: translateY(-5px); opacity: 1; }
}

/* ── 快捷提问 ── */
.agent-quick {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 10px 20px 12px;
  border-top: 1px solid #E5E4DF;
}
.quick-chip {
  padding: 5px 13px;
  font-size: 12.5px;
  font-family: inherit;
  border: 1px solid #E5E4DF;
  border-radius: 999px;
  background: transparent;
  color: #6B6B6B;
  cursor: pointer;
  transition: background .15s, color .15s, border-color .15s;
  white-space: nowrap;
}
.quick-chip:not(:disabled):hover {
  background: rgba(217, 119, 87, .06);
  border-color: rgba(217, 119, 87, .4);
  color: #C2622A;
}
.quick-chip:disabled { opacity: .4; cursor: not-allowed; }

/* ── 输入区 ── */
.agent-input {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 12px 20px 16px;
  background: #FAF9F5;
  border-top: 1px solid #E5E4DF;
}
.agent-input :deep(.el-textarea) { flex: 1; }
.agent-input :deep(.el-textarea__inner) {
  font-family: 'Inter', system-ui, sans-serif;
  font-size: 13.5px;
  line-height: 1.6;
  background: #F4F2EC;
  border: 1px solid #E5E4DF;
  border-radius: 10px;
  color: #141413;
  resize: none;
  box-shadow: none;
  transition: border-color .2s;
  padding: 10px 14px;
}
.agent-input :deep(.el-textarea__inner::placeholder) { color: #B0AEA8; }
.agent-input :deep(.el-textarea__inner:focus) {
  border-color: #C4BDB6;
  box-shadow: none;
  outline: none;
}

.send-btn {
  flex-shrink: 0;
  width: 40px; height: 40px;
  border-radius: 10px;
  border: none;
  background: #D97757;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: background .15s, opacity .15s;
  align-self: flex-end;
  margin-bottom: 3px;
}
.send-btn:not(:disabled):hover  { background: #C2622A; }
.send-btn:disabled               { opacity: .35; cursor: not-allowed; }

.send-arrow { line-height: 1; }
.send-spinner {
  width: 14px; height: 14px;
  border: 2px solid rgba(255,255,255,.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin .7s linear infinite;
}
</style>
