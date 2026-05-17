package apigenerica.config;

import apigenerica.service.ColaOperaciones;
import apigenerica.service.ParadoxCache;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Gestiona las conexiones JDBC al motor de base de datos Paradox.
 *
 * Paradox no soporta pool de conexiones al estilo HikariCP, por lo que
 * se mantiene UNA sola conexión compartida protegida por los ReentrantLock
 * que ya usan ParadoxCache y ColaOperaciones.
 *
 * Driver requerido: paradoxdriver-X.X.X.jar (añadir a lib/ y al classpath).
 * URL JDBC:        jdbc:paradox:/<ruta_al_directorio_que_contiene_los_DB>
 *
 * @author Grupo1
 */
public class ConexionParadox {

    // ── Configuración ────────────────────────────────────────────────────────
    /**
     * Ruta al directorio que contiene los ficheros .DB de Paradox.
     * Puede ser absoluta (C:/datos/paradox) o relativa al working-directory.
     * Ajusta este valor según el entorno de despliegue.
     */
    private static final String RUTA_PARADOX = "paradox_data";
    private static final int LIMITE_QUERIES_CONEXION = 45;

    // ── Estado interno ───────────────────────────────────────────────────────
    private static Connection conexion;
    private static ParadoxCache cache;
    private static ColaOperaciones cola;
    private static final AtomicInteger contadorQueries = new AtomicInteger(0);
    private static final ReentrantLock lockReinicio = new ReentrantLock();

    // ── Inicialización ───────────────────────────────────────────────────────

    /**
     * Abre la conexión JDBC con Paradox y construye los servicios dependientes
     * (ParadoxCache y ColaOperaciones).
     *
     * Llama a este método UNA SOLA VEZ al arrancar la API, después de
     * ConexionMysql.inicializar() y antes de crear los controladores.
     *
     * Ejemplo en ApiGenerica.main():
     * <pre>
     *   ConexionMysql.inicializar();
     *   ConexionParadox.inicializar();       // ← añadir esta línea
     * </pre>
     */
    public static synchronized void inicializar() {
        try {
            // Cargar el driver explícitamente (necesario en Java 8 sin ServiceLoader)
            Class.forName("com.googlecode.paradox.Driver");

            // Abrir conexión: jdbc:paradox:/<ruta>
            // La barra inicial es obligatoria en la URL del driver paradoxdriver.
            String url = "jdbc:paradox:/" + RUTA_PARADOX;
            conexion = DriverManager.getConnection(url);
            conexion.setAutoCommit(false);
            contadorQueries.set(0);

            // Construir los servicios que dependen de Paradox
            cache = new ParadoxCache(RUTA_PARADOX);
            cola  = new ColaOperaciones(conexion);

            System.out.println("[Paradox] Conexion establecida → " + url);

        } catch (ClassNotFoundException e) {
            System.err.println("[Paradox] Driver no encontrado. " +
                "Asegurate de incluir paradoxdriver-X.X.X.jar en lib/ y en el classpath.");
            throw new RuntimeException("Driver Paradox no disponible", e);

        } catch (SQLException e) {
            System.err.println("[Paradox] Error al conectar: " + e.getMessage());
            throw new RuntimeException("No se pudo conectar a Paradox", e);
        }
    }

    // ── Getters públicos ─────────────────────────────────────────────────────

    public static Connection getConexion() {
        try {
            asegurarConexion();
        } catch (Exception e) {
            System.err.println("[ConexionParadox] Error obteniendo conexion: " + e.getMessage());
        }
        if (conexion == null) {
            throw new IllegalStateException(
                "[Paradox] ConexionParadox no ha sido inicializada. " +
                "Llama a ConexionParadox.inicializar() al arrancar la API.");
        }
        return conexion;
    }

    public static void contarUso() {
        int n = contadorQueries.incrementAndGet();
        if (n >= LIMITE_QUERIES_CONEXION) {
            System.out.println("[ConexionParadox] Limite alcanzado (" + n + "/" + LIMITE_QUERIES_CONEXION + "). Proxima llamada renovara la conexion.");
        } else {
            System.out.println("[ConexionParadox] queries=" + n + "/" + LIMITE_QUERIES_CONEXION);
        }
    }

    public static ParadoxCache getCache() {
        if (cache == null) {
            throw new IllegalStateException(
                "[Paradox] ConexionParadox no ha sido inicializada.");
        }
        return cache;
    }

    public static ColaOperaciones getCola() {
        if (cola == null) {
            throw new IllegalStateException(
                "[Paradox] ConexionParadox no ha sido inicializada.");
        }
        return cola;
    }

    public static synchronized void cerrar() {
        if (conexion != null) {
            try {
                conexion.close();
                System.out.println("[Paradox] Conexion cerrada.");
            } catch (SQLException e) {
                System.err.println("[Paradox] Error al cerrar la conexion: " + e.getMessage());
            } finally {
                conexion = null;
                cache    = null;
                cola     = null;
            }
        }
    }

    /** Comprueba si la conexion sigue viva sin llamar a isValid(),
     *  ya que el driver Paradox no implementa ese metodo. */
    private static boolean conexionViva() {
        if (conexion == null) return false;
        try {
            if (conexion.isClosed()) return false;
            // Prueba ligera: crear y cerrar un Statement vacio
            try (Statement st = conexion.createStatement()) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static void asegurarConexion() throws Exception {
        boolean necesitaRenovar = !conexionViva() || contadorQueries.get() >= LIMITE_QUERIES_CONEXION;
        if (!necesitaRenovar) return;

        lockReinicio.lock();
        try {
            // Re-check dentro del lock para evitar doble renovacion
            if (!conexionViva() || contadorQueries.get() >= LIMITE_QUERIES_CONEXION) {
                renovarConexion();
            }
        } finally {
            lockReinicio.unlock();
        }
    }

    private static void renovarConexion() {
        int prev = contadorQueries.get();
        if (conexion != null) {
            try { conexion.close(); } catch (Exception ignored) {}
            conexion = null;
        }
        try {
            Class.forName("com.googlecode.paradox.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("[ConexionParadox] Driver Paradox no encontrado: " + e.getMessage(), e);
        }
        try {
            String url = "jdbc:paradox:/" + RUTA_PARADOX;
            conexion = DriverManager.getConnection(url);
            conexion.setAutoCommit(false);
            contadorQueries.set(0);
            System.out.println("[ConexionParadox] Conexion renovada. Queries usadas antes: " + prev
                    + " (limite: " + LIMITE_QUERIES_CONEXION + ")");
        } catch (SQLException e) {
            throw new RuntimeException("[ConexionParadox] Error conectando a Paradox: " + e.getMessage(), e);
        }
    }
}