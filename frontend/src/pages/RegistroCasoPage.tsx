import { Link } from 'react-router-dom'

export function RegistroCasoPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-black px-6 text-center">
      <h1 className="mb-3 text-3xl font-medium" style={{ letterSpacing: '-0.04em' }}>
        Registrar Queja
      </h1>
      <p className="mb-8 max-w-md text-gray-300">
        Formulario público CU-02. El registro completo de casos se implementará en el
        siguiente caso de uso.
      </p>
      <Link
        to="/"
        className="rounded-lg bg-white px-6 py-3 text-sm font-medium text-black hover:bg-gray-100"
      >
        Volver al portal
      </Link>
    </div>
  )
}
