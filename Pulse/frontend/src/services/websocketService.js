import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

/**
 * Thin wrapper around a single STOMP client. The backend's WebSocketConfig
 * rejects the CONNECT frame unless a valid "Authorization: Bearer <token>"
 * STOMP header is present, so we pass the JWT there rather than as a query
 * param.
 */
class WebSocketService {
  constructor() {
    this.client = null
    this.subscribers = new Set()
    this.connected = false
    this.statusListeners = new Set()
  }

  setConnected(value) {
    if (this.connected === value) return
    this.connected = value
    this.statusListeners.forEach((cb) => cb(value))
  }

  /** Notifies when the live connection comes up or drops. Returns an unsubscribe function. */
  onStatusChange(callback) {
    this.statusListeners.add(callback)
    return () => this.statusListeners.delete(callback)
  }

  connect(token) {
    if (this.client && this.client.active) return

    this.client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 4000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onWebSocketClose: () => this.setConnected(false),
      onConnect: () => {
        this.setConnected(true)
        this.client.subscribe('/topic/jobs', (message) => {
          try {
            const payload = JSON.parse(message.body)
            this.subscribers.forEach((cb) => cb(payload))
          } catch (e) {
            // ignore malformed frames
          }
        })
      },
    })

    this.client.activate()
  }

  disconnect() {
    if (this.client) {
      this.client.deactivate()
      this.client = null
      this.setConnected(false)
    }
  }

  onExecutionUpdate(callback) {
    this.subscribers.add(callback)
    return () => this.subscribers.delete(callback)
  }
}

export const websocketService = new WebSocketService()
