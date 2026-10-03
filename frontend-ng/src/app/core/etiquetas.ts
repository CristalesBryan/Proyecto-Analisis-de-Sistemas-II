export function etiquetaEstado(estado: string) {
  const etiquetas: Record<string, string> = {
    RECIBIDO: 'Recibido',
    EN_REVISION: 'En revisión',
    EN_PROCESO: 'En proceso',
    RESUELTO: 'Resuelto',
    CERRADO: 'Cerrado',
    ANULADO: 'Anulado',
  };
  return etiquetas[estado] || estado.split('_').join(' ');
}
