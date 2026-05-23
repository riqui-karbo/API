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
    public static String DIRECTORIO_BACKUPS          = getEnv("ERP_BACKUP_DIR",       AppConfig.DIR_BACKUPS);
    public static String DIRECTORIO_DB4O_RAIZ        = getEnv("ERP_DB4O_ROOT_DIR",    AppConfig.DIR_DB4O_RAIZ);
    public static String ARCHIVO_DB4O                = getEnv("ERP_DB4O_FILE",         AppConfig.ARCHIVO_DB4O);
    public static String DIRECTORIO_PARADOX          = getEnv("ERP_PARADOX_DIR",      AppConfig.DIR_PARADOX);
    public static String DIRECTORIO_LOGS             = getEnv("ERP_LOGS_DIR",         AppConfig.DIR_LOGS);
    public static String ARCHIVO_LOGS_JSON           = "logs_backup.json";
    public static String DIRECTORIO_ARCHIVOS_SEGUROS = getEnv("ERP_SECURE_FILES_DIR", AppConfig.DIR_ARCHIVOS_SEGUROS);

    // ── MySQL ────────────────────────────────────────────────────────────────
    public static String MYSQL_HOST     = getEnv("MYSQL_HOST",     AppConfig.DB_HOST);
    public static int    MYSQL_PORT     = Integer.parseInt(getEnv("MYSQL_PORT",     AppConfig.DB_PORT));
    public static String MYSQL_USER     = getEnv("MYSQL_USER",     AppConfig.DB_USUARIO);
    public static String MYSQL_PASSWORD = getEnv("MYSQL_PASSWORD", AppConfig.DB_PASSWORD);
    public static String MYSQLDUMP_PATH = getEnv("MYSQLDUMP_PATH", "mysqldump");

    // ── Que incluir en el backup ─────────────────────────────────────────────
    public static boolean INCLUIR_DB_SISTEMA       = true;
    public static boolean INCLUIR_DB_CLIENTE       = true;
    public static boolean INCLUIR_DB4O             = true;
    public static boolean INCLUIR_PARADOX          = true;
    public static boolean INCLUIR_LOGS             = true;
    public static boolean INCLUIR_ARCHIVOS_SEGUROS = true;

    // ── Opciones de backup ───────────────────────────────────────────────────
    public static int     MAX_BACKUPS_RETENIDOS = Integer.parseInt(getEnv("ERP_MAX_BACKUPS",        "10"));
    public static boolean COMPRIMIR_BACKUPS     = Boolean.parseBoolean(getEnv("ERP_COMPRESS_BACKUPS", "true"));
    public static boolean CIFRAR_BACKUPS        = Boolean.parseBoolean(getEnv("ERP_ENCRYPT_BACKUPS",  "false"));
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

    // ── Validacion ───────────────────────────────────────────────────────────
    public static void validarConfiguracion() {
        File dir = getDirectorioBackups();
        if (!dir.exists() && !dir.mkdirs())
            throw new RuntimeException("No se pudo crear el directorio de backups: " + dir.getAbsolutePath());
        System.out.println("[BackupConfig] Configuracion validada.");
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