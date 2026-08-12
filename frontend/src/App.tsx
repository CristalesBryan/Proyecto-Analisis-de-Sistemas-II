import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { PublicPortal } from './pages/PublicPortal'
import { LoginPage } from './pages/LoginPage'
import { RegistroCasoPage } from './pages/RegistroCasoPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<PublicPortal />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/registro-caso" element={<RegistroCasoPage />} />
      </Routes>
    </BrowserRouter>
  )
}
