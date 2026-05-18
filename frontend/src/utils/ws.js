/**
 * WebSocket (STOMP over SockJS) 客户端封装
 * 用法:
 *   import { useWS } from '@/utils/ws'
 *   const ws = useWS()
 *   ws.connect().then(() => ws.subscribe('/topic/alerts', msg => ...))
 */
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs.min.js'

let client = null
let connecting = null
const subscribers = new Map() // topic -> [{id, cb}]

function buildClient() {
  const wsUrl = import.meta.env.VITE_WS_URL || '/ws'
  // 如果走代理: SockJS 需要绝对路径; 当 VITE_WS_URL 以 / 开头时拼当前 origin
  const url = wsUrl.startsWith('http') || wsUrl.startsWith('ws')
    ? wsUrl
    : `${window.location.origin}${wsUrl}`
  const c = new Client({
    webSocketFactory: () => new SockJS(url),
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000
  })
  return c
}

export function useWS() {
  function connect() {
    if (client && client.connected) return Promise.resolve()
    if (connecting) return connecting
    client = buildClient()
    connecting = new Promise((resolve, reject) => {
      client.onConnect = () => {
        // 重连时重新订阅
        for (const [topic, list] of subscribers.entries()) {
          for (const item of list) {
            item._sub = client.subscribe(topic, (frame) => {
              try {
                const data = JSON.parse(frame.body)
                item.cb(data, frame)
              } catch (_) {
                item.cb(frame.body, frame)
              }
            })
          }
        }
        resolve()
      }
      client.onStompError = (err) => reject(err)
      client.activate()
    })
    return connecting
  }

  function subscribe(topic, cb) {
    if (!subscribers.has(topic)) subscribers.set(topic, [])
    const item = { id: Symbol('sub'), cb, _sub: null }
    subscribers.get(topic).push(item)
    if (client && client.connected) {
      item._sub = client.subscribe(topic, (frame) => {
        try {
          cb(JSON.parse(frame.body), frame)
        } catch (_) {
          cb(frame.body, frame)
        }
      })
    }
    return () => unsubscribe(topic, item.id)
  }

  function unsubscribe(topic, id) {
    const list = subscribers.get(topic) || []
    const idx = list.findIndex((x) => x.id === id)
    if (idx >= 0) {
      const [removed] = list.splice(idx, 1)
      if (removed._sub) removed._sub.unsubscribe()
    }
    if (list.length === 0) subscribers.delete(topic)
  }

  function disconnect() {
    if (client) {
      client.deactivate()
      client = null
      connecting = null
      subscribers.clear()
    }
  }

  return { connect, subscribe, disconnect }
}
