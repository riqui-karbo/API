package apigenerica.backup;

import core.util.Encriptador;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class CompressionManager {

    /**
     * Comprime un directorio completo en un archivo ZIP
     */
    public void comprimirDirectorio(File dirOrigen, File zipDestino) throws IOException {
        if (!dirOrigen.exists()) {
            throw new FileNotFoundException("Directorio no encontrado: " + dirOrigen.getAbsolutePath());
        }
        if (zipDestino.getParentFile() != null && !zipDestino.getParentFile().exists()) {
            zipDestino.getParentFile().mkdirs();
        }

        System.out.println("[Compression] Comprimiendo " + dirOrigen.getName() + "...");
        long inicio = System.currentTimeMillis();

        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(zipDestino)))) {

            Path origenPath = dirOrigen.toPath();

            Files.walkFileTree(origenPath, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // Calcular ruta relativa dentro del ZIP
                    Path relativePath = origenPath.relativize(file);
                    String entryName = relativePath.toString().replace("\\", "/");

                    // ✅ CORRECCIÓN: setTime() se llama en ZipEntry, NO en ZipOutputStream
                    ZipEntry zipEntry = new ZipEntry(entryName);
                    zipEntry.setTime(attrs.lastModifiedTime().toMillis());
                    
                    zos.putNextEntry(zipEntry);
                    Files.copy(file, zos);
                    zos.closeEntry();

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!dir.equals(origenPath)) {
                        Path relativePath = origenPath.relativize(dir);
                        String entryName = relativePath.toString().replace("\\", "/") + "/";

                        ZipEntry zipEntry = new ZipEntry(entryName);
                        zipEntry.setTime(attrs.lastModifiedTime().toMillis());
                        
                        zos.putNextEntry(zipEntry);
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        long duracion = System.currentTimeMillis() - inicio;
        long tamanoOriginal = calcularTamanoDirectorio(dirOrigen);
        long tamanoComprimido = zipDestino.length();
        double ratio = tamanoOriginal > 0 ? 100.0 * (1.0 - ((double) tamanoComprimido / tamanoOriginal)) : 0;

        System.out.println("[Compression] ✅ Compresión completada en " + duracion + "ms");
        System.out.println("[Compression] Original: " + formatearTamano(tamanoOriginal) + 
                          " → Comprimido: " + formatearTamano(tamanoComprimido) + 
                          " (" + String.format("%.1f", ratio) + "% ahorro)");
    }

    /**
     * Descomprime un archivo ZIP en un directorio destino con protección Zip-Slip
     */
    public void descomprimirArchivo(File zip, File dirDestino) throws IOException {
        if (!zip.exists()) {
            throw new FileNotFoundException("ZIP no encontrado: " + zip.getAbsolutePath());
        }
        if (!dirDestino.exists() && !dirDestino.mkdirs()) {
            throw new IOException("No se pudo crear el directorio destino: " + dirDestino);
        }

        System.out.println("[Compression] Descomprimiendo " + zip.getName() + "...");
        long inicio = System.currentTimeMillis();
        byte[] buffer = new byte[8192];

        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zip)))) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File destFile = new File(dirDestino, entry.getName());
                String canonicalDest = dirDestino.getCanonicalPath();
                String canonicalFile = destFile.getCanonicalPath();

                // 🔐 Protección Zip-Slip: evita escritura fuera del directorio destino
                if (!canonicalFile.startsWith(canonicalDest + File.separator) && 
                    !canonicalFile.equals(canonicalDest)) {
                    throw new IOException("Entrada ZIP inválida (Zip-Slip): " + entry.getName());
                }

                if (entry.isDirectory()) {
                    if (!destFile.exists() && !destFile.mkdirs()) {
                        throw new IOException("No se pudo crear directorio: " + destFile);
                    }
                } else {
                    File parent = destFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IOException("No se pudo crear directorio padre: " + parent);
                    }
                    try (BufferedOutputStream bos = new BufferedOutputStream(
                            new FileOutputStream(destFile))) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            bos.write(buffer, 0, len);
                        }
                    }
                    destFile.setLastModified(entry.getTime());
                }
                zis.closeEntry();
            }
        }

        long duracion = System.currentTimeMillis() - inicio;
        System.out.println("[Compression] ✅ Descompresión completada en " + duracion + "ms");
    }

    /**
     * Cifra un archivo usando AES-256 vía Encriptador
     */
    public void cifrarArchivo(File archivoOrigen, File archivoDestino) throws Exception {
        if (!archivoOrigen.exists()) {
            throw new FileNotFoundException("Archivo no encontrado: " + archivoOrigen.getAbsolutePath());
        }
        System.out.println("[Encryption] Cifrando " + archivoOrigen.getName() + "...");
        long inicio = System.currentTimeMillis();

        byte[] datosOriginales = Files.readAllBytes(archivoOrigen.toPath());
        byte[] datosCifrados = Encriptador.encriptar(datosOriginales);
        
        if (datosCifrados == null) {
            throw new BackupException("Error al cifrar el archivo. Verifica la clave AES.");
        }

        Files.write(archivoDestino.toPath(), datosCifrados);

        long duracion = System.currentTimeMillis() - inicio;
        System.out.println("[Encryption] ✅ Cifrado completado en " + duracion + "ms");
    }

    /**
     * Descifra un archivo usando AES-256 vía Encriptador
     */
    public void descifrarArchivo(File archivoCifrado, File archivoDestino) throws Exception {
        if (!archivoCifrado.exists()) {
            throw new FileNotFoundException("Archivo cifrado no encontrado: " + archivoCifrado.getAbsolutePath());
        }
        System.out.println("[Encryption] Descifrando " + archivoCifrado.getName() + "...");
        long inicio = System.currentTimeMillis();

        byte[] datosCifrados = Files.readAllBytes(archivoCifrado.toPath());
        byte[] datosDescifrados = Encriptador.desencriptar(datosCifrados);
        
        if (datosDescifrados == null) {
            throw new BackupException("Error al descifrar. Verifica la clave AES.");
        }

        Files.write(archivoDestino.toPath(), datosDescifrados);

        long duracion = System.currentTimeMillis() - inicio;
        System.out.println("[Encryption] ✅ Descifrado completado en " + duracion + "ms");
    }

    /**
     * Comprime y opcionalmente cifra un directorio
     */
    public File comprimirYCifrar(File dirOrigen, File archivoFinal, boolean cifrar) throws Exception {
        if (cifrar) {
            File tempZip = new File(archivoFinal.getParentFile(), archivoFinal.getName() + ".tmp");
            comprimirDirectorio(dirOrigen, tempZip);
            cifrarArchivo(tempZip, archivoFinal);
            if (!tempZip.delete()) {
                System.err.println("[Compression] ⚠️ No se eliminó temporal: " + tempZip.getName());
            }
            return archivoFinal;
        } else {
            comprimirDirectorio(dirOrigen, archivoFinal);
            return archivoFinal;
        }
    }

    /**
     * Descifra y descomprime un archivo de backup
     */
    public void descifrarYDescomprimir(File archivoBackup, File dirDestino, boolean cifrado) throws Exception {
        if (cifrado) {
            File tempZip = new File(archivoBackup.getParentFile(), archivoBackup.getName() + ".dec.tmp");
            descifrarArchivo(archivoBackup, tempZip);
            descomprimirArchivo(tempZip, dirDestino);
            if (!tempZip.delete()) {
                System.err.println("[Compression] ⚠️ No se eliminó temporal: " + tempZip.getName());
            }
        } else {
            descomprimirArchivo(archivoBackup, dirDestino);
        }
    }

    /**
     * Calcula el tamaño total de un directorio
     */
    private long calcularTamanoDirectorio(File directorio) throws IOException {
        if (!directorio.exists() || !directorio.isDirectory()) {
            return 0;
        }
        final long[] tamano = {0};
        Files.walkFileTree(directorio.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                tamano[0] += attrs.size();
                return FileVisitResult.CONTINUE;
            }
        });
        return tamano[0];
    }

    /**
     * Formatea el tamaño en formato legible
     */
    private String formatearTamano(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}