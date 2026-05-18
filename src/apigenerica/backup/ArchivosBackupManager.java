package apigenerica.backup;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ArchivosBackupManager {
    private final SimpleDateFormat sdf = new SimpleDateFormat(BackupConfig.FORMATO_FECHA_BACKUP);

    public List<File> realizarBackup(File destino) throws Exception {
        List<File> copiados = new ArrayList<>();
        if (!destino.exists() && !destino.mkdirs()) throw new IOException("No se pudo crear directorio: " + destino);
        String ts = sdf.format(new Date());

        if (BackupConfig.INCLUIR_DB4O) backupArchivo(BackupConfig.getArchivoDb4o(), "db4o_" + ts, destino, copiados);
        if (BackupConfig.INCLUIR_PARADOX) backupDirectorio(BackupConfig.getDirectorioParadox(), "paradox_data_" + ts, destino, copiados);
        if (BackupConfig.INCLUIR_LOGS) backupArchivo(BackupConfig.getArchivoLogsJson(), "logs_" + ts, destino, copiados);
        if (BackupConfig.INCLUIR_ARCHIVOS_SEGUROS) backupDirectorio(BackupConfig.getDirectorioArchivosSeguro(), "archivos_seguros_" + ts, destino, copiados);

        return copiados;
    }

    public void restaurarBackup(File dirBackup) throws Exception {
        if (!dirBackup.exists() || !dirBackup.isDirectory()) throw new FileNotFoundException("Directorio backup no válido");
        File[] archivos = dirBackup.listFiles();
        if (archivos == null) return;

        for (File f : archivos) {
            String n = f.getName();
            if (n.startsWith("db4o_")) restaurarArchivo(f, BackupConfig.getArchivoDb4o());
            else if (n.startsWith("paradox_data_") && f.isDirectory()) restaurarDirectorio(f, BackupConfig.getDirectorioParadox());
            else if (n.startsWith("logs_")) restaurarArchivo(f, BackupConfig.getArchivoLogsJson());
            else if (n.startsWith("archivos_seguros_") && f.isDirectory()) restaurarDirectorio(f, BackupConfig.getDirectorioArchivosSeguro());
        }
        System.out.println("[Archivos] ✅ Restauración completada.");
    }

    private void backupArchivo(File origen, String nombre, File destino, List<File> lista) throws IOException {
        if (!origen.exists()) { System.out.println("[Archivos] ⚠️ No encontrado: " + origen.getName()); return; }
        File d = new File(destino, nombre);
        Files.copy(origen.toPath(), d.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        lista.add(d);
        System.out.println("[Archivos] 📄 Copiado: " + d.getName() + " (" + formatearTamano(d.length()) + ")");
    }

    private void backupDirectorio(File origen, String nombre, File destino, List<File> lista) throws IOException {
        if (!origen.exists() || !origen.isDirectory()) { System.out.println("[Archivos] ⚠️ No encontrado: " + origen.getName()); return; }
        File d = new File(destino, nombre);
        copiarDirectorio(origen.toPath(), d.toPath());
        lista.add(d);
        System.out.println("[Archivos] 📁 Copiado: " + d.getName() + " (" + formatearTamano(calcularTamano(d)) + ")");
    }

    private void restaurarArchivo(File backup, File destino) throws IOException {
        System.out.println("[Archivos] 🔄 Restaurando: " + destino.getName());
        Files.copy(backup.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private void restaurarDirectorio(File backup, File destino) throws IOException {
        System.out.println("[Archivos] 🔄 Restaurando directorio: " + destino.getName());
        if (destino.exists()) eliminarDirectorio(destino.toPath());
        copiarDirectorio(backup.toPath(), destino.toPath());
    }

    private void copiarDirectorio(Path origen, Path destino) throws IOException {
        Files.walkFileTree(origen, new SimpleFileVisitor<Path>() {
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path t = destino.resolve(origen.relativize(dir));
                if (!Files.exists(t)) Files.createDirectories(t);
                return FileVisitResult.CONTINUE;
            }
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, destino.resolve(origen.relativize(file)), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void eliminarDirectorio(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException { Files.delete(file); return FileVisitResult.CONTINUE; }
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException { Files.delete(d); return FileVisitResult.CONTINUE; }
        });
    }

    private long calcularTamano(File dir) throws IOException {
        final long[] s = {0};
        if (dir.exists()) Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
            public FileVisitResult visitFile(Path f, BasicFileAttributes a) { s[0] += a.size(); return FileVisitResult.CONTINUE; }
        });
        return s[0];
    }

    private String formatearTamano(long b) {
        if (b < 1024) return b + " B";
        if (b < 1048576) return String.format("%.2f KB", b / 1024.0);
        return String.format("%.2f MB", b / 1048576.0);
    }
}