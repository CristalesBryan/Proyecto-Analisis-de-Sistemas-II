import { MapPin, Phone, Mail, Clock } from 'lucide-react'

const contactos = [
  {
    icon: MapPin,
    label: 'Dirección',
    value: 'Palacio Municipal, Centro Cívico, Ciudad',
  },
  {
    icon: Phone,
    label: 'Teléfono',
    value: '(502) 2222-0000',
  },
  {
    icon: Mail,
    label: 'Correo',
    value: 'qrds@municipalidad.gob.gt',
  },
  {
    icon: Clock,
    label: 'Horario presencial',
    value: 'Lunes a viernes, 8:00 – 16:00',
  },
]

export function ContactSection() {
  return (
    <section id="contacto" className="bg-black px-6 py-20 md:px-12 lg:px-16">
      <div className="mx-auto max-w-6xl">
        <h2 className="mb-3 text-3xl font-medium tracking-tight md:text-4xl">
          Contacto y canales de atención
        </h2>
        <p className="mb-12 max-w-2xl text-gray-300">
          Además del portal digital, puedes acercarte a los canales institucionales
          de atención presencial y remota.
        </p>

        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {contactos.map((item) => {
            const Icon = item.icon
            return (
              <div key={item.label} className="border-t border-white/20 pt-5">
                <div className="mb-3 flex items-center gap-2 text-sm text-gray-300">
                  <Icon className="h-4 w-4" aria-hidden="true" />
                  {item.label}
                </div>
                <p className="text-base font-medium">{item.value}</p>
              </div>
            )
          })}
        </div>
      </div>
    </section>
  )
}
