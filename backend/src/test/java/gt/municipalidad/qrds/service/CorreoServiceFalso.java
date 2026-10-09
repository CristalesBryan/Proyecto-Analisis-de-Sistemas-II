package gt.municipalidad.qrds.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("test")
public class CorreoServiceFalso extends CorreoService {

    private static final Logger log = LoggerFactory.getLogger(CorreoServiceFalso.class);

    public CorreoServiceFalso() {
        super();
    }

    @Override
    public void enviar(String para, String asunto, String texto) {
        registrar(para, asunto);
    }

    @Override
    public void enviarInmediato(String para, String asunto, String texto) {
        registrar(para, asunto);
    }

    private void registrar(String para, String asunto) {
        if (para == null || para.isBlank()) {
            return;
        }
        log.info("Correo de prueba registrado para {} (asunto: {})", para.trim(), asunto);
    }
}
