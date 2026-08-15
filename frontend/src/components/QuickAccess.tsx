import { Link } from 'react-router-dom'
import { ClipboardList, Search, LogIn } from 'lucide-react'

const accesos = [
  {
    title: 'Registrar Queja / Reclamo / Denuncia / Sugerencia',
    description:
      'Envía tu caso de forma pública, sin necesidad de iniciar sesión. Recibirás un código de seguimiento.',
    href: '/registro-caso',
    icon: ClipboardList,
    cta: 'Registrar ahora',
  },
  {
    title: 'Consultar Estado de Mi Caso',
    description:
      'Ingresa tu código de seguimiento (ej. Q-2026-00001) y conoce el estado actual de tu caso.',
    href: '#consultar',
    icon: Search,
    cta: 'Consultar caso',
    action: 'consultar' as const,
  },
  {
    title: 'Iniciar Sesión',
    description:
      'Acceso exclusivo para funcionarios y administradores del sistema municipal.',
    href: '/login',
    icon: LogIn,
    cta: 'Ir al login',
  },
]

type QuickAccessProps = {
  onConsultar: () => void
}

export function QuickAccess({ onConsultar }: QuickAccessProps) {
  return (
    <section id="accesos" className="bg-black px-6 py-20 md:px-12 lg:px-16">
      <div className="mx-auto max-w-6xl">
        <h2 className="mb-3 text-3xl font-medium tracking-tight md:text-4xl">
          Accesos rápidos
        </h2>
        <p className="mb-12 max-w-2xl text-gray-300">
          Elige cómo deseas interactuar con el portal. El acceso informativo y las
          gestiones ciudadanas no requieren autenticación.
        </p>

        <div className="grid gap-6 md:grid-cols-3">
          {accesos.map((item) => {
            const Icon = item.icon
            const content = (
              <>
                <div className="mb-5 inline-flex rounded-lg border border-white/20 p-3">
                  <Icon className="h-5 w-5" aria-hidden="true" />
                </div>
                <h3 className="mb-3 text-lg font-medium leading-snug">{item.title}</h3>
                <p className="mb-6 flex-1 text-sm leading-relaxed text-gray-300">
                  {item.description}
                </p>
                <span className="text-sm font-medium text-white underline-offset-4 group-hover:underline">
                  {item.cta}
                </span>
              </>
            )

            if (item.action === 'consultar') {
              return (
                <button
                  key={item.title}
                  type="button"
                  onClick={onConsultar}
                  className="group liquid-glass flex h-full flex-col rounded-xl border border-white/20 p-6 text-left transition-colors hover:border-white/40"
                >
                  {content}
                </button>
              )
            }

            return (
              <Link
                key={item.title}
                to={item.href}
                className="group liquid-glass flex h-full flex-col rounded-xl border border-white/20 p-6 transition-colors hover:border-white/40"
              >
                {content}
              </Link>
            )
          })}
        </div>
      </div>
    </section>
  )
}
