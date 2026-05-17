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
            case "INSERT":
                doInsert(args, url);
                break;
            case "SELECT_ALL":
                doSelectAll(url);
                break;
            case "INIT_MAX_ID":
                doInitMaxId(url);
                break;
            case "CREATE_TABLE":
                doCreateTable(url);
                break;
            default:
                System.out.println("ERROR:Comando desconocido: " + cmd);
        }
    }

    private static void doInsert(String[] args, String url) {
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
            asegurarTabla(con);
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

    private static void doSelectAll(String url) {
        StringBuilder sb = new StringBuilder("[");
        try (Connection con = DriverManager.getConnection(url, "", "")) {
            asegurarTabla(con);
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

    private static void doInitMaxId(String url) {
        try (Connection con = DriverManager.getConnection(url, "", "")) {
            asegurarTabla(con);
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

    private static void doCreateTable(String url) {
        try (Connection con = DriverManager.getConnection(url, "", "")) {
            asegurarTabla(con);
            System.out.println("OK:tabla logs lista");
        } catch (SQLException e) {
            System.out.println("ERROR:" + e.getMessage().replace("\n", " "));
        }
    }

    /**
     * Garantiza que la tabla LOGS exista.
     *
     * FIX CRÍTICO: No usamos meta.getTables() porque con el driver HXTT
     * (versión evaluación) ese método frecuentemente devuelve ResultSet vacío
     * aunque la tabla exista → el código antiguo intentaba crearla de nuevo
     * → excepción SQLException → la conexión quedaba en estado de error →
     * todos los INSERT/SELECT posteriores en esa conexión fallaban también.
     *
     * Solución: intentar CREATE TABLE directamente y atrapar la excepción
     * "tabla ya existe". Si el mensaje contiene "already/exist/duplicate"
     * sabemos que la tabla ya estaba creada y continuamos sin problema.
     * Cualquier otro error se relanza normalmente.
     */
    private static void asegurarTabla(Connection con) throws SQLException {
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
            // Si llegamos aquí: tabla creada por primera vez
            System.err.println("[ParadoxWorker] Tabla LOGS creada por primera vez.");
        } catch (SQLException e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            // "already exists", "table exists", "duplicate" → tabla ya existía → OK
            if (msg.contains("already") || msg.contains("exist") || msg.contains("duplicate")) {
                return;  // Normal: la tabla ya existía
            }
            // Error real → relanzar para que el llamador lo capture
            throw e;
        }
    }

    private static String jsonStr(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                       .replace("\r", "\\r")
                       .replace("\t", "\\t") + "\"";
    }

    private static String decode(String arg) {
        if ("__EMPTY__".equals(arg)) return "";
        return arg.replace("__SPACE__", " ");
    }
}
