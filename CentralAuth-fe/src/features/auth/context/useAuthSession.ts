import { useContext } from 'react'
import { AuthSessionStore } from './auth-session-store'

export function useAuthSession() {
  const context = useContext(AuthSessionStore)
  if (!context) {
    throw new Error('useAuthSession must be used inside AuthSessionProvider')
  }
  return context
}
