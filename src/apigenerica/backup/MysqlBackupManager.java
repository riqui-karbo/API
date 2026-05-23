package apigenerica.backup;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MysqlBackupManager {
    private final SimpleDateFormat sdf = new SimpleDateFormat(BackupConfig.FORMATO_FECHA_BACKUP);

    // Rutas candidatas donde suele instalarse MySQL en Windows
    private static final String[] RUTAS_WINDOWS = {
        "C:\\Program Files\\MySQL\\MySQL Server 9.0\\bin\\mysqldump.exe",
        "C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysqldump.exe",
        "C:\\Program Files\\MySQL\\MySQL Server 5.7\\bin\\mysqldump.exe",
        "C:\\xampp\\mysql\\bin\\mysqldump.exe",
        "C:\\wamp64\\bin\\mysql\\mysql8.0\\bin\\mysqldump.exe",
        "C:\\wamp\\bin\\mysql\\mysql8.0\\bin\\mysqldump.exe",
        "C:\\laragon\\bin\\mysql\\mysql-8.0\\bin\\mysqldump.exe",
    };

    /**
     * Resuelve la ruta real del ejecutable mysqldump.
     *
     * Prioridad:
     *  1. Variable de entorno MYSQLDUMP_PATH (configurada en BackupConfig)
     *  2. Rutas conocidas de instalaciones típicas de MySQL en Windows
     *  3. "mysqldump" tal cual (funciona si está en el PATH del sistema)
     *
     * @throws BackupException si no se encuentra ningún ejecutable válido
     */
    public static String resolverRutaMysqldump() throws BackupException {
        // 1. Si el usuario configuró una ruta explícita distinta del valor por defecto, usarla
        String configurada = BackupConfig.MYSQLDUMP_PATH;
        if (configurada != null && !configurada.equals("mysqldump") && !configurada.isEmpty()) {
            File f = new File(configurada);
            if (f.exists() && f.canExecute()) {
                System.out.println("[MySQL] mysqldump encontrado (config): " + configurada);
                return configurada;
            }
        }

        // 2. Buscar en rutas conocidas de Windows
        for (String ruta : RUTAS_WINDOWS) {
            File f = new File(ruta);
            if (f.exists()) {
                System.out.println("[MySQL] mysqldump encontrado (auto): " + ruta);
                return ruta;
            }
        }

        // 3. Buscar en rutas dinámicas: "C:\Program Files\MySQL\MySQL Server X.Y\bin\"
        File programFiles = new File("C:\\Program Files\\MySQL");
        if (programFiles.exists() && programFiles.isDirectory()) {
            File[] versiones = programFiles.listFiles(File::isDirectory);
            if (versiones != null) {
                for (File version : versiones) {
                    File exe = new File(version, "bin\\mysqldump.exe");
                    if (exe.exists()) {
                        System.out.println("[MySQL] mysqldump encontrado (dinámico): " + exe.getAbsolutePath());
                        return exe.getAbsolutePath();
                    }
                }
            }
        }

        // 4. Último recurso: confiar en que el nombre solo funcione en el PATH
        System.out.println("[MySQL] AVISO: mysqldump no localizado, usando nombre simple (requiere PATH configurado).");
        return "mysqldump";
    }

    /** Igual que resolverRutaMysqldump() pero para el cliente mysql (restauración / SHOW DATABASES) */
    public static String resolverRutaMysql() throws BackupException {
        String dump = resolverRutaMysqldump();
        // En la misma carpeta bin/ siempre hay mysql.exe junto a mysqldump.exe
        if (dump.endsWith(".exe")) {
            String mysql = dump.replace("mysqldump.exe", "mysql.exe");
            if (new File(mysql).exists()) return mysql;
        }
        return dump.replace("mysqldump", "mysql");
    }

    // ─────────────────────────────────────────────────────────────────────────

    public List<File> realizarBackup(File directorioDestino) throws Exception {
        List<File> archivosGenerados = new ArrayList<>();
        if (!directorioDestino.exists() && !directorioDestino.mkdirs()) {
            throw new IOException("No se pudo crear el directorio: " + directorioDestino);
        }
        String timestamp = sdf.format(new Date());

        // Solo se hace backup de las bases de datos del proyecto, definidas en AppConfig.
        // Nunca se tocan otras BDs que pueda haber en el servidor MySQL.
        List<String> basesDelProyecto = new ArrayList<>();
        if (BackupConfig.INCLUIR_DB_SISTEMA) basesDelProyecto.add(BackupConfig.getDbSistema());
        if (BackupConfig.INCLUIR_DB_CLIENTE) basesDelProyecto.add(BackupConfig.getDbCliente());

        System.out.println("[MySQL] Bases del proyecto a respaldar: " + basesDelProyecto);
        for (String db : basesDelProyecto) {
            File sql = new File(directorioDestino, "mysql_" + db + "_" + timestamp + ".sql");
            ejecutarMysqldump(db, sql);
            archivosGenerados.add(sql);
            System.out.println("[MySQL] Backup '" + db + "': " + formatearTamano(sql.length()));
        }
        return archivosGenerados;
    }

    public void restaurarBackup(File archivoSql, String nombreBaseDatos) throws Exception {
        if (!archivoSql.exists())
            throw new FileNotFoundException("SQL no encontrado: " + archivoSql.getAbsolutePath());
        System.out.println("[MySQL] Restaurando " + nombreBaseDatos + "...");
        eliminarYRecrearBaseDatos(nombreBaseDatos);
        ejecutarMysqlRestore(archivoSql, nombreBaseDatos);
        System.out.println("[MySQL] Restauración completada.");
    }

    /**
     * Elimina la base de datos si existe y la vuelve a crear vacía.
     * Esto garantiza una restauración limpia sin conflictos con datos previos.
     */
    private void eliminarYRecrearBaseDatos(String dbName) throws Exception {
        System.out.println("[MySQL] Eliminando base de datos existente: " + dbName);
        // Deshabilitar foreign key checks para evitar errores de constraint al borrar
        ejecutarSentenciaSQL("SET GLOBAL FOREIGN_KEY_CHECKS = 0");
        try {
            ejecutarSentenciaSQL("DROP DATABASE IF EXISTS `" + dbName + "`");
        } finally {
            // Siempre reactivar, aunque falle el DROP
            ejecutarSentenciaSQL("SET GLOBAL FOREIGN_KEY_CHECKS = 1");
        }
        System.out.println("[MySQL] Creando base de datos nueva: " + dbName);
        ejecutarSentenciaSQL("CREATE DATABASE `" + dbName + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        System.out.println("[MySQL] Base de datos recreada correctamente.");
    }

    /**
     * Ejecuta una sentencia SQL directamente contra el servidor MySQL (sin seleccionar BD).
     */
    private void ejecutarSentenciaSQL(String sentencia) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(resolverRutaMysql());
        cmd.add("--host=" + BackupConfig.MYSQL_HOST);
        cmd.add("--port=" + BackupConfig.MYSQL_PORT);
        cmd.add("--user=" + BackupConfig.MYSQL_USER);
        if (!BackupConfig.MYSQL_PASSWORD.isEmpty())
            cmd.add("--password=" + BackupConfig.MYSQL_PASSWORD);
        cmd.add("--execute=" + sentencia);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        // Capturar salida para diagnóstico en caso de error
        StringBuilder salida = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) salida.append(line).append("\n");
        }

        int codigo = p.waitFor();
        if (codigo != 0)
            throw new BackupException(
                "Error al ejecutar: " + sentencia + "\nSalida: " + salida.toString().trim());
    }

    private void ejecutarMysqldump(String dbName, File dest) throws Exception {
        String rutaDump = resolverRutaMysqldump();
        System.out.println("[MySQL] Usando mysqldump: " + rutaDump);

        List<String> cmd = new ArrayList<>();
        cmd.add(rutaDump);
        cmd.add("--host=" + BackupConfig.MYSQL_HOST);
        cmd.add("--port=" + BackupConfig.MYSQL_PORT);
        cmd.add("--user=" + BackupConfig.MYSQL_USER);
        if (!BackupConfig.MYSQL_PASSWORD.isEmpty())
            cmd.add("--password=" + BackupConfig.MYSQL_PASSWORD);
        cmd.add("--single-transaction");
        cmd.add("--routines");
        cmd.add("--triggers");
        cmd.add("--events");
        cmd.add("--add-drop-database");
        cmd.add("--databases");
        cmd.add(dbName);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter bw = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(dest), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                bw.write(line);
                bw.newLine();
            }
        }

        if (p.waitFor() != 0)
            throw new BackupException(
                "mysqldump falló para la base '" + dbName + "'.\n" +
                "Ruta usada: " + rutaDump + "\n" +
                "Verifica usuario/contraseña y que el servidor MySQL esté en marcha.");
    }

    private void ejecutarMysqlRestore(File sqlFile, String dbName) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(resolverRutaMysql());
        cmd.add("--host=" + BackupConfig.MYSQL_HOST);
        cmd.add("--port=" + BackupConfig.MYSQL_PORT);
        cmd.add("--user=" + BackupConfig.MYSQL_USER);
        if (!BackupConfig.MYSQL_PASSWORD.isEmpty())
            cmd.add("--password=" + BackupConfig.MYSQL_PASSWORD);
        cmd.add(dbName);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.redirectInput(sqlFile);
        Process p = pb.start();

        if (p.waitFor() != 0)
            throw new BackupException("mysql restore falló para '" + dbName + "'.");
    }

    public boolean verificarMysqldumpDisponible() {
        try {
            String ruta = resolverRutaMysqldump();
            return new ProcessBuilder(ruta, "--version").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String formatearTamano(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return String.format("%.2f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / 1048576.0);
    }
}