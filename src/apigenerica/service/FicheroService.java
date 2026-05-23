/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.service;

import apigenerica.config.ConexionDb4o;
import apigenerica.config.ConexionMysql;
import apigenerica.model.Fichero;
import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import io.javalin.http.UploadedFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import core.util.Encriptador; // Utilizacion de la clase de Encriptador
import core.util.ManejadorArchivos; // Utilizacion de la clase de ManejadorArchivo
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Encargado de almacenar y recuperar los archivos en db4o.
 *
 * Logica de almacenamiento (Por peso): - Archivo ≤ 20 MB -> Comprimido +
 * Encriptado -> fichero .enc en disco, ruta en db4o
 *
 * En ambos casos el contenido siempre sale Encriptado del servicio; la
 * recuperacion Invierte el proceso de forma transparente para el controller
 * (que no cambia)
 *
 * @author Grupo1
 */
public class FicheroService {

    // Ruta FUERA de OneDrive para evitar conflictos de sincronización que bloquean el fichero.
    // C:\Temp es un directorio de sistema que no se sincroniza con la nube.
    private static final String RUTA;

    static {
        String temp = System.getProperty("java.io.tmpdir");
        RUTA = temp + java.io.File.separator + "erp_ficheros.db4o";
        System.out.println("[Db4o] Usando ruta: " + RUTA);
    }

    private final ObjectContainer db = ConexionDb4o.getConexion(RUTA);

    // Tamaño máximo permitido por fichero almacenado en db4o
    private static final long TAMANO_MAX = 20L * 1024 * 1024; // 20MB

    // Carpeta donde se guardan los archivos grandes cifrados (.enc).
    // Separa de CARPETA_FICHERO original para distinguir claramente los .enc
    // de cualquier fichero plano que pudiera haber antes de la integracion
    private static final String CARPETA_SEGURO = "storage/ficheros_seguros/";

    /**
     * Almacena un archivo subido por la API. El controller llama a este metodo
     */
    public void guardar(String uuid, String tabla, UploadedFile file) {
        try (InputStream is = file.getContent()) {

            // Leemos todos los bytes en memoria una sola vez.
            // Para archivos muy grandes (> TAMANO_MAX) esto podría ser un problema de RAM,
            // pero es necesario porque la detección de tipo y la compresión necesitan el array.
            byte[] bytesOriginales = inputStreamToByteArray(is);

            // Deteccion de tipo por magic bytes
            // cuando el Content-Type es generico ("application/octet-Stream") o el
            // Nombre no tiene extension, intentamos identificar el formato real
            // Leyendo los primeros bytes del archivo. Si no lo reconoce se guarda
            // Como "Desconocido": El archivo se acepta igualmente, nunca se va a rechazar
            String mimeEfectivo = file.getContentType();
            String tipoDetectado = null;
            boolean sinExtension = !file.getFilename().contains(".");
            boolean mimeGenerico = mimeEfectivo == null
                    || mimeEfectivo.isEmpty()
                    || mimeEfectivo.equalsIgnoreCase("application/octet-stream");

            if (sinExtension || mimeGenerico) {
                tipoDetectado = ManejadorArchivos.detectarTipo(bytesOriginales);
                System.out.println("[FicheroService] Tipo detectado por magic bytes: "
                        + tipoDetectado);

                // Si seguimos sin mime usable, marcamos uno neutro para que el
                // controller pueda hacer el Content-Disposition en la descarga.
                if (mimeGenerico) {
                    mimeEfectivo = "application/octet-stream";
                }
            }

            // CORRECCIÓN: usamos bytesOriginales.length en lugar de file.getSize()
            // porque Javalin puede devolver -1 en getSize() para peticiones multipart,
            // lo que causaba que archivos grandes se tratasen como pequeños y se
            // guardasen en db4o con contenido=null en vez de en disco.
            boolean esGrande = bytesOriginales.length > TAMANO_MAX;

            Fichero f;
            // CAMINO A: archivo grande -> se va a disco cifrado
            // Antes: se guardaba en disco SIN cifrar(texto plano).
            // Ahora: Compresion + Encriptacion -> .enc en disco, solo la ruta en db4o
            // Si el fichero excede del tamaño máximo se guarda en el disco
            if (esGrande) {
                String ruta = guardarEnDiscoCifrado(uuid, tabla, bytesOriginales, file.getFilename());
                // Se almacena el objeto con la ruta del fichero en db4o
                f = new Fichero(
                        uuid,
                        file.getFilename(),
                        mimeEfectivo,
                        file.getSize(),
                        ruta, // ruta del .enc; isEnDisco() devuelve true
                        LocalDateTime.now()
                );
            } else {
                // CAMINO B: archivo pequeño -> bytes cifrados en db4o
                // Antes: solo comprimia, y solo si el mime era "comprimible"
                // (texto, pdf, json, xml). Los binarios iba sin comprimir
                // y todo iba sin cifrar.
                // Ahora: Compresion + encriptacion siempre, sin importar el mime.
                // La compresion ayuda incluso a binarios; el cifrado es obligatorio
                // para todos los archivos del sistema.
                // Convertir a array de bytes para almacenar en db4o
                byte[] bytesFinales = ManejadorArchivos.comprimirYEncriptar(bytesOriginales);

                if (bytesFinales == null) {
                    throw new RuntimeException("Error al comprimir/encriptar el fichero.");
                }

                // Se almacena el objeto con su contenido en db4o
                f = new Fichero(
                        uuid,
                        file.getFilename(),
                        mimeEfectivo,
                        file.getSize(),
                        bytesFinales,
                        true, // comprimido=true (siempre ahora)
                        LocalDateTime.now()
                );
            }

            // Guardamos el tipo detectado si lo obtuvimos
            if (tipoDetectado != null) {
                f.setTipoDetectado(tipoDetectado);
            }
            db.store(f);
            db.commit();

            // Registrar el fichero en MySQL para poder listarlo/auditarlo.
            // CORRECCIÓN: pasamos esGrande directamente en vez de f.isEnDisco()
            // para evitar el bug donde isEnDisco() devolvía false en archivos grandes
            // porque dependía de getSize() que Javalin reportaba mal.
            // La ruta solo se pasa si es grande; si es pequeño va null (está en db4o).
            registrarEnMysql(uuid, file.getFilename(), mimeEfectivo, tipoDetectado,
                    file.getSize(), esGrande, esGrande ? f.getRuta() : null, tabla);

        } catch (IOException e) {
            throw new RuntimeException("Error al procesar el fichero", e);
        }
    }

    /**
     * Guardar el contenido del fichero en el disco, si supera el tamaño máximo
     * permitido
     *
     * Comprime, cifra y escribe el archivo en el disco como .enc Antes este
     * metodo se llamaba guardarEnDisco() y copiaba el InputStream tal cual eñ
     * disco sin ningun tratamiento. Ahora recibo los bytes ya leidos, aplica
     * compresion + encriptacion y guarda el resultado con extenxion .enc en
     * carpeta CARPETA_SEGURO.
     *
     * @return Ruta en la que se guardo el fichero .enc generado
     * @throws IOException
     */
    private String guardarEnDiscoCifrado(String uuid, String tabla, byte[] bytesOriginales, String nombreOriginal) throws IOException {

        byte[] bytesSeguro = ManejadorArchivos.comprimirYEncriptar(bytesOriginales);

        if (bytesSeguro == null) {
            throw new IOException("Fallo al comprimir/encriptar el archivo grande.");
        }

        // Creamos la carpeta si no existe
        Path directorio = Paths.get(CARPETA_SEGURO, tabla);
        Files.createDirectories(directorio);

        // El nombre fisico incluye el uuid para evitar colisiones y la extension
        // .enc para dejar claro que el contenido estaa cifrado
        String nombreFisico = uuid + "_" + nombreOriginal + ".enc";
        Path rutaArchivo = directorio.resolve(nombreFisico);

        Files.write(rutaArchivo, bytesSeguro);
        System.out.println("[FicheroService] archivo grande guardado cifrado en: " + rutaArchivo);
        return rutaArchivo.toString();
    }

    public InputStream obtenerStream(String uuid) throws IOException {
        Fichero file = obtenerMetadatos(uuid);
        if (file == null) {
            return null;
        }

        if (file.isEnDisco()) {

            // Camino A: archivo grande desde disco
            // Antes: se devolvia un stream directo al archivo fisico sin descidrar.
            // Ahora: Leemos el .enc decrypt + decompress, devolvemos los bytes originales.
            byte[] bytesEncriptados = Files.readAllBytes(Paths.get(file.getRuta()));
            byte[] bytesOriginales = ManejadorArchivos.desencriptarYDescomprimir(bytesEncriptados);

            if (bytesOriginales == null) {
                throw new IOException("Error al descifrar el archivo en disco: " + file.getRuta());
            }

            System.out.println("[FicheroService] Archivo grande recuperado desde disco y descifrado.");
            return new ByteArrayInputStream(bytesOriginales);
        } else if (file.isComprimido()) {

            // Camino B: archivo pequeño desde db4o
            // Antes: solo descomprimia(los datos no estaban cifrado).
            // Ahora: decrypt + descompress porque guardar() siempre cifra.
            // Si por cualquier razon el objetivo viene de antes de la integracion
            // (sin cifrado), desencriptaYDescomprimir fallará y se relanza como
            // IOException para que el controller devuelva 500 con mensaje claro.
            if (file.getContenido() == null) {
                if (file.getRuta() != null && !file.getRuta().isEmpty()) {
                    System.out.println("[FicheroService] Contenido null pero hay ruta — leyendo desde disco (fix bug getSize).");
                    byte[] bytesEncriptados = Files.readAllBytes(Paths.get(file.getRuta()));
                    byte[] bytesOriginales = ManejadorArchivos.desencriptarYDescomprimir(bytesEncriptados);
                    if (bytesOriginales == null) {
                        throw new IOException("Error al descifrar desde ruta alternativa: " + file.getRuta());
                    }
                    return new ByteArrayInputStream(bytesOriginales);
                }
                throw new IOException("Contenido null y sin ruta en disco para uuid=" + uuid);
            }
            byte[] bytesOriginales = ManejadorArchivos.desencriptarYDescomprimir(file.getContenido());
            if (bytesOriginales == null) {
                throw new IOException("Error al descifrar/descomprimir uuid=" + uuid);
            }
            System.out.println("[FicheroService] Archivo pequeño recuperado de db4o y descifrado.");
            return new ByteArrayInputStream(bytesOriginales);

        } else {
            if (file.getContenido() == null) {
                throw new IOException("Fichero sin contenido ni ruta para uuid=" + uuid);
            }
            // Archivo sin comprimir y sin cifrar: solo puede darse con objeto
            // persistido antes de esta integracion. Los servimos tal cual para
            // no romper compatibilidad con datos antiguos.
            return new ByteArrayInputStream(file.getContenido());
        }
    }

    /**
     * Convertir un InputStream a array de bytes. Necesario porque db4o no puede
     * almacenar un InputStream
     *
     * @param is InputStream a convertir
     * @return Array de Bytes resultante
     * @throws IOException
     */
    private byte[] inputStreamToByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead; // Posición actual de lectura
        byte[] data = new byte[16384]; // Buffer de 16KB
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    /**
     * Eliminar un fichero de db4o
     *
     * @param uuid UUID del archivo
     */
    public void eliminar(String uuid) {
        ObjectSet<Fichero> result = db.queryByExample(new Fichero(uuid));
        if (!result.hasNext()) {
            System.err.println("[FicheroService] uuid " + uuid + " no encontrado en db4o.");
            eliminarDeMysql(uuid);
            return;
        }
        Fichero f = result.next();
        // NUEVO: activar el objeto completo antes de borrar (db4o necesita esto)
        db.activate(f, 5);

        System.out.println("[FicheroService] eliminar() — ruta: " + f.getRuta()
                + " | enDisco: " + f.isEnDisco()
                + " | contenido null: " + (f.getContenido() == null));

        // Borrar fichero físico si tiene ruta
        String ruta = f.getRuta();
        if (ruta != null && !ruta.isEmpty()) {
            try {
                boolean borrado = Files.deleteIfExists(Paths.get(ruta));
                System.out.println("[FicheroService] .enc " + (borrado ? "BORRADO" : "no existia"));
            } catch (IOException e) {
                System.err.println("[FicheroService] No se pudo borrar el .enc: " + e.getMessage());
            }
        }

        db.delete(f);
        db.commit();
        System.out.println("[FicheroService] Objeto eliminado de db4o.");
        eliminarDeMysql(uuid);
    }

    /**
     * Recuperar metadatos de un archivo a partir de su UUID (nombre, fecha de
     * subida, mime type...)
     *
     * @param uuid UUID del archivo
     * @return Objeto Fichero o null si no se encontró
     */
    public Fichero obtenerMetadatos(String uuid) {
        ObjectSet<Fichero> result = db.queryByExample(new Fichero(uuid));
        return result.hasNext() ? result.next() : null;
    }

    /**
     * Inserta una fila en erp_ficheros (MySQL) cada vez que se guarda un
     * archivo. Si falla el registro en MySQL el fichero ya está en db4o/disco:
     * se loguea el error pero NO se lanza excepción para no deshacer la subida.
     */
    private void registrarEnMysql(String uuid, String nombre, String mime,
            String tipoDetectado, long tamano, boolean enDisco, String ruta, String tabla) {
        String sql
                = "INSERT IGNORE INTO erp_sistema.erp_ficheros "
                + "(uuid, nombre_original, mime_type, tipo_detectado, tamano_bytes, esta_en_disco, ruta_disco, tabla_origen) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = ConexionMysql.getConexion(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, nombre);
            ps.setString(3, mime);
            ps.setString(4, tipoDetectado);   // puede ser null si tenía extensión conocida
            ps.setLong(5, tamano);
            ps.setInt(6, enDisco ? 1 : 0);
            ps.setString(7, ruta);            // null si está en db4o
            ps.setString(8, tabla);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[FicheroService] No se pudo registrar el fichero en MySQL: " + e.getMessage());
        }
    }

    private void eliminarDeMysql(String uuid) {
        String sql = "DELETE FROM erp_sistema.erp_ficheros WHERE uuid = ?";
        try (Connection conn = ConexionMysql.getConexion(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[FicheroService] No se pudo borrar el fichero de MySQL: " + e.getMessage());
        }
    }
}
