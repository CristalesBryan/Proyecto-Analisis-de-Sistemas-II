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
 * Completa columnas de CU-09 cuando la base ya fue creada con el esquema anterior.
 */
@Component
public class ReportesSchemaPatch implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReportesSchemaPatch.class);

    private final DataSource dataSource;

    public ReportesSchemaPatch(DataSource dataSource) {
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
                statement.execute("ALTER TABLE reportes_generados ADD COLUMN IF NOT EXISTS filtros_hash VARCHAR(64)");
                statement.execute("ALTER TABLE reportes_generados ADD COLUMN IF NOT EXISTS cantidad_filas INT");
                statement.execute("ALTER TABLE reportes_generados ADD COLUMN IF NOT EXISTS vista_previa TEXT");
                statement.execute("ALTER TABLE reportes_generados ADD COLUMN IF NOT EXISTS expira_en TIMESTAMPTZ");
                statement.execute(
                        "ALTER TABLE reportes_generados ALTER COLUMN filtros_json TYPE text USING filtros_json::text");
                statement.execute(
                        "ALTER TABLE bitacora_sistema ALTER COLUMN metadata_json TYPE text USING metadata_json::text");
            }
        } catch (SQLException ex) {
            log.warn("Ajuste de reportes omitido: {}", ex.getMessage());
        }
    }
}
