import { useEffect } from 'react'
import { websocketService } from '../services/websocketService'

/**
 * Subscribes `onUpdate` to live JobExecution broadcasts for the lifetime of
 * the calling component. Each update is a JobExecutionResponse - callers
 * typically use it to patch a job's status in place or prepend to a feed.
 */
export function useWebSocket(onUpdate) {
  useEffect(() => {
    const unsubscribe = websocketService.onExecutionUpdate(onUpdate)
    return unsubscribe
  }, [onUpdate])
}
