import { Navigate, Route, Routes } from 'react-router-dom'
import { AuthPage } from '../../features/auth/pages/AuthPage'
import { DashboardPage } from '../../features/dashboard/pages/DashboardPage'
import { ROUTES } from '../../shared/constants/routes'
import { useAuthSession } from '../../features/auth/context/useAuthSession'

function ProtectedDashboardRoute() {
  const { user, restoring } = useAuthSession()

  if (restoring) return null
  if (!user) return <Navigate to={ROUTES.signin} replace />
  return <DashboardPage />
}

export function AppRouter() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to={ROUTES.signin} replace />} />
      <Route path={ROUTES.signin} element={<AuthPage mode="signin" />} />
      <Route path={ROUTES.signup} element={<AuthPage mode="signup" />} />
      <Route path={ROUTES.dashboard} element={<ProtectedDashboardRoute />} />
      <Route path="*" element={<Navigate to={ROUTES.signin} replace />} />
    </Routes>
  )
}
