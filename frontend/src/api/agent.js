/**
 * AI 灾害助手 SSE 流式客户端。
 *
 * 后端 (Python FastAPI :8000, 经 Vite proxy /ai) POST /ai/chat 返回 text/event-stream,
 * 每帧 `data: {json}\n\n`, json.type ∈ {tool, token, done, error}。
 * 用原生 fetch + ReadableStream 读取 (axios 不适合流式)。
 */

/**
 * @param {Array<{role:string,content:string}>} messages 完整对话历史
 * @param {object} cb { onTool, onToken, onDone, onError, signal }
 */
export async function streamChat(messages, { onTool, onToken, onDone, onError, signal } = {}) {
  try {
    const resp = await fetch('/ai/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ messages }),
      signal,
    })
    if (!resp.ok || !resp.body) {
      onError?.(`AI 服务暂不可用 (HTTP ${resp.status})`)
      return
    }

    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buf = ''

    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })

      // 按 SSE 帧分隔 (\n\n) 切分
      let idx
      while ((idx = buf.indexOf('\n\n')) !== -1) {
        const frame = buf.slice(0, idx).trim()
        buf = buf.slice(idx + 2)
        if (!frame.startsWith('data:')) continue
        const jsonStr = frame.slice(5).trim()
        if (!jsonStr) continue
        let ev
        try { ev = JSON.parse(jsonStr) } catch { continue }
        if (ev.type === 'tool') onTool?.(ev)
        else if (ev.type === 'token') onToken?.(ev.text || '')
        else if (ev.type === 'done') onDone?.()
        else if (ev.type === 'error') onError?.(ev.message || 'AI 生成失败')
      }
    }
    onDone?.()
  } catch (e) {
    if (e?.name === 'AbortError') return
    onError?.('AI 服务暂不可用')
  }
}
