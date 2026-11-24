import { useEffect, useState } from 'react'
import { websocketService } from '../services/websocketService'

/** True while the live (WebSocket) feed is connected. */
export function useLiveStatus() {
  const [connected, setConnected] = useState(websocketService.connected)
  useEffect(() => websocketService.onStatusChange(setConnected), [])
  return connected
}
