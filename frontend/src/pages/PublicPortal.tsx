import { useEffect, useState } from 'react'
import { HeroSection } from '../components/HeroSection'
import { QuickAccess } from '../components/QuickAccess'
import { InfoSection } from '../components/InfoSection'
import { ContactSection } from '../components/ContactSection'
import { Footer } from '../components/Footer'
import { ConsultaCasoModal } from '../components/ConsultaCasoModal'
import { MaintenancePage } from './MaintenancePage'
import { getSistemaEstado, registrarAccesoPublico } from '../services/api'

export function PublicPortal() {
  const [disponible, setDisponible] = useState<boolean | null>(null)
  const [modalOpen, setModalOpen] = useState(false)

  useEffect(() => {
    let cancelled = false

    async function boot() {
      try {
        const estado = await getSistemaEstado()
        if (cancelled) return
        if (estado.estado === 'UP') {
          setDisponible(true)
          await registrarAccesoPublico('ACCESO_PORTAL', 'OK')
        } else {
          setDisponible(false)
        }
      } catch {
        if (!cancelled) setDisponible(false)
      }
    }

    boot()
    return () => {
      cancelled = true
    }
  }, [])

  function openConsulta() {
    setModalOpen(true)
    void registrarAccesoPublico('CONSULTAR_CASO', 'OK')
  }

  if (disponible === null) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-black text-gray-300">
        Cargando portal…
      </div>
    )
  }

  if (!disponible) {
    return <MaintenancePage />
  }

  return (
    <main>
      <HeroSection onConsultar={openConsulta} />
      <QuickAccess onConsultar={openConsulta} />
      <InfoSection />
      <ContactSection />
      <Footer />
      <ConsultaCasoModal open={modalOpen} onClose={() => setModalOpen(false)} />
    </main>
  )
}
