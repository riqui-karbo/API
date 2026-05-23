package apigenerica.backup;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Gestiona el backup y restauración de los tres orígenes de datos no-MySQL:
 *
 *  1. db4o   → Comprime el directorio raíz (storage/) en un ZIP individual.
 *              Ese ZIP, junto con los demás ficheros, acaba dentro del tempDir
 *              que BackupOrchestrator comprime luego en el backup final.
 *
 *  2. Paradox → Copia el directorio base_de_datos/ (con los .DB) como
 *               subdirectorio dentro del tempDir.
 *
 *  3. Logs    → Copia logs_backup.json cambiando la extensión a .txt.
 *               El contenido no se modifica; solo cambia la extensión.
 */
public class ArchivosBackupManager {
    private final SimpleDateFormat sdf = new SimpleDateFormat(BackupConfig.FORMATO_FECHA_BACKUP);

    // ─────────────────────────────────────────────────────────────────────────
    // BACKUP
    // ─────────────────────────────────────────────────────────────────────────

    public List<File> realizarBackup(File destino) throws Exception {
        List<File> generados = new ArrayList<>();
        if (!destino.exists() && !destino.mkdirs())
            throw new IOException("No se pudo crear directorio: " + destino);

        String ts = sdf.format(new Date());

        // ── 1. db4o: comprime storage/ en db4o_<ts>.zip ──────────────────────
        if (BackupConfig.INCLUIR_DB4O) {
            File dirDb4o = BackupConfig.getDirectorioDb4oRaiz();
            backupDb4oComoZip(dirDb4o, "db4o_" + ts, destino, generados);
        }

        // ── 2. Paradox: copia base_de_datos/ como paradox_data_<ts>/ ─────────
        if (BackupConfig.INCLUIR_PARADOX) {
            File dirParadox = BackupConfig.getDirectorioParadox();
            backupDirectorio(dirParadox, "paradox_data_" + ts, destino, generados);
        }

        // ── 3. Logs: copia logs_backup.json renombrándolo a logs_<ts>.txt ─────
        if (BackupConfig.INCLUIR_LOGS) {
            File jsonLogs = BackupConfig.getArchivoLogsJson();
            backupLogsComoTxt(jsonLogs, "logs_" + ts, destino, generados);
        }

        // ── 4. Archivos seguros adicionales (opcional) ────────────────────────
        if (BackupConfig.INCLUIR_ARCHIVOS_SEGUROS) {
            File dirSeguros = BackupConfig.getDirectorioArchivosSeguro();
            backupDirectorio(dirSeguros, "archivos_seguros_" + ts, destino, generados);
        }

        return generados;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RESTAURACIÓN
    // ─────────────────────────────────────────────────────────────────────────

    public void restaurarBackup(File dirBackup) throws Exception {
        if (!dirBackup.exists() || !dirBackup.isDirectory())
            throw new FileNotFoundException("Directorio de backup no válido: " + dirBackup);

        File[] archivos = dirBackup.listFiles();
        if (archivos == null) return;

        for (File f : archivos) {
            String n = f.getName();
            if (n.startsWith("db4o_") && n.endsWith(".zip")) {
                // db4o: descomprime el ZIP en storage/
                restaurarDb4oDesdeZip(f, BackupConfig.getDirectorioDb4oRaiz());

            } else if (n.startsWith("paradox_data_") && f.isDirectory()) {
                // Paradox: copia el directorio sobre base_de_datos/
                restaurarDirectorio(f, BackupConfig.getDirectorioParadox());

            } else if (n.startsWith("logs_") && n.endsWith(".txt")) {
                // Logs: copia el .txt devolviéndole la extensión .json
                restaurarLogsDesdeText(f, BackupConfig.getArchivoLogsJson());

            } else if (n.startsWith("archivos_seguros_") && f.isDirectory()) {
                restaurarDirectorio(f, BackupConfig.getDirectorioArchivosSeguro());
            }
        }
        System.out.println("[Archivos] Restauración completada.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // db4o: comprime el directorio completo (storage/) en un ZIP
    // ─────────────────────────────────────────────────────────────────────────

    private void backupDb4oComoZip(File dirOrigen, String nombre, File destino,
            List<File> lista) throws IOException {
        if (!dirOrigen.exists() || !dirOrigen.isDirectory()) {
            System.out.println("[Archivos] Directorio db4o no encontrado: "
                    + dirOrigen.getAbsolutePath() + " — se omite.");
            return;
        }
        File zipDestino = new File(destino, nombre + ".zip");
        new CompressionManager().comprimirDirectorio(dirOrigen, zipDestino);
        lista.add(zipDestino);
        System.out.println("[Archivos] db4o → " + zipDestino.getName()
                + " (" + formatearTamano(zipDestino.length()) + ")");
    }

    private void restaurarDb4oDesdeZip(File zipBackup, File dirDestino) throws IOException {
        System.out.println("[Archivos] Restaurando db4o desde: " + zipBackup.getName());
        if (dirDestino.exists()) eliminarDirectorio(dirDestino.toPath());
        new CompressionManager().descomprimirArchivo(zipBackup, dirDestino);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Logs: JSON → .txt (mismo contenido, extensión cambiada)
    // ─────────────────────────────────────────────────────────────────────────

    private void backupLogsComoTxt(File jsonOrigen, String nombre, File destino,
            List<File> lista) throws IOException {
        if (!jsonOrigen.exists()) {
            System.out.println("[Archivos] Fichero de logs no encontrado: "
                    + jsonOrigen.getName() + " — se omite.");
            return;
        }
        File txtDestino = new File(destino, nombre + ".txt");
        Files.copy(jsonOrigen.toPath(), txtDestino.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);
        lista.add(txtDestino);
        System.out.println("[Archivos] Logs  → " + txtDestino.getName()
                + " (" + formatearTamano(txtDestino.length()) + ")");
    }

    private void restaurarLogsDesdeText(File txtBackup, File jsonDestino) throws IOException {
        System.out.println("[Archivos] Restaurando logs desde: " + txtBackup.getName());
        if (jsonDestino.getParentFile() != null && !jsonDestino.getParentFile().exists())
            jsonDestino.getParentFile().mkdirs();
        Files.copy(txtBackup.toPath(), jsonDestino.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers genéricos
    // ─────────────────────────────────────────────────────────────────────────

    private void backupDirectorio(File origen, String nombre, File destino,
            List<File> lista) throws IOException {
        if (!origen.exists() || !origen.isDirectory()) {
            System.out.println("[Archivos] Directorio no encontrado: "
                    + origen.getName() + " — se omite.");
            return;
        }
        File d = new File(destino, nombre);
        copiarDirectorio(origen.toPath(), d.toPath());
        lista.add(d);
        System.out.println("[Archivos] " + origen.getName() + " → " + d.getName()
                + " (" + formatearTamano(calcularTamano(d)) + ")");
    }

    private void restaurarDirectorio(File backup, File destino) throws IOException {
        System.out.println("[Archivos] Restaurando directorio: " + destino.getName());
        if (destino.exists()) eliminarDirectorio(destino.toPath());
        copiarDirectorio(backup.toPath(), destino.toPath());
    }

    private void copiarDirectorio(Path origen, Path destino) throws IOException {
        Files.walkFileTree(origen, new SimpleFileVisitor<Path>() {
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                Path t = destino.resolve(origen.relativize(dir));
                if (!Files.exists(t)) Files.createDirectories(t);
                return FileVisitResult.CONTINUE;
            }
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.copy(file,
                        destino.resolve(origen.relativize(file)),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void eliminarDirectorio(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException { Files.delete(file); return FileVisitResult.CONTINUE; }
            public FileVisitResult postVisitDirectory(Path d, IOException exc)
                    throws IOException { Files.delete(d); return FileVisitResult.CONTINUE; }
        });
    }

    private long calcularTamano(File dir) throws IOException {
        final long[] s = {0};
        if (dir.exists())
            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
                public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    s[0] += a.size();
                    return FileVisitResult.CONTINUE;
                }
            });
        return s[0];
    }

    private String formatearTamano(long b) {
        if (b < 1024) return b + " B";
        if (b < 1048576) return String.format("%.2f KB", b / 1024.0);
        return String.format("%.2f MB", b / 1048576.0);
    }
}
