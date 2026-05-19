package apigenerica.backup;
import apigenerica.config.AppConfig;
import java.io.File;
import java.nio.file.Paths;

public class BackupConfig {
    public static String DIRECTORIO_BACKUPS = getEnv("ERP_BACKUP_DIR", "./backups");
    public static String DIRECTORIO_PARADOX = getEnv("ERP_PARADOX_DIR", "./paradox_data");
    public static String ARCHIVO_DB4O       = getEnv("ERP_DB4O_FILE", "./ficheros.db4o");
    public static String DIRECTORIO_ARCHIVOS_SEGUROS = getEnv("ERP_SECURE_FILES_DIR", "./archivos_seguros");
    public static String DIRECTORIO_LOGS    = getEnv("ERP_LOGS_DIR", "./base_de_datos");
    public static String ARCHIVO_LOGS_JSON  = "logs_backup.json";

    public static String MYSQL_HOST     = getEnv("MYSQL_HOST", "localhost");
    public static int    MYSQL_PORT     = Integer.parseInt(getEnv("MYSQL_PORT", "3306"));
    public static String MYSQL_USER     = getEnv("MYSQL_USER", "root");
    public static String MYSQL_PASSWORD = getEnv("MYSQL_PASSWORD", "");
    public static String MYSQLDUMP_PATH = getEnv("MYSQLDUMP_PATH", "mysqldump");

    public static int     MAX_BACKUPS_RETENIDOS = Integer.parseInt(getEnv("ERP_MAX_BACKUPS", "10"));
    public static boolean COMPRIMIR_BACKUPS     = Boolean.parseBoolean(getEnv("ERP_COMPRESS_BACKUPS", "true"));
    public static boolean CIFRAR_BACKUPS        = Boolean.parseBoolean(getEnv("ERP_ENCRYPT_BACKUPS", "false"));
    public static String  BACKUP_ENCRYPTION_KEY = AppConfig.AES_KEY;

    public static boolean INCLUIR_DB_SISTEMA       = true;
    public static boolean INCLUIR_DB_CLIENTE       = true;
    public static boolean INCLUIR_DB4O             = true;
    public static boolean INCLUIR_PARADOX          = true;
    public static boolean INCLUIR_LOGS             = true;
    public static boolean INCLUIR_ARCHIVOS_SEGUROS = true;

    public static String FORMATO_FECHA_BACKUP = "yyyyMMdd_HHmmss";
    public static String PREFIJO_BACKUP       = "backup_erp_";
    public static String EXTENSION_BACKUP     = ".zip";

    public static File getDirectorioBackups()        { return new File(DIRECTORIO_BACKUPS).getAbsoluteFile(); }
    public static File getArchivoDb4o()              { return new File(ARCHIVO_DB4O).getAbsoluteFile(); }
    public static File getDirectorioParadox()        { return new File(DIRECTORIO_PARADOX).getAbsoluteFile(); }
    public static File getDirectorioArchivosSeguro() { return new File(DIRECTORIO_ARCHIVOS_SEGUROS).getAbsoluteFile(); }
    public static File getArchivoLogsJson()          { return Paths.get(DIRECTORIO_LOGS, ARCHIVO_LOGS_JSON).toFile().getAbsoluteFile(); }
    public static String getDbSistema()              { return AppConfig.DB_SISTEMA; }
    public static String getDbCliente()              { return AppConfig.DB_CLIENTE; }

    public static void validarConfiguracion() {
        File dir = getDirectorioBackups();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("No se pudo crear el directorio de backups: " + dir.getAbsolutePath());
        }
        System.out.println("[BackupConfig] Configuración validada correctamente.");
    }

    private static String getEnv(String key, String defaultValue) {
        String env = System.getenv(key);
        return (env != null && !env.isEmpty()) ? env : defaultValue;
    }
}