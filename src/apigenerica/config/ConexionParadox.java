package apigenerica.config;

import apigenerica.service.ColaOperaciones;
import apigenerica.service.ParadoxCache;
import java.io.File;
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
 * Driver requerido: Paradox_JDBC30.jar (HXTT).
 * URL JDBC:        jdbc:paradox:///<ruta_al_directorio_que_contiene_los_DB>
 *
 * @author Grupo1
 */
public class ConexionParadox {

    // ── Configuración ────────────────────────────────────────────────────────
    /**
     * Ruta al DIRECTORIO que contiene los ficheros .DB de Paradox.
     * El driver HXTT apunta al directorio; cada tabla es un .DB dentro.
     * base_paradox.db se crea automáticamente en inicializar().
     */
    private static final String RUTA_PARADOX = "base_de_datos";
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
     * Si el directorio paradox_data no existe, lo crea automáticamente.
     *
     * Llama a este método UNA SOLA VEZ al arrancar la API, después de
     * ConexionMysql.inicializar() y antes de crear los controladores.
     */
    public static synchronized void inicializar() {
        try {
            // Crear el directorio si no existe
            crearDirectorioSiNoExiste();

            // Crear base_paradox.db fisicamente ANTES de abrir la conexion principal
            crearArchivoDB();

            // Cargar el driver HXTT Paradox explicitamente
            Class.forName("com.hxtt.sql.paradox.ParadoxDriver");

            // Abrir conexión: jdbc:paradox:///<ruta>
            String rutaNorm = new java.io.File(RUTA_PARADOX).getAbsolutePath().replace("\\", "/");
            String url = "jdbc:paradox:///" + rutaNorm;
            conexion = DriverManager.getConnection(url);
            conexion.setAutoCommit(false);
            contadorQueries.set(0);

            // Construir los servicios que dependen de Paradox
            cache = new ParadoxCache(RUTA_PARADOX);
            cola  = new ColaOperaciones(conexion);

            System.out.println("[Paradox] Conexion establecida → " + url);

        } catch (ClassNotFoundException e) {
            System.err.println("[Paradox] Driver no encontrado. " +
                "Asegurate de incluir Paradox_JDBC30.jar en lib/ y en el classpath.");
            throw new RuntimeException("Driver Paradox no disponible", e);

        } catch (SQLException e) {
            System.err.println("[Paradox] Error al conectar: " + e.getMessage());
            throw new RuntimeException("No se pudo conectar a Paradox", e);
        }
    }

    /**
     * Crea fisicamente el archivo base_paradox.db en base_de_datos/ si no existe.
     *
     * CRITICO: se abre una conexion PROPIA con autoCommit=true y se cierra
     * inmediatamente despues. Asi el driver HXTT escribe el .DB en disco
     * antes de que la conexion principal (autoCommit=false) la use.
     * Si se hace sobre la conexion principal el archivo nunca llega a disco.
     */
    private static void crearArchivoDB() {
        File archivo = new File(RUTA_PARADOX + File.separator + "base_paradox.db");
        if (archivo.exists()) {
            System.out.println("[Paradox] base_paradox.db ya existe — OK.");
            return;
        }
        Connection conTemp = null;
        try {
            Class.forName("com.hxtt.sql.paradox.ParadoxDriver");
            String rutaNorm = new File(RUTA_PARADOX).getAbsolutePath().replace("\\", "/");
            conTemp = DriverManager.getConnection("jdbc:paradox:///" + rutaNorm);
            conTemp.setAutoCommit(true);
            try (Statement stmt = conTemp.createStatement()) {
                stmt.executeUpdate(
                    "CREATE TABLE paradox_sensibles (" +
                    "tabla_id   INTEGER,"              +
                    "columna_id INTEGER,"              +
                    "pk         VARCHAR(50),"          +
                    "valor      VARCHAR(255)"          +
                    ")"
                );
            }
            System.out.println("[Paradox] base_paradox.db (tabla paradox_sensibles) creado en: " + archivo.getAbsolutePath());
        } catch (ClassNotFoundException e) {
            System.err.println("[Paradox] Driver no encontrado al crear base_paradox.db: " + e.getMessage());
        } catch (SQLException e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("already") || msg.contains("exist") || msg.contains("duplicate")) {
                System.out.println("[Paradox] base_paradox.db ya existia en BD — OK.");
            } else {
                System.err.println("[Paradox] Error creando base_paradox.db: " + e.getMessage());
            }
        } finally {
            if (conTemp != null) {
                try { conTemp.close(); } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * Crea el directorio base_de_datos/ si no existe.
     * No hace nada si ya existe.
     */
    private static void crearDirectorioSiNoExiste() {
        File dir = new File(RUTA_PARADOX);
        if (!dir.exists()) {
            boolean creado = dir.mkdirs();
            if (creado) {
                System.out.println("[Paradox] Directorio creado automaticamente: "
                        + dir.getAbsolutePath());
            } else {
                System.err.println("[Paradox] No se pudo crear el directorio: "
                        + dir.getAbsolutePath()
                        + ". Comprueba los permisos.");
            }
        } else {
            System.out.println("[Paradox] Directorio encontrado: " + dir.getAbsolutePath());
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
            Class.forName("com.hxtt.sql.paradox.ParadoxDriver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("[ConexionParadox] Driver Paradox no encontrado: " + e.getMessage(), e);
        }
        try {
            // Asegurarse de que el directorio sigue existiendo al renovar
            crearDirectorioSiNoExiste();
            String rutaNorm = new java.io.File(RUTA_PARADOX).getAbsolutePath().replace("\\", "/");
            String url = "jdbc:paradox:///" + rutaNorm;
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