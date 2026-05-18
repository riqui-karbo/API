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
            System.out.println("[Orchestrator] 🚀 Iniciando backup: " + nombre);
            if (BackupConfig.INCLUIR_DB_SISTEMA || BackupConfig.INCLUIR_DB_CLIENTE) {
                mysql.realizarBackup(tempDir);
            }
            files.realizarBackup(tempDir);
            comp.comprimirYCifrar(tempDir, destinoFinal, BackupConfig.CIFRAR_BACKUPS);
            rotarBackups();
            System.out.println("[Orchestrator] 🎉 Backup finalizado: " + destinoFinal.getAbsolutePath());
            return destinoFinal;
        } finally {
            eliminarDirectorio(tempDir.toPath());
        }
    }

    public void restaurarBackupCompleto(File archivo) throws Exception {
        File tempDir = Files.createTempDirectory("restore_temp_").toFile();
        try {
            System.out.println("[Orchestrator] 🔄 Restaurando desde: " + archivo.getName());
            comp.descifrarYDescomprimir(archivo, tempDir, BackupConfig.CIFRAR_BACKUPS);
            File[] contenido = tempDir.listFiles();
            if (contenido != null) {
                for (File f : contenido) {
                    if (f.getName().endsWith(".sql")) {
                        String db = extraerDbDesdeNombre(f.getName());
                        if (db != null) mysql.restaurarBackup(f, db);
                    }
                }
            }
            files.restaurarBackup(tempDir);
            System.out.println("[Orchestrator] 🎉 Restauración completada.");
        } finally {
            eliminarDirectorio(tempDir.toPath());
        }
    }

    private void rotarBackups() {
        File dir = BackupConfig.getDirectorioBackups();
        File[] backups = dir.listFiles(new java.io.FileFilter() {
            public boolean accept(File f) {
                return f.getName().startsWith(BackupConfig.PREFIJO_BACKUP) && f.getName().endsWith(BackupConfig.EXTENSION_BACKUP);
            }
        });
        if (backups == null || backups.length <= BackupConfig.MAX_BACKUPS_RETENIDOS) return;

        Arrays.sort(backups);
        for (int i = 0; i < backups.length - BackupConfig.MAX_BACKUPS_RETENIDOS; i++) {
            if (backups[i].delete()) System.out.println("[Orchestrator] 🗑️ Eliminado antiguo: " + backups[i].getName());
        }
    }

    private String extraerDbDesdeNombre(String nombre) {
        if (!nombre.startsWith("mysql_") || !nombre.contains(".sql")) return null;
        String sinPrefijo = nombre.substring(6);
        int lastIdx = sinPrefijo.lastIndexOf('_');
        return lastIdx > 0 ? sinPrefijo.substring(0, lastIdx) : null;
    }

    private void eliminarDirectorio(java.nio.file.Path dir) {
        try {
            Files.walkFileTree(dir, new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                public java.nio.file.FileVisitResult visitFile(java.nio.file.Path f, java.nio.file.attribute.BasicFileAttributes a) throws IOException { Files.delete(f); return java.nio.file.FileVisitResult.CONTINUE; }
                public java.nio.file.FileVisitResult postVisitDirectory(java.nio.file.Path d, IOException e) throws IOException { Files.delete(d); return java.nio.file.FileVisitResult.CONTINUE; }
            });
        } catch (Exception ignored) {}
    }
}