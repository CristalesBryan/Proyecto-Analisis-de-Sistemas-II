export function Footer() {
  return (
    <footer className="border-t border-white/10 bg-zinc-950 px-6 py-10 md:px-12 lg:px-16">
      <div className="mx-auto flex max-w-6xl flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div>
          <p className="text-sm font-medium">QRDS Municipal</p>
          <p className="mt-1 text-sm text-gray-300">
            © {new Date().getFullYear()} Municipalidad. Todos los derechos reservados.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-6 text-sm text-gray-300">
          <span>Versión del sistema 1.0</span>
          <a href="#privacidad" className="transition-colors hover:text-white">
            Política de privacidad
          </a>
        </div>
      </div>
    </footer>
  )
}
