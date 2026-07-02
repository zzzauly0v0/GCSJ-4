<template>
  <div class="agent-root">
    <header class="agent-head">
      <div class="agent-title">
        <el-icon :size="20"><ChatDotRound /></el-icon>
        <span>AI 灾害助手</span>
        <span class="agent-sub">· 出行风险研判</span>
      </div>
    </header>

    <div class="agent-body" ref="bodyRef">
      <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
        <div class="bubble" :class="{ err: m.error }">
          <div v-if="m.thinking" class="think-box">
            <button class="think-toggle" @click="m.thinkOpen = !m.thinkOpen">
              <span>{{ m.thinkOpen ? '▾' : '▸' }} 思考过程</span>
            </button>
            <div v-show="m.thinkOpen" class="think-content" v-text="m.thinking"></div>
          </div>
          <span v-if="m.tool" class="tool-hint">🔧 {{ m.tool }}…</span>
          <span class="content" v-text="m.content"></span>
          <span v-if="m.role === 'assistant' && m.streaming && !m.content && !m.tool" class="typing">思考中…</span>
        </div>
      </div>
    </div>

    <div class="agent-quick">
      <button v-for="q in quickAsks" :key="q" class="quick-chip" @click="sendQuick(q)">{{ q }}</button>
    </div>

    <div class="agent-input">
      <el-input
        v-model="draft"
        type="textarea"
        :rows="2"
        resize="none"
        placeholder="例如: 我 9 月想去九寨沟玩, 安全吗?"
        @keydown.enter.exact.prevent="send"
        :disabled="busy"
      />
      <el-button type="primary" :loading="busy" @click="send">发送</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { ChatDotRound } from '@element-plus/icons-vue'
import { streamChat } from '@/api/agent'

const messages = ref([
  { role: 'assistant', content: '你好! 告诉我你想去的地点和时间 (如「我 9 月想去九寨沟玩」), 我会结合当地历史灾害数据帮你研判出行风险。' },
])
const draft = ref('')
const busy = ref(false)
const bodyRef = ref(null)

const quickAsks = [
  '我 9 月想去九寨沟玩, 安全吗?',
  '暑假去峨眉山要注意什么灾害?',
  '雨季去都江堰安全吗?',
]

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

  // 只把 user/assistant 的实质对话发给后端 (去掉本地 UI 字段)
  const history = messages.value
    .filter(m => m.content || m === assistant)
    .slice(0, -1)  // 不含刚 push 的空 assistant
    .map(m => ({ role: m.role, content: m.content }))
    .filter(m => m.content)

  await streamChat(history, {
    onTool: (ev) => {
      // 工具调用前累积的正文其实是模型的思考/规划 (含裸 JSON 参数), 收进折叠区,
      // 只保留最后一次工具调用之后的文本作为最终研判。
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
      assistant.streaming = false
      assistant.tool = ''
      assistant.error = true
      assistant.content = assistant.content || msg
      busy.value = false
    },
  })
}
</script>

<style scoped>
.agent-root {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--au-bg-surface, #fff);
  border-radius: var(--au-radius-lg, 12px);
  border: 1px solid var(--au-border-subtle, #E5E7EB);
  overflow: hidden;
}
.agent-head {
  padding: 14px 18px;
  border-bottom: 1px solid var(--au-border-subtle, #E5E7EB);
}
.agent-title {
  display: flex; align-items: center; gap: 8px;
  font-size: 16px; font-weight: 700; color: #1E293B;
}
.agent-sub { font-size: 12px; font-weight: 400; color: #94A3B8; }

.agent-body {
  flex: 1; overflow-y: auto; padding: 18px;
  display: flex; flex-direction: column; gap: 14px;
}
.msg { display: flex; }
.msg.user { justify-content: flex-end; }
.msg.assistant { justify-content: flex-start; }
.bubble {
  max-width: 76%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px; line-height: 1.6;
  white-space: pre-wrap; word-break: break-word;
}
.msg.user .bubble {
  background: linear-gradient(135deg, #2563EB, #06B6D4);
  color: #fff;
}
.msg.assistant .bubble {
  background: #F1F5F9; color: #1E293B;
}
.bubble.err { background: #FEF2F2; color: #DC2626; }
.tool-hint { display: block; font-size: 12px; color: #64748B; margin-bottom: 2px; }
.typing { color: #94A3B8; }

.think-box { margin-bottom: 6px; }
.think-toggle {
  border: none; background: transparent; cursor: pointer;
  font-size: 12px; color: #94A3B8; padding: 0;
}
.think-toggle:hover { color: #64748B; }
.think-content {
  margin-top: 4px; padding: 8px 10px;
  background: #F8FAFC; border: 1px dashed #E2E8F0; border-radius: 8px;
  font-size: 12px; color: #94A3B8; line-height: 1.5;
  white-space: pre-wrap; word-break: break-word;
}

.agent-quick {
  display: flex; gap: 8px; flex-wrap: wrap;
  padding: 0 18px 8px;
}
.quick-chip {
  padding: 5px 12px; font-size: 12px;
  border: 1px solid #E5E7EB; border-radius: 999px;
  background: #fff; color: #475569; cursor: pointer;
  transition: all .15s;
}
.quick-chip:hover { background: #EFF6FF; border-color: #93C5FD; color: #1D4ED8; }

.agent-input {
  display: flex; gap: 10px; align-items: flex-end;
  padding: 12px 18px 16px;
  border-top: 1px solid var(--au-border-subtle, #E5E7EB);
}
.agent-input :deep(.el-textarea) { flex: 1; }
</style>
