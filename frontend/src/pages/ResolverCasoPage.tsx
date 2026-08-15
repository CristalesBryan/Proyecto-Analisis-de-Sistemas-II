import { Link, useParams } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { InternalLayout } from '../components/InternalLayout'

export function ResolverCasoPage() {
  const { id } = useParams()

  return (
    <InternalLayout>
      <Link
        to={`/casos/${id}/seguimiento`}
        className="mb-6 inline-flex items-center gap-2 text-sm text-gray-300 hover:text-white"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        Volver al seguimiento
      </Link>
      <div className="liquid-glass max-w-xl rounded-xl border border-white/20 p-6">
        <h1 className="text-3xl font-normal tracking-[-0.04em]">Resolver caso</h1>
        <p className="mt-4 text-sm leading-relaxed text-gray-300">
          El 100% de avance no cierra el expediente. La resolución formal, el dictamen y el cierre
          se implementarán en el CU-05.
        </p>
      </div>
    </InternalLayout>
  )
}
