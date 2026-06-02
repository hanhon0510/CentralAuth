import type { ReactNode } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { AuthPage } from '../../features/auth/pages/AuthPage'
import { ForgotPasswordPage } from '../../features/auth/pages/ForgotPasswordPage'
import { ResetPasswordPage } from '../../features/auth/pages/ResetPasswordPage'
import { VerifyEmailPage } from '../../features/auth/pages/VerifyEmailPage'
import { DashboardPage } from '../../features/dashboard/pages/DashboardPage'
import { ProfilePage } from '../../features/profile/pages/ProfilePage'
import { ROUTES } from '../../shared/constants/routes'
import { useAuthSession } from '../../features/auth/context/useAuthSession'
import { demoClients } from '../../features/demo-clients/demoClients'
import { DemoClientCallbackPage } from '../../features/demo-clients/pages/DemoClientCallbackPage'
import { DemoClientLogoutPage } from '../../features/demo-clients/pages/DemoClientLogoutPage'
import { DemoClientProtectedPage } from '../../features/demo-clients/pages/DemoClientProtectedPage'
import { DemoClientPublicPage } from '../../features/demo-clients/pages/DemoClientPublicPage'

function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user, restoring } = useAuthSession()
  const location = useLocation()

  if (restoring) return null
  if (!user) {
    return (
      <Navigate
        to={ROUTES.signin}
        replace
        state={{
          authRequired: true,
          returnTo: `${location.pathname}${location.search}${location.hash}`,
        }}
      />
    )
  }
  return children
}

export function AppRouter() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to={ROUTES.signin} replace />} />
      <Route path={ROUTES.signin} element={<AuthPage mode="signin" />} />
      <Route path={ROUTES.signup} element={<AuthPage mode="signup" />} />
      <Route path={ROUTES.verifyEmail} element={<VerifyEmailPage />} />
      <Route path={ROUTES.forgotPassword} element={<ForgotPasswordPage />} />
      <Route path={ROUTES.resetPassword} element={<ResetPasswordPage />} />
      <Route
        path={ROUTES.dashboard}
        element={
          <ProtectedRoute>
            <DashboardPage />
          </ProtectedRoute>
        }
      />
      <Route
        path={ROUTES.profile}
        element={
          <ProtectedRoute>
            <ProfilePage />
          </ProtectedRoute>
        }
      />
      <Route path="/demo" element={<Navigate to={demoClients.projects.publicPath} replace />} />
      <Route
        path={demoClients.projects.publicPath}
        element={<DemoClientPublicPage client={demoClients.projects} />}
      />
      <Route
        path={demoClients.projects.protectedPath}
        element={<DemoClientProtectedPage client={demoClients.projects} />}
      />
      <Route
        path={demoClients.projects.callbackPath}
        element={<DemoClientCallbackPage client={demoClients.projects} />}
      />
      <Route
        path={demoClients.projects.logoutPath}
        element={<DemoClientLogoutPage client={demoClients.projects} />}
      />
      <Route
        path={demoClients.reports.publicPath}
        element={<DemoClientPublicPage client={demoClients.reports} />}
      />
      <Route
        path={demoClients.reports.protectedPath}
        element={<DemoClientProtectedPage client={demoClients.reports} />}
      />
      <Route
        path={demoClients.reports.callbackPath}
        element={<DemoClientCallbackPage client={demoClients.reports} />}
      />
      <Route
        path={demoClients.reports.logoutPath}
        element={<DemoClientLogoutPage client={demoClients.reports} />}
      />
      <Route path="*" element={<Navigate to={ROUTES.signin} replace />} />
    </Routes>
  )
}
