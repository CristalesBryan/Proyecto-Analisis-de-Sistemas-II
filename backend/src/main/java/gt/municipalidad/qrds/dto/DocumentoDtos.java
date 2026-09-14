package gt.municipalidad.qrds.dto;

import gt.municipalidad.qrds.entity.DocumentoCaso;
import java.time.Instant;
import java.util.List;

public final class DocumentoDtos {

    private DocumentoDtos() {
    }

    public record DocumentoResumen(
            Long id,
            String nombreArchivo,
            String tipoMime,
            long tamanioBytes,
            Instant subidoEn,
            String origen) {

        public static DocumentoResumen de(DocumentoCaso documento) {
            return new DocumentoResumen(
                    documento.getId(),
                    documento.getNombreArchivo(),
                    documento.getTipoMime(),
                    documento.getTamanioBytes(),
                    documento.getSubidoEn(),
                    documento.getOrigen().name());
        }
    }

    public record ArchivoSubido(Long id, String nombreArchivo) {
        public static ArchivoSubido de(DocumentoCaso documento) {
            return new ArchivoSubido(documento.getId(), documento.getNombreArchivo());
        }
    }

    public record ArchivoRechazado(String nombre, String motivo) {
    }

    public record CargaDocumentosRespuesta(
            List<ArchivoSubido> archivosSubidos,
            List<ArchivoRechazado> rechazados) {
    }
}
