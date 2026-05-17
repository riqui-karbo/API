package logs.monitor;

import apigenerica.config.ConexionMysql;
import apigenerica.config.AppConfig;
import logs.dao.LogDAO;

import java.sql.*;
import java.util.*;

/**
 * Hilo daemon que detecta cambios en MySQL cada INTERVALO_MS ms
 * y los registra en LogDAO.
 *
 * ── Capa 1: tablas de metadatos (siempre vigiladas) ──────────────────────────
 * erp_meta_tablas, erp_modulos, erp_meta_columnas, erp_users, erp_roles
 * Snapshot: Map<id, Map<campo,valor>>
 * Detecta: INSERT / UPDATE (campo a campo) / DELETE
 *
 * ── Capa 2: tablas de usuario (se descubren dinámicamente) ───────────────────
 * Cualquier tabla física de db_metadatos que NO empiece por "erp_"
 * Al arrancar y en cada ciclo se escanea INFORMATION_SCHEMA para descubrir
 * tablas nuevas y añadirlas al seguimiento sin reiniciar la app.
 */
public class DatabaseChangeMonitor implements Runnable {

    private static final long   INTERVALO_MS    = 5_000;
    private static final String USUARIO_EXTERNO = "sistema/web";

    private volatile boolean activo = true;

    // Capa 1: metadatos  tabla -> { id -> { campo -> valor } }
    private final Map<String, Map<Integer, Map<String, String>>> snapMeta = new LinkedHashMap<>();

    // Capa 2: tablas de usuario  tabla -> { pk_string -> { campo -> valor } }
    private final Map<String, Map<String, Map<String, String>>> snapUser = new LinkedHashMap<>();
    // tabla -> lista de columnas PK
    private final Map<String, List<String>> pkCache = new LinkedHashMap<>();

    // ── Arranque ──────────────────────────────────────────────────────────────

    public static DatabaseChangeMonitor iniciar() {
        DatabaseChangeMonitor m = new DatabaseChangeMonitor();
        Thread t = new Thread(m, "DB-Change-Monitor");
        t.setDaemon(true);
        t.start();
        System.out.println("[Monitor] Iniciado — polling cada " + INTERVALO_MS / 1000 + " s");
        return m;
    }

    public void detener() {
        activo = false;
    }

    // ── Bucle ─────────────────────────────────────────────────────────────────

    @Override
    public void run() {
        try {
            cargarSnapshotInicial();
        } catch (Exception e) {
            System.err.println("[Monitor] Error en snapshot inicial: " + e.getMessage());
        }

        while (activo) {
            try {
                Thread.sleep(INTERVALO_MS);
                detectarCambios();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[Monitor] Error en ciclo: " + e.getMessage());
            }
        }
        System.out.println("[Monitor] Detenido.");
    }

    // ── Snapshot inicial ──────────────────────────────────────────────────────

    private void cargarSnapshotInicial() throws SQLException {
        //CAPA 1: Metadatos del sistema (siempre en erp_sistema)
        try (Connection conSys = ConexionMysql.getConexion(AppConfig.DB_SISTEMA)) {
            snapMeta.put("erp_meta_tablas",     leerTablaGeneral(conSys, "erp_meta_tablas", "id"));
            snapMeta.put("erp_modulos",         leerTablaGeneral(conSys, "erp_modulos", "id"));
            snapMeta.put("erp_meta_columnas",   leerTablaGeneral(conSys, "erp_meta_columnas", "id"));
            snapMeta.put("erp_users",           leerTablaGeneral(conSys, "erp_users", "id"));
            snapMeta.put("erp_roles",           leerTablaGeneral(conSys, "erp_roles", "id"));
        }

        //CAPA 2: Tablas de usuario (en erp_empresa)
        try (Connection conCli = ConexionMysql.getConexion(AppConfig.DB_CLIENTE)) {
            for (String tabla : descubrirTablasUsuario(conCli)) {
                List<String> pks = obtenerPKs(conCli, tabla);
                pkCache.put(tabla, pks);
                snapUser.put(tabla, leerTablaUsuario(conCli, tabla, pks));
                System.out.println("[Monitor] Vigilando tabla de usuario: " + tabla + " (PK: " + pks + ")");
            }
        }
    }

    // ── Ciclo de detección ────────────────────────────────────────────────────

    private void detectarCambios() throws SQLException {
        // 🔹 CAPA 1: Metadatos del sistema
        try (Connection conSys = ConexionMysql.getConexion(AppConfig.DB_SISTEMA)) {
            Map<String, Map<Integer, Map<String, String>>> actualMeta = new LinkedHashMap<>();
            actualMeta.put("erp_meta_tablas",     leerTablaGeneral(conSys, "erp_meta_tablas", "id"));
            actualMeta.put("erp_modulos",         leerTablaGeneral(conSys, "erp_modulos", "id"));
            actualMeta.put("erp_meta_columnas",   leerTablaGeneral(conSys, "erp_meta_columnas", "id"));
            actualMeta.put("erp_users",           leerTablaGeneral(conSys, "erp_users", "id"));
            actualMeta.put("erp_roles",           leerTablaGeneral(conSys, "erp_roles", "id"));

            for (String tabla : actualMeta.keySet()) {
                compararMeta(tabla, snapMeta.get(tabla), actualMeta.get(tabla));
                snapMeta.put(tabla, actualMeta.get(tabla));
            }
        }

        // 🔹 CAPA 2: Tablas de usuario
        try (Connection conCli = ConexionMysql.getConexion(AppConfig.DB_CLIENTE)) {
            Set<String> tablasActuales = descubrirTablasUsuario(conCli);
            Set<String> tablasNuevas   = new LinkedHashSet<>();

            for (String tabla : tablasActuales) {
                if (!snapUser.containsKey(tabla)) {
                    List<String> pks = obtenerPKs(conCli, tabla);
                    pkCache.put(tabla, pks);
                    Map<String, Map<String, String>> filas = leerTablaUsuario(conCli, tabla, pks);
                    snapUser.put(tabla, filas);
                    tablasNuevas.add(tabla);
                    System.out.println("[Monitor] Nueva tabla detectada: " + tabla + " (" + filas.size() + " fila(s))");
                    for (Map.Entry<String, Map<String, String>> entrada : filas.entrySet()) {
                        LogDAO.registrar(USUARIO_EXTERNO, "INSERT", tabla,
                                "Fila existente en nueva tabla '" + tabla + "' [pk=" + entrada.getKey() + "] " + resumenFila(entrada.getValue()));
                    }
                }
            }

            List<String> tablasEliminadas = new ArrayList<>();
            for (String tabla : snapUser.keySet()) {
                if (!tablasActuales.contains(tabla)) tablasEliminadas.add(tabla);
            }
            for (String tabla : tablasEliminadas) {
                LogDAO.registrar(USUARIO_EXTERNO, "DELETE", tabla,
                        "Tabla '" + tabla + "' eliminada físicamente de la base de datos");
                pkCache.remove(tabla);
                snapUser.remove(tabla);
                System.out.println("[Monitor] Tabla eliminada: " + tabla);
            }

            for (String tabla : tablasActuales) {
                if (tablasNuevas.contains(tabla) || !snapUser.containsKey(tabla)) continue;
                List<String> pks = pkCache.get(tabla);
                Map<String, Map<String, String>> anterior = snapUser.get(tabla);
                Map<String, Map<String, String>> actual = leerTablaUsuario(conCli, tabla, pks);
                compararUsuario(tabla, anterior, actual);
                snapUser.put(tabla, actual);
            }
        }
    }

    // ── Comparadores ─────────────────────────────────────────────────────────

    private void compararMeta(String tabla,
                              Map<Integer, Map<String, String>> anterior,
                              Map<Integer, Map<String, String>> actual) {
        if (anterior == null) return;
        for (int id : actual.keySet()) {
            if (!anterior.containsKey(id)) {
                LogDAO.registrar(USUARIO_EXTERNO, "INSERT", tabla,
                        buildInsertDescMeta(tabla, id, actual.get(id)));
                System.out.println("[Monitor] INSERT " + tabla + " id=" + id);
            }
        }
        for (int id : anterior.keySet()) {
            if (!actual.containsKey(id)) {
                LogDAO.registrar(USUARIO_EXTERNO, "DELETE", tabla,
                        buildDeleteDescMeta(tabla, id, anterior.get(id)));
                System.out.println("[Monitor] DELETE " + tabla + " id=" + id);
            }
        }
        for (int id : actual.keySet()) {
            if (!anterior.containsKey(id)) continue;
            List<String> diffs = diffCampos(anterior.get(id), actual.get(id));
            if (!diffs.isEmpty()) {
                String nombre = nombreIdentificadorMeta(tabla, actual.get(id));
                LogDAO.registrar(USUARIO_EXTERNO, "UPDATE", tabla,
                        "Actualizado externamente" + (nombre.isEmpty() ? "" : " '" + nombre + "'")
                        + " [" + String.join(", ", diffs) + "]");
                System.out.println("[Monitor] UPDATE " + tabla + " id=" + id + " -> " + diffs);
            }
        }
    }

    private void compararUsuario(String tabla,
                                 Map<String, Map<String, String>> anterior,
                                 Map<String, Map<String, String>> actual) {
        if (anterior == null) return;
        for (String pk : actual.keySet()) {
            if (!anterior.containsKey(pk)) {
                LogDAO.registrar(USUARIO_EXTERNO, "INSERT", tabla,
                        "Fila insertada en '" + tabla + "' [pk=" + pk + "] " + resumenFila(actual.get(pk)));
                System.out.println("[Monitor] INSERT datos " + tabla + " pk=" + pk);
            }
        }
        for (String pk : anterior.keySet()) {
            if (!actual.containsKey(pk)) {
                LogDAO.registrar(USUARIO_EXTERNO, "DELETE", tabla,
                        "Fila eliminada en '" + tabla + "' [pk=" + pk + "] " + resumenFila(anterior.get(pk)));
                System.out.println("[Monitor] DELETE datos " + tabla + " pk=" + pk);
            }
        }
        for (String pk : actual.keySet()) {
            if (!anterior.containsKey(pk)) continue;
            List<String> diffs = diffCampos(anterior.get(pk), actual.get(pk));
            if (!diffs.isEmpty()) {
                LogDAO.registrar(USUARIO_EXTERNO, "UPDATE", tabla,
                        "Fila modificada en '" + tabla + "' [pk=" + pk + "] [" + String.join(", ", diffs) + "]");
                System.out.println("[Monitor] UPDATE datos " + tabla + " pk=" + pk + " -> " + diffs);
            }
        }
    }

    // ── Lectura de BD ─────────────────────────────────────────────────────────

    private Set<String> descubrirTablasUsuario(Connection con) throws SQLException {
        Set<String> tablas = new LinkedHashSet<>();
        String sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                     "WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' " +
                     "AND TABLE_NAME NOT LIKE 'erp\\_%' ORDER BY TABLE_NAME";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, AppConfig.DB_CLIENTE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) tablas.add(rs.getString("TABLE_NAME"));
            }
        }
        return tablas;
    }

    private List<String> obtenerPKs(Connection con, String tabla) throws SQLException {
        List<String> pks = new ArrayList<>();
        String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE " +
                     "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? " +
                     "AND CONSTRAINT_NAME = 'PRIMARY' ORDER BY ORDINAL_POSITION";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, AppConfig.DB_CLIENTE);
            ps.setString(2, tabla);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) pks.add(rs.getString("COLUMN_NAME"));
            }
        }
        return pks;
    }

    private Map<String, Map<String, String>> leerTablaUsuario(Connection con,
                                                              String tabla,
                                                              List<String> pks) {
        Map<String, Map<String, String>> mapa = new LinkedHashMap<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT * FROM `" + tabla + "`");
             ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData meta   = rs.getMetaData();
            int               cols   = meta.getColumnCount();
            List<String>      columnas = new ArrayList<>();
            for (int i = 1; i <= cols; i++) columnas.add(meta.getColumnName(i));

            while (rs.next()) {
                Map<String, String> fila = new LinkedHashMap<>();
                for (String col : columnas) fila.put(col, nullSafe(rs.getString(col)));

                String clavePK;
                if (!pks.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (String pk : pks) {
                        if (sb.length() > 0) sb.append("|");
                        sb.append(nullSafe(rs.getString(pk)));
                    }
                    clavePK = sb.toString();
                } else {
                    clavePK = String.valueOf(fila.values().toString().hashCode());
                }
                mapa.put(clavePK, fila);
            }
        } catch (SQLException e) {
            System.err.println("[Monitor] No se pudo leer tabla '" + tabla + "': " + e.getMessage());
        }
        return mapa;
    }

    private Map<Integer, Map<String, String>> leerTablaGeneral(Connection con,
                                                               String tabla,
                                                               String campoId) throws SQLException {
        Map<Integer, Map<String, String>> mapa = new LinkedHashMap<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT * FROM `" + tabla + "`");
             ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData meta    = rs.getMetaData();
            int               cols    = meta.getColumnCount();
            List<String>      columnas = new ArrayList<>();
            for (int i = 1; i <= cols; i++) columnas.add(meta.getColumnName(i));

            while (rs.next()) {
                int                 id   = rs.getInt(campoId);
                Map<String, String> fila = new LinkedHashMap<>();
                for (String col : columnas) fila.put(col, nullSafe(rs.getString(col)));
                mapa.put(id, fila);
            }
        } catch (SQLException e) {
            System.err.println("[Monitor] No se pudo leer tabla sys '" + tabla + "': " + e.getMessage());
        }
        return mapa;
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private List<String> diffCampos(Map<String, String> antes, Map<String, String> despues) {
        List<String> diffs = new ArrayList<>();
        for (String campo : despues.keySet()) {
            String vA = nullSafe(antes != null ? antes.get(campo) : null);
            String vD = nullSafe(despues.get(campo));
            if (!vA.equals(vD)) diffs.add(campo + ": '" + vA + "' -> '" + vD + "'");
        }
        return diffs;
    }

    private String resumenFila(Map<String, String> fila) {
        if (fila == null || fila.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("{");
        int n = 0;
        for (Map.Entry<String, String> e : fila.entrySet()) {
            if (n++ >= 3) { sb.append(", ..."); break; }
            if (n > 1) sb.append(", ");
            sb.append(e.getKey()).append("='").append(e.getValue()).append("'");
        }
        return sb.append("}").toString();
    }

    private String buildInsertDescMeta(String tabla, int id, Map<String, String> v) {
        switch (tabla) {
            case "erp_meta_tablas": return "Tabla de metadatos creada externamente: '" + v.get("nombre_logico")
                    + "' (" + v.get("nombre_amigable") + ")";
            case "erp_modulos":   return "Modulo creado externamente: '" + v.get("nombre") + "'";
            case "erp_meta_columnas": return "Columna creada externamente: '" + v.get("nombre")
                    + "' tipo=" + v.get("tipo") + " nullable=" + v.get("nullable");
            case "erp_users":     return "Usuario creado externamente: email='" + v.get("email")
                    + "' rol=" + v.get("rol") + " activo=" + v.get("activo");
            case "erp_roles":     return "Rol creado externamente: '" + v.get("nombre")
                    + "' - " + v.get("descripcion");
            default:              return "Registro id=" + id + " insertado en " + tabla;
        }
    }

    private String buildDeleteDescMeta(String tabla, int id, Map<String, String> v) {
        String nombre = nombreIdentificadorMeta(tabla, v);
        return "Eliminado externamente de " + tabla
                + (nombre.isEmpty() ? "" : ": '" + nombre + "'") + " (id=" + id + ")";
    }

    private String nombreIdentificadorMeta(String tabla, Map<String, String> v) {
        switch (tabla) {
            case "erp_meta_tablas": return nullSafe(v.get("nombre_logico"));
            case "erp_modulos":   return nullSafe(v.get("nombre"));
            case "erp_meta_columnas": return nullSafe(v.get("nombre"));
            case "erp_users":     return nullSafe(v.get("email"));
            case "erp_roles":     return nullSafe(v.get("nombre"));
            default:              return "";
        }
    }

    private static String nullSafe(String s) { return s != null ? s : ""; }
}