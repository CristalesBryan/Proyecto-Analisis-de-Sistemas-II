const tipos = [
  {
    nombre: 'Queja',
    texto: 'Manifestación de insatisfacción por un servicio municipal.',
  },
  {
    nombre: 'Reclamo',
    texto: 'Solicitud formal ante un derecho no atendido o mal ejecutado.',
  },
  {
    nombre: 'Denuncia',
    texto: 'Reporte de irregularidades o hechos que requieren investigación.',
  },
  {
    nombre: 'Sugerencia',
    texto: 'Propuesta ciudadana para mejorar servicios o procesos.',
  },
]

const pasos = [
  'Registra tu caso y obtén un código de seguimiento de 10 caracteres.',
  'El sistema asigna y gestiona el caso según plazos institucionales.',
  'Consulta el estado en cualquier momento con tu código.',
  'Recibe la resolución y el cierre del caso por los canales configurados.',
]

export function InfoSection() {
  return (
    <section id="informacion" className="border-t border-white/10 bg-zinc-950 px-6 py-20 md:px-12 lg:px-16">
      <div className="mx-auto max-w-6xl">
        <h2 className="mb-3 text-3xl font-medium tracking-tight md:text-4xl">
          Qué es el sistema QRDS
        </h2>
        <p className="mb-12 max-w-3xl text-gray-300">
          El Portal Público del Sistema de Quejas, Reclamos, Denuncias y Sugerencias
          es la fachada institucional digital de la municipalidad: un punto de acceso
          centralizado, disponible 24/7, para que cualquier ciudadano interactúe con
          la institución sin barreras.
        </p>

        <div className="mb-16 grid gap-8 md:grid-cols-2 lg:grid-cols-4">
          {tipos.map((tipo) => (
            <div key={tipo.nombre}>
              <h3 className="mb-2 text-lg font-medium">{tipo.nombre}</h3>
              <p className="text-sm leading-relaxed text-gray-300">{tipo.texto}</p>
            </div>
          ))}
        </div>

        <h3 className="mb-6 text-2xl font-medium tracking-tight">Cómo funciona</h3>
        <ol className="grid gap-4 md:grid-cols-2">
          {pasos.map((paso, index) => (
            <li
              key={paso}
              className="flex gap-4 border-l border-white/20 pl-4 text-sm leading-relaxed text-gray-300"
            >
              <span className="font-medium text-white">0{index + 1}</span>
              <span>{paso}</span>
            </li>
          ))}
        </ol>
      </div>
    </section>
  )
}
