package apigenerica.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuracion global de la aplicacion.
 * Todos los valores se leen desde db.properties (en el classpath).
 *
 * @author Grupo1
 */
public class AppConfig {

    private static final Properties PROPS = new Properties();
    public static String AES_KEY;

    static {
        try (InputStream is = AppConfig.class.getClassLoader()
                .getResourceAsStream("db.properties")) {
            if (is == null) {
                throw new RuntimeException(
                    "[AppConfig] No se encontro db.properties en el classpath. " +
                    "Coloca el archivo en src/ antes de compilar.");
            }
            PROPS.load(is);
        } catch (IOException e) {
            throw new RuntimeException("[AppConfig] Error leyendo db.properties: " + e.getMessage(), e);
        }
    }

    // ── Bases de datos ──────────────────────────────────────────────
    public static final String DB_SISTEMA  = PROPS.getProperty("db.nombre_sistema", "erp_sistema");
    public static final String DB_CLIENTE  = PROPS.getProperty("db.nombre_cliente", "erp_empresa");

    // ── Conexion MySQL ──────────────────────────────────────────────
    public static final String DB_HOST     = PROPS.getProperty("db.host",    "localhost");
    public static final String DB_PORT     = PROPS.getProperty("db.port",    "3306");
    public static final String DB_USUARIO  = PROPS.getProperty("db.usuario", "root");
    public static final String DB_PASSWORD = PROPS.getProperty("db.password", "");

    // ── Seguridad ───────────────────────────────────────────────────
    private static final String SECRET_KEY = PROPS.getProperty("app.secret_key", "clave_secreta_de_prueba");

    public static String getSecretKey() {
        return SECRET_KEY;
    }
}
