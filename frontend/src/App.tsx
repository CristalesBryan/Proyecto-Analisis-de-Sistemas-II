import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './components/ProtectedRoute'
import { AccesoDenegadoPage } from './pages/AccesoDenegadoPage'
import { BandejaCasosPage } from './pages/BandejaCasosPage'
import { DashboardPage } from './pages/DashboardPage'
import { DetalleCasoPage } from './pages/DetalleCasoPage'
import { LoginPage } from './pages/LoginPage'
import { PublicPortal } from './pages/PublicPortal'
import { RegistroCasoPage } from './pages/RegistroCasoPage'
import { RegistroCiudadanoPage } from './pages/RegistroCiudadanoPage'
import { CiudadanoCuentaPage } from './pages/CiudadanoCuentaPage'
import { ResolverCasoPage } from './pages/ResolverCasoPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<PublicPortal />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/registro-ciudadano" element={<RegistroCiudadanoPage />} />
        <Route
          path="/ciudadano"
          element={
            <ProtectedRoute rolRequerido="CIUDADANO">
              <CiudadanoCuentaPage />
            </ProtectedRoute>
          }
        />
        <Route path="/registro-caso" element={<RegistroCasoPage />} />
        <Route path="/acceso-denegado" element={<AccesoDenegadoPage />} />
        <Route
          path="/admin/dashboard"
          element={
            <ProtectedRoute rolRequerido="ADMIN">
              <DashboardPage rolRequerido="ADMIN" />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/casos"
          element={
            <ProtectedRoute rolRequerido="ADMIN">
              <BandejaCasosPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/supervisor/dashboard"
          element={
            <ProtectedRoute rolRequerido="SUPERVISOR">
              <DashboardPage rolRequerido="SUPERVISOR" />
            </ProtectedRoute>
          }
        />
        <Route
          path="/supervisor/casos"
          element={
            <ProtectedRoute rolRequerido="SUPERVISOR">
              <BandejaCasosPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/agente/casos"
          element={
            <ProtectedRoute rolRequerido="AGENTE">
              <BandejaCasosPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/casos/:id"
          element={
            <ProtectedRoute rolesPermitidos={['ADMIN', 'SUPERVISOR', 'AGENTE']}>
              <DetalleCasoPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/casos/:id/seguimiento"
          element={
            <ProtectedRoute rolesPermitidos={['ADMIN', 'SUPERVISOR', 'AGENTE']}>
              <DetalleCasoPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/casos/:id/resolver"
          element={
            <ProtectedRoute rolesPermitidos={['ADMIN', 'SUPERVISOR', 'AGENTE']}>
              <ResolverCasoPage />
            </ProtectedRoute>
          }
        />
        <Route path="/admin" element={<Navigate to="/admin/casos" replace />} />
        <Route path="/supervisor" element={<Navigate to="/supervisor/casos" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
