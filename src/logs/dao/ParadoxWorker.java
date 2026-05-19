package logs.dao;

import java.io.File;
import java.sql.*;

/**
 * Programa auxiliar que se ejecuta en un SUBPROCESO INDEPENDIENTE (JVM nueva)
 * para cada operación contra Paradox.
 *
 * POR QUÉ EXISTE:
 *   El driver HXTT evaluación limita a 50 queries GLOBALES por proceso JVM,
 *   no por conexión. Abrir conexiones nuevas no resetea ese contador.
 *   La única forma de resetear el límite sin pagar la licencia es usar
 *   una JVM nueva por operación: cada subproceso tiene su propio contador
 *   a 0 y muere al terminar la operación.
 *
 * FIX CRÍTICO — asegurarTabla:
 *   En lugar de usar meta.getTables() (que con HXTT frecuentemente no
 *   detecta tablas ya existentes → intenta crearla → error → aborta la
 *   conexión entera), ahora hacemos directamente el CREATE TABLE y
 *   capturamos la excepción "tabla ya existe". Así la conexión nunca
 *   queda en estado de error y los INSERT/SELECT se ejecutan correctamente.
 *
 * FIX — URL JDBC:
 *   Construida con getAbsolutePath() normalizado a barras forward, más
 *   robusto que toURI() en algunos entornos Windows/NetBeans.
 *
 * TABLAS GESTIONADAS:
 *   - LOGS              → comandos: INSERT, SELECT_ALL, INIT_MAX_ID, CREATE_TABLE
 *   - paradox_sensibles → comandos: SENS_INSERT, SENS_UPDATE_PK, SENS_SELECT,
 *                                   SENS_SELECT_ALL_TABLE, SENS_DELETE, SENS_INIT
 *   - meta_cache        → comandos: META_GET, META_INSERT, META_DELETE, META_INIT
 *
 * PROTOCOLO DE SALIDA (stdout):
 *   OK:<resultado>   o   ERROR:<mensaje>
 */
public class ParadoxWorker {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("ERROR:Se requieren al menos 2 argumentos: <carpeta> <comando>");
            return;
        }

        String carpeta = args[0];
        new File(carpeta).mkdirs();

        // Construir URL JDBC robusta: normalizar a barras forward
        String rutaNorm = new File(carpeta).getAbsolutePath().replace("\\", "/");
        String url = "jdbc:paradox:///" + rutaNorm;

        String cmd = args[1].toUpperCase();

        try {
            Class.forName("com.hxtt.sql.paradox.ParadoxDriver");
        } catch (ClassNotFoundException e) {
            System.out.println("ERROR:Driver no encontrado: " + e.getMessage());
            return;
        }

        switch (cmd) {
            // ── LOGS ──────────────────────────────────────────────────────────
            case "INSERT":
                doLogsInsert(args, url);
                break;
            case "SELECT_ALL":
                doLogsSelectAll(url);
                break;
            case "INIT_MAX_ID":
                doLogsInitMaxId(url);
                break;
            case "CREATE_TABLE":
                doLogsCreateTable(url);
                break;

            // ── paradox_sensibles ─────────────────────────────────────────────
            case "SENS_INSERT":
                doSensInsert(args, url);
                break;
            case "SENS_UPDATE_PK":
                doSensUpdatePk(args, url);
                break;
            case "SENS_SELECT":
                doSensSelect(args, url);
                break;
            case "SENS_SELECT_ALL_TABLE":
                doSensSelectAllTable(args, url);
                break;
            case "SENS_DELETE":
                doSensDelete(args, url);
                break;
            case "SENS_INIT":
                doSensInit(url);
                break;

            // ── meta_cache ────────────────────────────────────────────────────
            case "META_GET":
                doMetaGet(args, url);
                break;
            case "META_INSERT":
                doMetaInsert(args, url);
                break;
            case "META_DELETE":
                doMetaDelete(args, url);
                break;
            case "META_INIT":
                doMetaInit(url);
                break;

            default:
                System.out.println("ERROR:Comando desconocido: " + cmd);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOGS
    // ══════════════════════════════════════════════════════════════════════════

    private static void doLogsInsert(String[] args, String url) {
        if (args.length < 7) {
            System.out.println("ERROR:INSERT requiere 5 argumentos: id usuario operacion tabla descripcion");
            return;
        }
        int id;
        try {
            id = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            System.out.println("ERROR:ID no válido: " + args[2]);
            return;
        }
        String usuario     = decode(args[3]);
        String operacion   = decode(args[4]);
        String tabla       = decode(args[5]);
        String descripcion = decode(args[6]);
        Timestamp ahora    = new Timestamp(System.currentTimeMillis());

        try (Connection con = DriverManager.getConnection(url, "", "")) {
            asegurarTablaLogs(con);
            String sql = "INSERT INTO LOGS (id, usuario, operacion, tabla_afectada, descripcion, fecha_hora) "
                       + "VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt      (1, id);
                ps.setString   (2, usuario);
                ps.setString   (3, operacion);
                ps.setString   (4, tabla);
                ps.setString   (5, descripcion);
                ps.setTimestamp(6, ahora);
                ps.executeUpdate();
            }
            System.out.println("OK:" + id + ":" + ahora.toString());
        } catch (SQLException e) {
            System.out.println("ERROR:" + e.getMessage().replace("\n", " "));
        }
    }

    private static void doLogsSelectAll(String url) {
        StringBuilder sb = new StringBuilder("[");
        try (Connection con = DriverManager.getConnection(url, "", "")) {
            asegurarTablaLogs(con);
            String sql = "SELECT id, usuario, operacion, tabla_afectada, descripcion, fecha_hora "
                       + "FROM LOGS ORDER BY id DESC";
            try (PreparedStatement ps = con.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) sb.append(",");
                    first = false;
                    sb.append("[")
                      .append(jsonStr(rs.getString("id")))             .append(",")
                      .append(jsonStr(rs.getString("usuario")))         .append(",")
                      .append(jsonStr(rs.getString("operacion")))       .append(",")
                      .append(jsonStr(rs.getString("tabla_afectada")))  .append(",")
                      .append(jsonStr(rs.getString("descripcion")))     .append(",")
                      .append(jsonStr(rs.getString("fecha_hora")))
                      .append("]");
                }
            }
        } catch (SQLException e) {
            System.out.println("ERROR:" + e.getMessage().replace("\n", " "));
            return;
        }
        sb.append("]");
        System.out.println(sb.toString());
    }

    private static void doLogsInitMaxId(String url) {
        try (Connection con = DriverManager.getConnection(url, "", "")) {
            asegurarTablaLogs(con);
            try (PreparedStatement ps = con.prepareStatement("SELECT MAX(id) FROM LOGS");
                 ResultSet rs = ps.executeQuery()) {
                int max = 0;
                if (rs.next() && rs.getObject(1) != null) {
                    max = rs.getInt(1);
                }
                System.out.println("MAX:" + max);
            }
        } catch (SQLException e) {
            System.out.println("ERROR:" + e.getMessage().replace("\n", " "));
        }
    }

    private static void doLogsCreateTable(String url) {
        try (Connection con = DriverManager.getConnection(url, "", "")) {
            asegurarTablaLogs(con);
            System.out.println("OK:tabla logs lista");
        } catch (SQLException e) {
            System.out.println("ERROR:" + e.getMessage().replace("\n", " "));
        }
    }

    /**
     * Garantiza que la tabla LOGS exista.
     * FIX CRÍTICO: intenta CREATE TABLE y captura "ya existe" en lugar de
     * usar getTables() que con HXTT evaluación es poco fiable.
     */
    private static void asegurarTablaLogs(Connection con) throws SQLException {
        try (Statement stmt = con.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE logs (" +
                "id             INTEGER,"     +
                "usuario        VARCHAR(100)," +
                "operacion      VARCHAR(20),"  +
                "tabla_afectada VARCHAR(80),"  +
                "descripcion    VARCHAR(255)," +
                "fecha_hora     TIMESTAMP"     +
                ")"
            );
            System.err.println("[ParadoxWorker] Tabla LOGS creada por primera vez.");
        } catch (SQLException e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("already") || msg.contains("exist") || msg.contains("duplicate")) {
                return;
            }
            throw e;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // paradox_sensibles
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * SENS_INSERT <tablaId> <columnaId> <pk> <valorEncriptado>
     * Hace DELETE previo para simular REPLACE (Paradox no soporta REPLACE).
     */
    private static void doSensInsert(String[] args, String url) {
        if (args.length < 6) {
            System.out.println("ERROR:SENS_INSERT requiere: carpeta SENS_INSERT tablaId columnaId pk valor");
            return;
        }
        long tablaId, columnaId;
        try {
            tablaId   = Long.parseLong(args[2]);
            columnaId = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            System.out.println("ERROR:tablaId/columnaId no validos");
            return;
        }
        String pk    = decode(args[4]);
        String valor = decode(args[5]);

        try (Connection con = DriverManager.getConnection(url, "", "")) {
            con.setAutoCommit(true);
            asegurarTablaSensibles(con);
            // DELETE previo (REPLACE semantics — Paradox no soporta REPLACE)
            try (PreparedStatement del = con.prepareStatement(
                    "DELETE FROM paradox_sensibles WHERE tabla_id=? AND columna_id=? AND pk=?")) {
                del.setLong  (1, tablaId);
                del.setLong  (2, columnaId);
                del.setString(3, pk);
                del.executeUpdate();
            }
            String sql = "INSERT INTO paradox_sensibles (tabla_id, columna_id, pk, valor) VALUES (?,?,?,?)";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setLong  (1, tablaId);
                ps.setLong  (2, columnaId);
                ps.setString(3, pk);
                ps.setString(4, valor);
                ps.executeUpdate();
            }
            System.out.println("OK:SENS_INSERT");
        } catch (SQLException e) {
            System.out.println("ERROR:" + e.getMessage().replace("\n", " "));
        }
    }

    /**
     * SENS_UPDATE_PK <tablaId> <pkReal>
     * Cambia todas las filas con pk='PENDING' de esa tabla al pkReal.
     */
    private static void doSensUpdatePk(String[] args, String url) {
        if (args.length < 4) {
            System.out.println("ERROR:SENS_UPDATE_PK requiere: carpeta SENS_UPDATE_PK tablaId pkReal");
            return;
        }
        long tablaId;
        try {
            tablaId = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            System.out.println("ERROR:tablaId no valido");
            return;
        }
        String pkReal = decode(args[3]);

        try (Connection con = DriverManager.getConnection(url, "", "")) {
            con.setAutoCommit(true);
            asegurarTablaSensibles(con);
            String sql = "UPDATE paradox_sensibles SET pk=? WHERE tabla_id=? AND pk='PENDING'";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, pkReal);
                ps.setLong  (2, tablaId);
                ps.executeUpdate();
            }
            System.out.println("OK:SENS_UPDATE_PK");
        } catch (SQLException e) {
            System.out.println("ERROR:" + e.getMessage().replace("\n", " "));
        }
    }

    /**
     * SENS_SELECT <tablaId> <columnaId> <pk>
     * Devuelve OK:<valorEncode> o OK:NULL si no existe.
     */
    private static void doSensSelect(String[] args, String url) {
        if (args.length < 5) {
            System.out.println("ERROR:SENS_SELECT requiere: carpeta SENS_SELECT tablaId columnaId pk");
            return;
        }
        long tablaId, columnaId;
        try {
            tablaId   = Long.parseLong(args[2]);
            columnaId = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            System.out.println("ERROR:tablaId/columnaId no validos");
            return;
        }
        String pk = decode(args[4]);

        try (Connection con = DriverManager.getConnection(url, "", "")) {
            con.setAutoCommit(true);
            asegurarTablaSensibles(con);
            String sql = "SELECT valor FROM paradox_sensibles WHERE tabla_id=? AND columna_id=? AND pk=?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setLong  (1, tablaId);
                ps.setLong  (2, columnaId);
                ps.setString(3, pk);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String v = rs.getString("valor");
                    System.out.println("OK:" + encode(v != null ? v : ""));
                } else {
                    System.out.println("OK:NULL");
                }
            }
        } catch (SQLException e) {
            System.out.println("ERROR:" + e.getMessage().replace("\n", " "));
        }
    }

    /**
     * SENS_SELECT_ALL_TABLE <tablaId>
     * Devuelve OK:[[columnaId,pk,valor],...] para todas las filas de esa tabla.
     */
    private static void doSensSelectAllTable(String[] args, String url) {
        if (args.length < 3) {
            System.out.println("ERROR:SENS_SELECT_ALL_TABLE requiere: carpeta SENS_SELECT_ALL_TABLE tablaId");
            return;
        }
        long tablaId;
        try {
            tablaId = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            System.out.println("ERROR:tablaId no valido");
            return;
        }

        try (Connection con = DriverManager.getConnection(url, "", "")) {
            con.setAutoCommit(true);
            asegurarTablaSensibles(con);
            String sql = "SELECT columna_id, pk, valor FROM paradox_sensibles WHERE tabla_id=? ORDER BY pk";
            StringBuilder sb = new StringBuilder("[");
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setLong(1, tablaId);
                ResultSet rs = ps.executeQuery();
                boolean first = true;
                while (rs.next()) {
                    if (!first) sb.append(",");
                    first = false;
                    sb.append("[")
                      .append(jsonStr(rs.getString("columna_id"))).append(",")
                      .append(jsonStr(rs.getString("pk"))).append(",")
                      .append(jsonStr(rs.getString("valor")))
                      .append("]");
                }
            }
            sb.append("]");
            System.out.println("OK:" + sb.toString());
        } catch (SQLException e) {
            System.out.println("ERROR:" + e.getMessage().replace("\n", " "));
        }
    }

    /**
     * SENS_DELETE <tablaId> <pk>
     * Elimina todas las columnas sensibles de un registro (cuando se borra la fila).
     */
    private static void doSensDelete(String[] args, String url) {
        if (args.length < 4) {
            System.out.println("ERROR:SENS_DELETE requiere: carpeta SENS_DELETE tablaId pk");
            return;
        }
        long tablaId;
        try {
            tablaId = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            System.out.println("ERROR:tablaId no valido");
            return;
        }
        String pk = decode(args[3]);

        try (Connection con = DriverManager.getConnection(url, "", "")) {
            con.setAutoCommit(true);
            asegurarTablaSensibles(con);
            String sql = "DELETE FROM paradox_sensibles WHERE tabla_id=? AND pk=?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setLong  (1, tablaId);
                ps.setString(2, pk);
                ps.executeUpdate();
            }
            System.out.println("OK:SENS_DELETE");
        } catch (SQLException e) {
            System.out.println("ERROR:" + e.getMessage().replace("\n", " "));
        }
    }

    /** SENS_INIT — solo asegura que la tabla paradox_sensibles exista. */
    private static void doSensInit(String url) {
        try (Connection con = DriverManager.getConnection(url, "", "")) {
            con.setAutoCommit(true);
            asegurarTablaSensibles(con);
            System.out.println("OK:tabla paradox_sensibles lista");
        } catch (SQLException e) {
            System.out.println("ERROR:" + e.getMessage().replace("\n", " "));
        }
    }

    private static void asegurarTablaSensibles(Connection con) throws SQLException {
        try (Statement stmt = con.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE paradox_sensibles (" +
                "tabla_id   INTEGER,"              +
                "columna_id INTEGER,"              +
                "pk         VARCHAR(50),"          +
                "valor      VARCHAR(255)"          +
                ")"
            );
            System.err.println("[ParadoxWorker] Tabla paradox_sensibles creada por primera vez.");
        } catch (SQLException e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("already") || msg.contains("exist") || msg.contains("duplicate")) {
                return;
            }
            throw e;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // meta_cache
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * META_GET <nombreLogico>
     * Devuelve OK:<datosEncode> o OK:NULL si no existe.
     */
    private static void doMetaGet(String[] args, String url) {
        if (args.length < 3) {
            System.out.println("ERROR:META_GET requiere: carpeta META_GET nombreLogico");
            return;
        }
        String nombre = decode(args[2]);

        try (Connection con = DriverManager.getConnection(url, "", "")) {
            con.setAutoCommit(true);
            asegurarTablaMetaCache(con);
            String sql = "SELECT datos FROM meta_cache WHERE nombre_logico=?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, nombre);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String d = rs.getString("datos");
                    System.out.println("OK:" + encode(d != null ? d : ""));
                } else {
                    System.out.println("OK:NULL");
                }
            }
        } catch (SQLException e) {
            System.out.println("ERROR:" + e.getMessage().replace("\n", " "));
        }
    }

    /**
     * META_INSERT <nombreLogico> <datos>
     * Hace DELETE previo para evitar duplicados, luego INSERT.
     */
    private static void doMetaInsert(String[] args, String url) {
        if (args.length < 4) {
            System.out.println("ERROR:META_INSERT requiere: carpeta META_INSERT nombreLogico datos");
            return;
        }
        String nombre = decode(args[2]);
        String datos  = decode(args[3]);

        try (Connection con = DriverManager.getConnection(url, "", "")) {
            con.setAutoCommit(true);
            asegurarTablaMetaCache(con);
            try (PreparedStatement del = con.prepareStatement(
                    "DELETE FROM meta_cache WHERE nombre_logico=?")) {
                del.setString(1, nombre);
                del.executeUpdate();
            }
            String sql = "INSERT INTO meta_cache (nombre_logico, datos) VALUES (?, ?)";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, nombre);
                ps.setString(2, datos);
                ps.executeUpdate();
            }
            System.out.println("OK:META_INSERT");
        } catch (SQLException e) {
            System.out.println("ERROR:" + e.getMessage().replace("\n", " "));
        }
    }

    /**
     * META_DELETE <nombreLogico>
     */
    private static void doMetaDelete(String[] args, String url) {
        if (args.length < 3) {
            System.out.println("ERROR:META_DELETE requiere: carpeta META_DELETE nombreLogico");
            return;
        }
        String nombre = decode(args[2]);

        try (Connection con = DriverManager.getConnection(url, "", "")) {
            con.setAutoCommit(true);
            asegurarTablaMetaCache(con);
            String sql = "DELETE FROM meta_cache WHERE nombre_logico=?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, nombre);
                ps.executeUpdate();
            }
            System.out.println("OK:META_DELETE");
        } catch (SQLException e) {
            System.out.println("ERROR:" + e.getMessage().replace("\n", " "));
        }
    }

    /** META_INIT — solo asegura que la tabla meta_cache exista. */
    private static void doMetaInit(String url) {
        try (Connection con = DriverManager.getConnection(url, "", "")) {
            con.setAutoCommit(true);
            asegurarTablaMetaCache(con);
            System.out.println("OK:tabla meta_cache lista");
        } catch (SQLException e) {
            System.out.println("ERROR:" + e.getMessage().replace("\n", " "));
        }
    }

    private static void asegurarTablaMetaCache(Connection con) throws SQLException {
        try (Statement stmt = con.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE meta_cache (" +
                "nombre_logico VARCHAR(150)," +
                "datos         VARCHAR(255)"  +
                ")"
            );
            System.err.println("[ParadoxWorker] Tabla meta_cache creada por primera vez.");
        } catch (SQLException e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("already") || msg.contains("exist") || msg.contains("duplicate")) {
                return;
            }
            throw e;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Utilidades compartidas
    // ══════════════════════════════════════════════════════════════════════════

    private static String jsonStr(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                       .replace("\r", "\\r")
                       .replace("\t", "\\t") + "\"";
    }

    /**
     * Codifica un valor para pasarlo seguro como argumento de línea de comandos.
     * Reemplaza espacios y caracteres problemáticos por tokens inequívocos.
     */
    public static String encode(String s) {
        if (s == null || s.isEmpty()) return "__EMPTY__";
        return s.replace("\\", "__BACKSLASH__")
                .replace(" ",  "__SPACE__")
                .replace("\n", "__NL__")
                .replace("\r", "__CR__");
    }

    private static String decode(String arg) {
        if ("__EMPTY__".equals(arg)) return "";
        return arg.replace("__SPACE__",     " ")
                  .replace("__NL__",        "\n")
                  .replace("__CR__",        "\r")
                  .replace("__BACKSLASH__", "\\");
    }
}