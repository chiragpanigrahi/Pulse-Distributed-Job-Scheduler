
import React, { createContext, useEffect, useMemo, useState } from 'react'
import { authService } from '../services/authService'
import { websocketService } from '../services/websocketService'

export const AuthContext = createContext(null)

function getStoredUser() {
  try {
    const raw = localStorage.getItem('pulse_user')
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function decodeToken(token) {
  try {
    const part = token.split('.')[1]
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/')
    return JSON.parse(atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')))
  } catch {
    return {}
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(getStoredUser)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    const token = localStorage.getItem('pulse_token')

    if (token && user) {
      try {
        websocketService.connect(token)
      } catch (e) {
        console.error('WebSocket connection failed:', e)
      }
    }

    return () => websocketService.disconnect()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const login = async (username, password) => {
    setLoading(true)
    setError(null)

    try {
      // Step 1: Authenticate with backend
      const data = await authService.login(username, password)

      if (!data?.token) {
        throw new Error('Backend did not return a JWT token')
      }

      // Step 2: Read user information from JWT
      const payload = decodeToken(data.token)

      const nextUser = {
        username: data.username || payload.sub || username,
        role: (data.role || payload.role || 'USER').replace(/^ROLE_/, '')
      }

      // Step 3: Save authentication
      localStorage.setItem('pulse_token', data.token)
      localStorage.setItem('pulse_user', JSON.stringify(nextUser))

      setUser(nextUser)

      // Step 4: Connect WebSocket separately
      try {
        websocketService.connect(data.token)
      } catch (wsError) {
        console.error('WebSocket error:', wsError)
      }

      console.log('Pulse login successful:', nextUser)

      return true

    } catch (e) {
      console.error('Pulse authentication error:', e)

      setError(
          e.response?.data?.message ||
          e.message ||
          'Login failed. Check browser console.'
      )

      return false

    } finally {
      setLoading(false)
    }
  }

  const logout = () => {
    localStorage.removeItem('pulse_token')
    localStorage.removeItem('pulse_user')

    websocketService.disconnect()
    setUser(null)
    setError(null)
  }

  const value = useMemo(
      () => ({ user, login, logout, loading, error }),
      [user, loading, error]
  )

  return (
      <AuthContext.Provider value={value}>
        {children}
      </AuthContext.Provider>
  )
}
