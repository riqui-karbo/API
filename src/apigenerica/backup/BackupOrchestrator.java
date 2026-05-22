package apigenerica.backup;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.nio.file.Files;

public class BackupOrchestrator {
    private final MysqlBackupManager mysql = new MysqlBackupManager();
    private final ArchivosBackupManager files = new ArchivosBackupManager();
    private final CompressionManager comp = new CompressionManager();

    public File ejecutarBackupCompleto() throws Exception {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern(BackupConfig.FORMATO_FECHA_BACKUP));
        String nombre = BackupConfig.PREFIJO_BACKUP + ts + BackupConfig.EXTENSION_BACKUP;
        File tempDir = Files.createTempDirectory("backup_temp_").toFile();
        File destinoFinal = new File(BackupConfig.getDirectorioBackups(), nombre);

        try {
            System.out.println("[Orchestrator] Iniciando backup completo: " + nombre);

            // 1. MySQL (phpMyAdmin / mysqldump) → .sql dentro de tempDir
            if (BackupConfig.INCLUIR_DB_SISTEMA || BackupConfig.INCLUIR_DB_CLIENTE) {
                mysql.realizarBackup(tempDir);
            }

            // 2. db4o (directorio storage/ comprimido como ZIP) + Paradox (directorio copiado)
            //    + Logs (JSON exportado como .txt)  → todos dentro de tempDir
            files.realizarBackup(tempDir);

            // 3. Comprimir TODO tempDir en un único ZIP (y opcionalmente cifrar)
            comp.comprimirYCifrar(tempDir, destinoFinal, BackupConfig.CIFRAR_BACKUPS);

            rotarBackups();

            long tamano = destinoFinal.length();
            System.out.println("[Orchestrator] Backup finalizado: " + destinoFinal.getName()
                    + " (" + formatearTamano(tamano) + ")");
            System.out.println("[Orchestrator] Contenido del backup:");
            System.out.println("  - MySQL:   .sql por base de datos");
            System.out.println("  - db4o:    directorio storage/ comprimido como db4o_<ts>.zip");
            System.out.println("  - Paradox: directorio base_de_datos/ como paradox_data_<ts>/");
            System.out.println("  - Logs:    logs_backup.json exportado como logs_<ts>.txt");
            return destinoFinal;
        } finally {
            eliminarDirectorio(tempDir.toPath());
        }
    }

    public void restaurarBackupCompleto(File archivo) throws Exception {
        File tempDir = Files.createTempDirectory("restore_temp_").toFile();
        try {
            System.out.println("[Orchestrator] Restaurando desde: " + archivo.getName());
            comp.descifrarYDescomprimir(archivo, tempDir, BackupConfig.CIFRAR_BACKUPS);
            File[] contenido = tempDir.listFiles();
            if (contenido != null) {
                for (File f : contenido) {
                    String n = f.getName();
                    // MySQL: ficheros .sql generados por mysqldump
                    if (n.startsWith("mysql_") && n.endsWith(".sql")) {
                        String db = extraerDbDesdeNombre(n);
                        if (db != null) mysql.restaurarBackup(f, db);
                    }
                }
            }
            // db4o, Paradox y Logs los gestiona ArchivosBackupManager
            files.restaurarBackup(tempDir);
            System.out.println("[Orchestrator] Restauración completada.");
        } finally {
            eliminarDirectorio(tempDir.toPath());
        }
    }

    private void rotarBackups() {
        File dir = BackupConfig.getDirectorioBackups();
        File[] backups = dir.listFiles(new java.io.FileFilter() {
            public boolean accept(File f) {
                return f.getName().startsWith(BackupConfig.PREFIJO_BACKUP)
                        && f.getName().endsWith(BackupConfig.EXTENSION_BACKUP);
            }
        });
        if (backups == null || backups.length <= BackupConfig.MAX_BACKUPS_RETENIDOS) return;

        Arrays.sort(backups);
        for (int i = 0; i < backups.length - BackupConfig.MAX_BACKUPS_RETENIDOS; i++) {
            if (backups[i].delete())
                System.out.println("[Orchestrator] Eliminado antiguo: " + backups[i].getName());
        }
    }

    private String extraerDbDesdeNombre(String nombre) {
        // Formato esperado: mysql_<dbName>_<yyyyMMdd>_<HHmmss>.sql
        // Ejemplo:          mysql_erp_empresa_20260522_012456.sql -> "erp_empresa"
        if (!nombre.startsWith("mysql_") || !nombre.endsWith(".sql")) return null;
        // Quitar prefijo "mysql_" y sufijo ".sql"
        String cuerpo = nombre.substring(6, nombre.length() - 4);
        // El timestamp ocupa siempre 15 chars (yyyyMMdd_HHmmss) + 1 guion bajo separador = 16
        int longitudTimestamp = BackupConfig.FORMATO_FECHA_BACKUP.length() + 1;
        if (cuerpo.length() <= longitudTimestamp) return null;
        return cuerpo.substring(0, cuerpo.length() - longitudTimestamp);
    }

    private void eliminarDirectorio(java.nio.file.Path dir) {
        try {
            Files.walkFileTree(dir, new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                public java.nio.file.FileVisitResult visitFile(java.nio.file.Path f,
                        java.nio.file.attribute.BasicFileAttributes a) throws IOException {
                    Files.delete(f);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
                public java.nio.file.FileVisitResult postVisitDirectory(java.nio.file.Path d,
                        IOException e) throws IOException {
                    Files.delete(d);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception ignored) {}
    }

    private String formatearTamano(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return String.format("%.2f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / 1048576.0);
    }
}