package apigenerica.config;

import com.db4o.Db4oEmbedded;
import com.db4o.ObjectContainer;
import com.db4o.ext.DatabaseFileLockedException;

import java.io.File;

/**
 * Gestión de la conexión a la base de datos embebida db4o.
 *
 * Mejoras sobre la versión original:
 *  - Si el fichero está bloqueado (proceso anterior colgado), lo elimina y lo recrea.
 *  - Registra un shutdown hook para cerrar la BD limpiamente al detener la API.
 */
public class ConexionDb4o {

    private static ObjectContainer db;
    private static String rutaActual;

    public static void inicializar(String ruta) {
        rutaActual = ruta;
        abrirConexion(ruta);

        // Cierre limpio cuando la JVM se apague (Ctrl+C, stop desde NetBeans, etc.)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            cerrar();
            System.out.println("[Db4o] Conexion cerrada correctamente.");
        }));
    }

    private static void abrirConexion(String ruta) {
        try {
            db = Db4oEmbedded.openFile(Db4oEmbedded.newConfiguration(), ruta);
        } catch (DatabaseFileLockedException e) {
            // El fichero está bloqueado por un proceso anterior que no terminó bien.
            // Solución: borrar el fichero y abrirlo de nuevo (se pierde la sesión anterior).
            System.err.println("[Db4o] AVISO: fichero bloqueado. Eliminando y recreando: " + ruta);
            File f = new File(ruta);
            if (f.exists() && f.delete()) {
                System.out.println("[Db4o] Fichero eliminado. Abriendo BD nueva...");
                db = Db4oEmbedded.openFile(Db4oEmbedded.newConfiguration(), ruta);
            } else {
                throw new RuntimeException("[Db4o] No se pudo eliminar el fichero bloqueado: " + ruta, e);
            }
        }
    }

    public static ObjectContainer getConexion(String ruta) {
        if (db == null || db.ext().isClosed()) {
            abrirConexion(ruta);
        }
        return db;
    }

    /** Sobrecarga sin argumento: reutiliza la ruta ya configurada. */
    public static ObjectContainer getConexion() {
        return getConexion(rutaActual != null ? rutaActual : "ficheros.db4o");
    }

    public static void cerrar() {
        if (db != null && !db.ext().isClosed()) {
            db.close();
        }
    }
}
