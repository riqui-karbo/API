package logs.service;

import logs.dao.LogDAO;
import logs.monitor.DatabaseChangeMonitor;

import java.util.List;

/**
 * Capa de servicio para logs.
 *
 * Oculta el DAO y el Monitor al controlador REST.
 * Aquí se centraliza cualquier lógica de negocio sobre los logs
 * (validaciones, paginación, filtrado avanzado, etc.).
 *
 * Uso típico desde el controlador REST (Spark / Spring / JAX-RS):
 * <pre>
 *   LogService.inicializar();                          // al arrancar la app
 *   List<String[]> todos  = LogService.obtenerTodos();
 *   List<String[]> filtro = LogService.buscar("DELETE");
 *   LogService.registrar("api", "INSERT", "tabla", "Descripción");
 * </pre>
 */
public class LogService {

    private static DatabaseChangeMonitor monitor;

    // ---------------------------------------------------------------
    // Ciclo de vida
    // ---------------------------------------------------------------

    /**
     * Inicializa el DAO y arranca el monitor de cambios.
     * Llamar UNA SOLA VEZ al arrancar la aplicación.
     */
    public static void inicializar() {
        LogDAO.inicializar();
        monitor = DatabaseChangeMonitor.iniciar();
        System.out.println("[LogService] Listo.");
    }

    /**
     * Detiene el monitor de cambios.
     * Llamar al apagar la aplicación (shutdown hook).
     */
    public static void detener() {
        if (monitor != null) {
            monitor.detener();
            System.out.println("[LogService] Monitor detenido.");
        }
    }

    /**
     * Reinicializa el servicio de logs tras una restauración de backup.
     *
     * Pasos:
     *  1. Detiene el monitor actual (evita logs espurios durante la restauración).
     *  2. Recarga el LogDAO desde el JSON restaurado (el buffer RAM queda sincronizado).
     *  3. Arranca un monitor nuevo con un snapshot fresco de la BD restaurada,
     *     de modo que el próximo ciclo de polling no compare estados incongruentes.
     *
     * Llamar SIEMPRE después de BackupOrchestrator.restaurarBackupCompleto().
     */
    public static synchronized void reinicializar() {
        // 1. Parar el monitor viejo para que no genere logs de "cambios" fantasma
        detener();

        // 2. Recargar LogDAO desde el JSON que acaba de restaurarse
        LogDAO.reinicializar();

        // 3. Arrancar un monitor nuevo con snapshot alineado al estado restaurado
        monitor = DatabaseChangeMonitor.iniciar();
        System.out.println("[LogService] Reiniciado tras restauración de backup.");
    }

    // ---------------------------------------------------------------
    // Operaciones expuestas a la API
    // ---------------------------------------------------------------

    /**
     * Devuelve todos los logs en orden descendente (más reciente primero).
     * Cada elemento es String[6]: {id, usuario, operacion, tabla, descripcion, timestamp}
     */
    public static List<String[]> obtenerTodos() {
        return LogDAO.obtenerTodos();
    }

    /**
     * Búsqueda simple: devuelve los logs donde cualquier campo contenga
     * el texto indicado (case-insensitive).
     */
    public static List<String[]> buscar(String filtro) {
        return LogDAO.buscar(filtro);
    }

    /**
     * Registra un log manualmente (útil para que la propia API REST
     * audite sus propias operaciones).
     *
     * @param usuario       Nombre del usuario o sistema que realiza la acción.
     * @param operacion     INSERT | UPDATE | DELETE | SELECT | OTRO
     * @param tabla         Tabla o recurso afectado.
     * @param descripcion   Descripción legible de la operación.
     */
    public static void registrar(String usuario, String operacion,
                                  String tabla,   String descripcion) {
        LogDAO.registrar(usuario, operacion, tabla, descripcion);
    }

    /**
     * Convierte la lista de String[] en una lista de LogEntry,
     * más cómoda para serializar a JSON con cualquier librería.
     */
    public static List<LogEntry> obtenerTodosComoEntradas() {
        return convertir(obtenerTodos());
    }

    public static List<LogEntry> buscarComoEntradas(String filtro) {
        return convertir(buscar(filtro));
    }

    private static List<LogEntry> convertir(List<String[]> filas) {
        List<LogEntry> lista = new java.util.ArrayList<>(filas.size());
        for (String[] f : filas) lista.add(new LogEntry(f));
        return lista;
    }

    // ---------------------------------------------------------------
    // DTO
    // ---------------------------------------------------------------

    /**
     * Representación de un registro de log lista para serializar.
     * Compatible con cualquier librería JSON (Gson, Jackson, etc.).
     */
    public static class LogEntry {
        public final int    id;
        public final String usuario;
        public final String operacion;
        public final String tablaAfectada;
        public final String descripcion;
        public final String fechaHora;

        public LogEntry(String[] fila) {
            this.id            = parseInt(fila[0]);
            this.usuario       = fila[1];
            this.operacion     = fila[2];
            this.tablaAfectada = fila[3];
            this.descripcion   = fila[4];
            this.fechaHora     = fila[5];
        }

        private static int parseInt(String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; }
        }
    }
}