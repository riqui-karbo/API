/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package core.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// Utilidad para leer y exportar los ficheros binarios que se guardan en DB4O.
// DB4O almacena los ficheros como byte[] dentro de RegistroDinamico.archivoBinario.
// Esta clase hace dos cosas:
//   1. Leer un fichero del disco y convertirlo a byte[] para pasárselo a DB4O.
//   2. Recuperar el byte[] de DB4O y escribirlo de vuelta a disco como fichero.
//
// NO hace compresión ni cifrado de los bytes; eso se puede añadir después.
// PUEDES CAMBIAR: añade compresión GZip antes de store() y descompresión tras query()
// si los ficheros son muy grandes y queréis ahorrar espacio en el .db4o.
public class ManejadorArchivos {

    // Ruta de carpeta donde se exportan los ficheros recuperados de DB4O.
    // PUEDES CAMBIAR "ficheros_exportados" por la ruta absoluta que necesitéis,
    // por ejemplo: "C:/GestionEmpresa/exportados" o System.getProperty("user.home") + "/exportados".
    private static final String CARPETA_EXPORTACION = "ficheros_exportados";

    // Tamaño máximo permitido por fichero al subir a DB4O (en bytes).
    // PUEDES CAMBIAR el valor: 20 * 1024 * 1024 = 20 MB, ajusta según vuestra RAM.
    public static final long MAX_BYTES = 20L * 1024 * 1024; // 20 MB

    // INTEGRACION PRUEBA: Carpeta para archivos encriptados en disco
    // =============================================================================
    private static final String CARPETA_ARCHIVOS = "./archivos_seguros/";

    // Lee un fichero del disco y devuelve su contenido como byte[].
    // Este byte[] es el que luego se guarda en RegistroDinamico.archivoBinario.
    // Devuelve null si el fichero no existe, es demasiado grande o hay error de lectura.
    // PUEDES CAMBIAR el nombre a "leerFichero" o "cargarBinario".
    public static byte[] leerDesdeDisco(File archivo) {

        // Comprobamos que el fichero existe antes de intentar leerlo.
        if (archivo == null || !archivo.exists()) {
            System.err.println("ManejadorArchivos: el fichero no existe — " + archivo);
            return null;
        }

        // Rechazamos ficheros demasiado grandes para no saturar la memoria.
        if (archivo.length() > MAX_BYTES) {
            System.err.println("ManejadorArchivos: fichero demasiado grande (" + (archivo.length() / 1024) + " KB). Límite: " + (MAX_BYTES / 1024) + " KB");
            return null;
        }

        try {
            // Files.readAllBytes carga el fichero completo en memoria de una vez.
            // Para ficheros muy grandes podríais necesitar un InputStream con buffer.
            byte[] contenido = Files.readAllBytes(archivo.toPath());
            System.out.println("ManejadorArchivos: leídos " + contenido.length + " bytes de '" + archivo.getName() + "'");
            return contenido;

        } catch (IOException e) {
            System.err.println("ManejadorArchivos ERROR (lectura): " + e.getMessage());
            return null;
        }
    }

    // Escribe un byte[] recuperado de DB4O de vuelta a disco como fichero.
    // Útil para "descargar" o previsualizar un fichero que estaba en la bóveda.
    // Devuelve true si la escritura fue exitosa.
    // PUEDES CAMBIAR el nombre a "exportarFichero" o "guardarEnDisco".
    public static boolean escribirEnDisco(byte[] contenido, String nombreFichero) {

        if (contenido == null || contenido.length == 0) {
            System.err.println("ManejadorArchivos: no hay bytes que escribir.");
            return false;
        }

        try {
            // Creamos la carpeta de exportación si no existe todavía.
            Path carpeta = Paths.get(CARPETA_EXPORTACION);
            if (!Files.exists(carpeta)) {
                Files.createDirectories(carpeta);
            }

            // Construimos la ruta completa del fichero de salida.
            // PUEDES CAMBIAR: añadir un timestamp al nombre para evitar sobrescribir ficheros.
            Path destino = carpeta.resolve(nombreFichero);
            Files.write(destino, contenido);

            System.out.println("ManejadorArchivos: fichero exportado a '" + destino.toAbsolutePath() + "'");
            return true;

        } catch (IOException e) {
            System.err.println("ManejadorArchivos ERROR (escritura): " + e.getMessage());
            return false;
        }
    }

    // Comprime los bytes para que ocupen menos espacio en db4o
    public static byte[] comprimir(byte[] datos) throws IOException {
        if (datos == null || datos.length == 0) {
            return datos;
        }
        ByteArrayOutputStream obj = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(obj)) {
            gzip.write(datos);
        }
        return obj.toByteArray();
    }

    // Descomprime los bytes al recuperarlos de db4o
    public static byte[] descomprimir(byte[] datosComprimidos) throws IOException {
        if (datosComprimidos == null || datosComprimidos.length == 0) {
            return datosComprimidos;
        }
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(datosComprimidos))) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
    }

    // Detecta el tipo de fichero leyendo los primeros bytes ("magic bytes").
    // Es más fiable que fiarse solo de la extensión, porque un usuario puede renombrar un fichero.
    // Devuelve una etiqueta legible: "PDF", "JPG", "PNG", "ZIP", o "DESCONOCIDO".
    // PUEDES CAMBIAR: añadir más tipos al bloque if-else (DOCX, MP4, etc.).
    public static String detectarTipo(byte[] contenido) {

        if (contenido == null || contenido.length < 4) {
            return "DESCONOCIDO";
        }

        // Los "magic bytes" son los primeros bytes que identifican el formato.
        // PDF empieza siempre por %PDF (0x25 0x50 0x44 0x46).
        if (contenido[0] == 0x25 && contenido[1] == 0x50 && contenido[2] == 0x44 && contenido[3] == 0x46) {
            return "PDF";
        }

        // JPG empieza por FF D8 FF.
        if ((contenido[0] & 0xFF) == 0xFF && (contenido[1] & 0xFF) == 0xD8 && (contenido[2] & 0xFF) == 0xFF) {
            return "JPG";
        }

        // PNG empieza por 89 50 4E 47.
        if ((contenido[0] & 0xFF) == 0x89 && contenido[1] == 0x50 && contenido[2] == 0x4E && contenido[3] == 0x47) {
            return "PNG";
        }

        // ZIP (y DOCX/XLSX por dentro son ZIP) empieza por 50 4B 03 04.
        if (contenido[0] == 0x50 && contenido[1] == 0x4B && contenido[2] == 0x03 && contenido[3] == 0x04) {
            return "ZIP/DOCX/XLSX";
        }

        // Si no coincide con ninguno de los anteriores, no podemos identificarlo.
        return "DESCONOCIDO";
    }

    public static byte[] descomprimirZipEnMemoria(byte[] datosZip) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(datosZip))) {
            ZipEntry entrada = zis.getNextEntry();
            if (entrada != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
                return baos.toByteArray();
            }
        }
        return datosZip; // Si no es zip o está vacío, devuelve el original
    }

    // Detecta la extensión real basada en el nombre del archivo original
    public static String obtenerExtension(String nombreArchivo) {
        if (nombreArchivo == null || !nombreArchivo.contains(".")) {
            return "bin";
        }
        return nombreArchivo.substring(nombreArchivo.lastIndexOf(".") + 1);
    }

    // =============================================================================
    // INICIO INTEGRACION: NUEVOS METODOS comprimirYEncriptar + desencriptarYDescomprimir
    // =============================================================================
    // Comprime con GZIP y luego encripta con AES-256. Orden optimo: primero comprimir.
    public static byte[] comprimirYEncriptar(byte[] datosOriginales) {
        try {
            // Paso 1: Reducir el tamaño
            byte[] comprimido = comprimir(datosOriginales);
            // paso 2: Protegemos con la encriptacion
            byte[] encriptado = Encriptador.encriptar(comprimido);
            System.out.println("[SEGURIDAD] Archivo comprimido (" + datosOriginales.length
                    + " -> " + comprimido.length + ") y encriptado (" + encriptado.length + ")");
            return encriptado;
        } catch (Exception e) {
            System.err.println("[SEGURIDAD] Error en comprimirYEncriptar: " + e.getMessage());
            return null;
        }
    }

    // Desencipta con AES-256 y luego descomprime GZIP. Orden inverso al guardado
    public static byte[] desencriptarYDescomprimir(byte[] datosEncriptados) {
        try {
            // Paso 1: Quitar proteccion
            byte[] desencriptado = Encriptador.desencriptar(datosEncriptados);
            if (desencriptado == null) {
                System.err.println("[SEGURIDAD] fallo al desencriptar");
                return null;
            }
            // Paso 2: Recuperar estado original
            byte[] descomprimido = descomprimir(desencriptado);
            System.out.println("[SEGURIDAD] archivo desencriptado y descomprimido: "
                    + descomprimido.length + " bytes");
            return descomprimido;
        } catch (Exception e) {
            System.err.println("[SEGURIDAD] Error en desencriptarYDescomprimir: " + e.getMessage());
            return null;
        }
    }

    // Guardar bytes en disco con extension .enc (encriptado). Crea carpeta si no existe.
    public static String guardarEnDiscoSeguro(byte[] datosEncriptados, String nombreBase) throws IOException {
        Path carpeta = Paths.get(CARPETA_ARCHIVOS);
        if (!Files.exists(carpeta)) {
            // Creamos toda la ruta
            Files.createDirectories(carpeta);
        }
        // Extenxion que indica encriptado
        String nombreSeguro = nombreBase + ".enc";
        Path destino = carpeta.resolve(nombreSeguro);
        // Escribe byte en disco
        Files.write(destino, datosEncriptados);
        System.out.println("[DISCO] Archivo seguro guardado en: " + destino);
        // Devuelve ruta completa para guardar en DB4O
        return destino.toString();
    }

    // Lee byte desde disco (Archivo .enc encriptado).
    public static byte[] leerDesdeDiscoSeguro(String rutaCompleta) throws IOException {
        Path origen = Paths.get(rutaCompleta);
        if (!Files.exists(origen)) {
            System.err.println("[DISCO] Archivo no encontrado: " + rutaCompleta);
            return null;
        }
        byte[] bytes = Files.readAllBytes(origen);
        System.out.println("[DISCO] archivo leido desde disco: " + bytes.length + " bytes");
        return bytes;
    }
}
