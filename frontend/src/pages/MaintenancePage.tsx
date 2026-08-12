export function MaintenancePage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-black px-6 text-center">
      <p className="mb-4 text-sm uppercase tracking-[0.2em] text-gray-300">QRDS</p>
      <h1 className="mb-4 text-3xl font-medium md:text-5xl" style={{ letterSpacing: '-0.04em' }}>
        El sistema está en mantenimiento.
      </h1>
      <p className="max-w-md text-gray-300">
        Intente más tarde. El administrador ha sido notificado automáticamente.
      </p>
    </div>
  )
}
