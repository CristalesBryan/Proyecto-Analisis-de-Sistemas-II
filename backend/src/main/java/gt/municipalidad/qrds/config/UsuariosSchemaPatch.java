package gt.municipalidad.qrds.config;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Ajusta el esquema H2 persistido antes de Hibernate: columnas nuevas con DEFAULT
 * y ENUMs convertidos a VARCHAR, para que valores nuevos (CIUDADANO, REGISTRO_CIUDADANO)
 * no fallen al insertar sobre una base ya creada.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UsuariosSchemaPatch implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(UsuariosSchemaPatch.class);

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource dataSource) {
            parchear(dataSource);
        }
        return bean;
    }

    private void parchear(DataSource dataSource) {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(
                    "ALTER TABLE IF EXISTS usuarios ADD COLUMN IF NOT EXISTS telefono VARCHAR(20)");
            statement.execute(
                    "ALTER TABLE IF EXISTS usuarios ADD COLUMN IF NOT EXISTS dpi VARCHAR(13)");
            statement.execute(
                    "ALTER TABLE IF EXISTS usuarios ADD COLUMN IF NOT EXISTS acepta_privacidad BOOLEAN DEFAULT FALSE NOT NULL");
            statement.execute(
                    "ALTER TABLE IF EXISTS usuarios ADD COLUMN IF NOT EXISTS email_verificado BOOLEAN DEFAULT FALSE NOT NULL");
            statement.execute("ALTER TABLE IF EXISTS usuarios ALTER COLUMN rol SET DATA TYPE VARCHAR(20)");
            statement.execute(
                    "ALTER TABLE IF EXISTS bitacora_accesos ALTER COLUMN tipo_evento SET DATA TYPE VARCHAR(30)");
            statement.execute(
                    "ALTER TABLE IF EXISTS bitacora_casos ALTER COLUMN tipo_evento SET DATA TYPE VARCHAR(40)");
            statement.execute("ALTER TABLE IF EXISTS casos ALTER COLUMN estado SET DATA TYPE VARCHAR(20)");
            statement.execute("ALTER TABLE IF EXISTS casos ALTER COLUMN prioridad SET DATA TYPE VARCHAR(10)");
            statement.execute("ALTER TABLE IF EXISTS casos ALTER COLUMN tipo_caso SET DATA TYPE VARCHAR(1)");
            statement.execute(
                    "ALTER TABLE IF EXISTS correlativos_caso ALTER COLUMN tipo_caso SET DATA TYPE VARCHAR(1)");
            statement.execute(
                    "ALTER TABLE IF EXISTS documentos_caso ALTER COLUMN origen SET DATA TYPE VARCHAR(20)");
            statement.execute(
                    "ALTER TABLE IF EXISTS seguimientos_caso ALTER COLUMN tipo SET DATA TYPE VARCHAR(20)");
        } catch (Exception ex) {
            log.warn("Parche de esquema H2 omitido: {}", ex.getMessage());
        }
    }
}
