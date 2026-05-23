/*
Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
*/
package apigenerica.config;

/**
 * @author Grupo1
 */
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppConfig {

    private static final Properties PROPS = new Properties();

    static {
        try (InputStream is = AppConfig.class.getClassLoader()
                .getResourceAsStream("db.properties")) {
            if (is == null) {
                throw new RuntimeException(
                    "[AppConfig] No se encontro db.properties en el classpath.");
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
    public static final String DB_HOST     = PROPS.getProperty("db.host",     "localhost");
    public static final String DB_PORT     = PROPS.getProperty("db.port",     "3306");
    public static final String DB_USUARIO  = PROPS.getProperty("db.usuario",  "root");
    public static final String DB_PASSWORD = PROPS.getProperty("db.password", "");

    // ── Rutas relativas al proyecto ─────────────────────────────────
    public static final String DIR_BACKUPS          = PROPS.getProperty("dir.backups",          "./backups");
    public static final String DIR_DB4O_RAIZ        = PROPS.getProperty("dir.db4o_raiz",        "./storage");
    public static final String ARCHIVO_DB4O         = PROPS.getProperty("archivo.db4o",         "./ficheros.db4o");
    public static final String DIR_PARADOX          = PROPS.getProperty("dir.paradox",          "./base_de_datos");
    public static final String DIR_LOGS             = PROPS.getProperty("dir.logs",             "./base_de_datos");
    public static final String DIR_ARCHIVOS_SEGUROS = PROPS.getProperty("dir.archivos_seguros", "./storage/ficheros_seguros");

    // ── Seguridad ───────────────────────────────────────────────────
    private static final String SECRET_KEY = PROPS.getProperty("app.secret_key", "clave_secreta_de_prueba");

    public static String getSecretKey() {
        return SECRET_KEY;
    }

    public static final String AES_KEY = System.getenv("ERP_AES_KEY") != null
            ? System.getenv("ERP_AES_KEY")
            : PROPS.getProperty("app.aes_key", "ErpClaveSegura32CaracteresExact!");
}
