package apigenerica.backup;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MysqlBackupManager {
    private final SimpleDateFormat sdf = new SimpleDateFormat(BackupConfig.FORMATO_FECHA_BACKUP);

    public List<File> realizarBackup(File directorioDestino) throws Exception {
        List<File> archivosGenerados = new ArrayList<>();
        if (!directorioDestino.exists() && !directorioDestino.mkdirs()) {
            throw new IOException("No se pudo crear el directorio: " + directorioDestino);
        }
        String timestamp = sdf.format(new Date());

        if (BackupConfig.INCLUIR_DB_SISTEMA) {
            File sql = new File(directorioDestino, "mysql_" + BackupConfig.getDbSistema() + "_" + timestamp + ".sql");
            ejecutarMysqldump(BackupConfig.getDbSistema(), sql);
            archivosGenerados.add(sql);
            System.out.println("[MySQL] ✅ Backup sistema: " + formatearTamano(sql.length()));
        }

        if (BackupConfig.INCLUIR_DB_CLIENTE) {
            File sql = new File(directorioDestino, "mysql_" + BackupConfig.getDbCliente() + "_" + timestamp + ".sql");
            ejecutarMysqldump(BackupConfig.getDbCliente(), sql);
            archivosGenerados.add(sql);
            System.out.println("[MySQL] ✅ Backup cliente: " + formatearTamano(sql.length()));
        }
        return archivosGenerados;
    }

    public void restaurarBackup(File archivoSql, String nombreBaseDatos) throws Exception {
        if (!archivoSql.exists()) throw new FileNotFoundException("SQL no encontrado: " + archivoSql.getAbsolutePath());
        System.out.println("[MySQL] 🔄 Restaurando " + nombreBaseDatos + "...");
        ejecutarMysqlRestore(archivoSql, nombreBaseDatos);
        System.out.println("[MySQL] ✅ Restauración completada.");
    }

    private void ejecutarMysqldump(String dbName, File dest) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(BackupConfig.MYSQLDUMP_PATH);
        cmd.add("--host=" + BackupConfig.MYSQL_HOST);
        cmd.add("--port=" + BackupConfig.MYSQL_PORT);
        cmd.add("--user=" + BackupConfig.MYSQL_USER);
        if (!BackupConfig.MYSQL_PASSWORD.isEmpty()) cmd.add("--password=" + BackupConfig.MYSQL_PASSWORD);
        cmd.add("--single-transaction"); cmd.add("--routines"); cmd.add("--triggers");
        cmd.add("--events"); cmd.add("--add-drop-database"); cmd.add("--databases"); cmd.add(dbName);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(dest), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) { bw.write(line); bw.newLine(); }
        }

        if (p.waitFor() != 0) throw new BackupException("mysqldump falló. Verifica credenciales y PATH.");
    }

    private void ejecutarMysqlRestore(File sqlFile, String dbName) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(BackupConfig.MYSQLDUMP_PATH.replace("mysqldump", "mysql"));
        cmd.add("--host=" + BackupConfig.MYSQL_HOST);
        cmd.add("--port=" + BackupConfig.MYSQL_PORT);
        cmd.add("--user=" + BackupConfig.MYSQL_USER);
        if (!BackupConfig.MYSQL_PASSWORD.isEmpty()) cmd.add("--password=" + BackupConfig.MYSQL_PASSWORD);
        cmd.add(dbName);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.redirectInput(sqlFile);
        Process p = pb.start();

        if (p.waitFor() != 0) throw new BackupException("mysql restore falló.");
    }

    public boolean verificarMysqldumpDisponible() {
        try { return new ProcessBuilder(BackupConfig.MYSQLDUMP_PATH, "--version").start().waitFor() == 0; } 
        catch (Exception e) { return false; }
    }

    private String formatearTamano(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return String.format("%.2f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / 1048576.0);
    }
}