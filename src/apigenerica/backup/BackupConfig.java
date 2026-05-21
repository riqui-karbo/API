package apigenerica.backup;
import apigenerica.config.AppConfig;
import java.io.File;
import java.nio.file.Paths;

/**
 * Configuración centralizada del sistema de backups.
 *
 * Todas las rutas pueden sobreescribirse con variables de entorno,
 * lo que permite ajustarlas en producción sin recompilar.
 *
 * ── Estructura del backup final ────────────────────────────────────────────
 *  backup_erp_<ts>.zip
 *  ├── mysql_erp_sistema_<ts>.sql          (volcado mysqldump de erp_sistema)
 *  ├── mysql_erp_empresa_<ts>.sql          (volcado mysqldump de erp_empresa)
 *  ├── db4o_<ts>.zip                       (directorio storage/ comprimido)
 *  │    ├── ficheros_seguros/
 *  │    │    └── documentos/
 *  │    │         └── <uuid>.enc
 *  │    └── ficheros.db4o                  (si está dentro de storage/)
 *  ├── paradox_data_<ts>/                  (copia del directorio base_de_datos/)
 *  │    ├── meta_cache.db
 *  │    └── paradox_sensibles.db
 *  └── logs_<ts>.txt                       (contenido de logs_backup.json, ext. cambiada)
 * ───────────────────────────────────────────────────────────────────────────
 */
public class BackupConfig {

    // ── Directorios ──────────────────────────────────────────────────────────

    /** Directorio donde se guardan los ZIPs de backup generados. */
    public static String DIRECTORIO_BACKUPS =
            getEnv("ERP_BACKUP_DIR", "./backups");

    /**
     * Directorio RAÍZ de db4o: contiene el fichero .db4o y los archivos
     * seguros cifrados. Todo este directorio se comprime en un único ZIP
     * dentro del backup.
     * Por defecto: ./storage/
     */
    public static String DIRECTORIO_DB4O_RAIZ =
            getEnv("ERP_DB4O_ROOT_DIR", "./storage");

    /**
     * Ruta al fichero .db4o (solo usado por ConexionDb4o para abrir la BD).
     * No se usa directamente en el backup; el backup toma el directorio raíz.
     */
    public static String ARCHIVO_DB4O =
            getEnv("ERP_DB4O_FILE", "./ficheros.db4o");

    /**
     * Directorio de Paradox: contiene los ficheros .DB.
     * Se copia tal cual dentro del backup como subdirectorio paradox_data_<ts>/.
     */
    public static String DIRECTORIO_PARADOX =
            getEnv("ERP_PARADOX_DIR", "./base_de_datos");

    /**
     * Directorio donde vive logs_backup.json.
     * El fichero se copia en el backup con extensión .txt.
     */
    public static String DIRECTORIO_LOGS =
            getEnv("ERP_LOGS_DIR", "./base_de_datos");

    /** Nombre del fichero JSON de logs dentro de DIRECTORIO_LOGS. */
    public static String ARCHIVO_LOGS_JSON = "logs_backup.json";

    /**
     * Directorio de archivos seguros adicionales que se quieren copiar
     * por separado además del ZIP de db4o (opcional).
     */
    public static String DIRECTORIO_ARCHIVOS_SEGUROS =
            getEnv("ERP_SECURE_FILES_DIR", "./archivos_seguros");

    // ── MySQL ────────────────────────────────────────────────────────────────

    public static String MYSQL_HOST     = getEnv("MYSQL_HOST", "localhost");
    public static int    MYSQL_PORT     = Integer.parseInt(getEnv("MYSQL_PORT", "3306"));
    public static String MYSQL_USER     = getEnv("MYSQL_USER", "root");
    public static String MYSQL_PASSWORD = getEnv("MYSQL_PASSWORD", "");
    /**
     * Ruta al ejecutable mysqldump.
     * Si se deja el valor por defecto "mysqldump", MysqlBackupManager lo localiza
     * automáticamente (MySQL Server 8/9, XAMPP, WAMP, Laragon...).
     * Para forzar una ruta concreta usa la variable de entorno MYSQLDUMP_PATH:
     *   MYSQLDUMP_PATH=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe
     */
    public static String MYSQLDUMP_PATH = getEnv("MYSQLDUMP_PATH", "mysqldump");

    // ── Qué incluir en el backup ─────────────────────────────────────────────

    /** Incluir volcados mysqldump de las bases de datos MySQL. */
    public static boolean INCLUIR_DB_SISTEMA       = true;
    public static boolean INCLUIR_DB_CLIENTE       = true;

    /** Incluir el directorio db4o (storage/) comprimido como ZIP. */
    public static boolean INCLUIR_DB4O             = true;

    /** Incluir el directorio Paradox (base_de_datos/) copiado. */
    public static boolean INCLUIR_PARADOX          = true;

    /** Incluir logs_backup.json exportado como .txt. */
    public static boolean INCLUIR_LOGS             = true;

    /** Incluir directorio de archivos seguros adicionales (opcional). */
    public static boolean INCLUIR_ARCHIVOS_SEGUROS = true;

    // ── Opciones de backup ───────────────────────────────────────────────────

    public static int     MAX_BACKUPS_RETENIDOS = Integer.parseInt(getEnv("ERP_MAX_BACKUPS", "10"));
    public static boolean COMPRIMIR_BACKUPS     = Boolean.parseBoolean(getEnv("ERP_COMPRESS_BACKUPS", "true"));
    public static boolean CIFRAR_BACKUPS        = Boolean.parseBoolean(getEnv("ERP_ENCRYPT_BACKUPS", "false"));
    public static String  BACKUP_ENCRYPTION_KEY = AppConfig.AES_KEY;

    public static String FORMATO_FECHA_BACKUP = "yyyyMMdd_HHmmss";
    public static String PREFIJO_BACKUP       = "backup_erp_";
    public static String EXTENSION_BACKUP     = ".zip";

    // ── Getters ──────────────────────────────────────────────────────────────

    public static File getDirectorioBackups()        { return new File(DIRECTORIO_BACKUPS).getAbsoluteFile(); }
    public static File getDirectorioDb4oRaiz()       { return new File(DIRECTORIO_DB4O_RAIZ).getAbsoluteFile(); }
    public static File getArchivoDb4o()              { return new File(ARCHIVO_DB4O).getAbsoluteFile(); }
    public static File getDirectorioParadox()        { return new File(DIRECTORIO_PARADOX).getAbsoluteFile(); }
    public static File getDirectorioArchivosSeguro() { return new File(DIRECTORIO_ARCHIVOS_SEGUROS).getAbsoluteFile(); }
    public static File getArchivoLogsJson()          { return Paths.get(DIRECTORIO_LOGS, ARCHIVO_LOGS_JSON).toFile().getAbsoluteFile(); }
    public static String getDbSistema()              { return AppConfig.DB_SISTEMA; }
    public static String getDbCliente()              { return AppConfig.DB_CLIENTE; }

    // ── Validación ───────────────────────────────────────────────────────────

    public static void validarConfiguracion() {
        File dir = getDirectorioBackups();
        if (!dir.exists() && !dir.mkdirs())
            throw new RuntimeException("No se pudo crear el directorio de backups: " + dir.getAbsolutePath());
        System.out.println("[BackupConfig] Configuración validada.");
        System.out.println("[BackupConfig]   Backups  → " + dir.getAbsolutePath());
        System.out.println("[BackupConfig]   db4o     → " + getDirectorioDb4oRaiz().getAbsolutePath());
        System.out.println("[BackupConfig]   Paradox  → " + getDirectorioParadox().getAbsolutePath());
        System.out.println("[BackupConfig]   Logs     → " + getArchivoLogsJson().getAbsolutePath());
    }

    private static String getEnv(String key, String defaultValue) {
        String env = System.getenv(key);
        return (env != null && !env.isEmpty()) ? env : defaultValue;
    }
}
