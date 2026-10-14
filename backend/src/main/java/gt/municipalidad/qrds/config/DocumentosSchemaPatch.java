package gt.municipalidad.qrds.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Completa columnas de CU-12 cuando la base ya fue creada sin la baja lógica
 * ni el vínculo a seguimiento o resolución.
 */
@Component
public class DocumentosSchemaPatch implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DocumentosSchemaPatch.class);

    private final DataSource dataSource;

    public DocumentosSchemaPatch(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            String producto = connection.getMetaData().getDatabaseProductName();
            if (producto == null || !producto.toLowerCase().contains("postgres")) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE documentos_caso ADD COLUMN IF NOT EXISTS activo BOOLEAN DEFAULT TRUE");
                statement.execute("UPDATE documentos_caso SET activo = TRUE WHERE activo IS NULL");
                statement.execute("ALTER TABLE documentos_caso ADD COLUMN IF NOT EXISTS motivo_baja TEXT");
                statement.execute("ALTER TABLE documentos_caso ADD COLUMN IF NOT EXISTS baja_por_id BIGINT");
                statement.execute("ALTER TABLE documentos_caso ADD COLUMN IF NOT EXISTS baja_en TIMESTAMPTZ");
                statement.execute("ALTER TABLE documentos_caso ADD COLUMN IF NOT EXISTS seguimiento_id BIGINT");
                statement.execute("ALTER TABLE documentos_caso ADD COLUMN IF NOT EXISTS resolucion_id BIGINT");
            }
        } catch (SQLException ex) {
            log.warn("Ajuste de documentos omitido: {}", ex.getMessage());
        }
    }
}
