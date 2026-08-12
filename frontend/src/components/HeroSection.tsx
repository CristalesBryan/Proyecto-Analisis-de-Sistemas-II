import { Link } from 'react-router-dom'
import { AnimatedHeading } from './AnimatedHeading'
import { FadeIn } from './FadeIn'
import { Navbar } from './Navbar'

type HeroSectionProps = {
  onConsultar: () => void
}

const VIDEO_URL =
  'https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260403_050628_c4e32401-fab4-4a27-b7a8-6e9291cd5959.mp4'

export function HeroSection({ onConsultar }: HeroSectionProps) {
  return (
    <section id="inicio" className="relative min-h-screen overflow-hidden bg-black">
      <video
        className="absolute inset-0 h-full w-full object-cover"
        src={VIDEO_URL}
        autoPlay
        loop
        muted
        playsInline
        aria-hidden="true"
      />

      <div className="relative z-10 flex min-h-screen flex-col">
        <Navbar onConsultar={onConsultar} />

        <div className="flex flex-1 flex-col justify-end px-6 pb-12 md:px-12 lg:px-16 lg:pb-16">
          <div className="lg:grid lg:grid-cols-2 lg:items-end">
            <div>
              <AnimatedHeading
                text={"Tu voz cuenta\nen nuestra ciudad."}
                className="mb-4 text-4xl font-normal md:text-5xl lg:text-6xl xl:text-7xl"
                style={{ letterSpacing: '-0.04em' }}
              />

              <FadeIn delay={800} duration={1000}>
                <p className="mb-5 text-base text-gray-300 md:text-lg">
                  Sistema municipal de Quejas, Reclamos, Denuncias y Sugerencias.
                  Registra tu caso o consulta su estado sin autenticación.
                </p>
              </FadeIn>

              <FadeIn delay={1200} duration={1000}>
                <div className="flex flex-wrap gap-4">
                  <Link
                    to="/registro-caso"
                    className="rounded-lg bg-white px-8 py-3 font-medium text-black transition-colors hover:bg-gray-100"
                  >
                    Registrar Queja
                  </Link>
                  <button
                    type="button"
                    onClick={onConsultar}
                    className="liquid-glass rounded-lg border border-white/20 px-8 py-3 font-medium text-white transition-colors hover:bg-white hover:text-black"
                  >
                    Consultar Mi Caso
                  </button>
                </div>
              </FadeIn>
            </div>

            <FadeIn
              delay={1400}
              duration={1000}
              className="mt-8 flex items-end justify-start lg:mt-0 lg:justify-end"
            >
              <div className="liquid-glass rounded-xl border border-white/20 px-6 py-3">
                <p className="text-lg font-light md:text-xl lg:text-2xl">
                  Quejas. Reclamos. Denuncias. Sugerencias.
                </p>
              </div>
            </FadeIn>
          </div>
        </div>
      </div>
    </section>
  )
}
