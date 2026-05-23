package apigenerica.config;

import apigenerica.config.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.sql.*;
import java.util.*;

/**
 * Procesa schema.json para crear módulos, tablas y columnas personalizadas.
 * Lee el JSON → Inserta en erp_meta_* → Crea tablas físicas en DB_CLIENTE.
 */
public class SchemaProcessor {

    private static final String SCHEMA_PATH = "src/schema.json";

    public static void procesarSchema(Connection connSistema, Connection connCliente) {
        File schemaFile = new File(SCHEMA_PATH);
        if (!schemaFile.exists()) {
            System.out.println("[API] No hay schema.json, omitiendo tablas personalizadas.");
            return;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            SchemaConfig schema = mapper.readValue(schemaFile, SchemaConfig.class);

            // 1. Insertar módulos
            for (ModuloDTO modulo : schema.modulos) {
                long idModulo = asegurarModulo(connSistema, modulo);

                // 2. Procesar tablas de este módulo
                for (TablaDTO tabla : schema.tablas) {
                    if (tabla.modulo != null && tabla.modulo.equals(modulo.nombre)) {
                        procesarTabla(connSistema, connCliente, idModulo, tabla);
                    }
                }
            }
            System.out.println("[API] Schema personalizado procesado correctamente.");

        } catch (Exception e) {
            System.err.println("[API] Error procesando schema.json: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static long asegurarModulo(Connection conn, ModuloDTO modulo) throws SQLException {
        // Verificar si ya existe
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM erp_modulos WHERE nombre = ?")) {
            ps.setString(1, modulo.nombre);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("id");
            }
        }

        // Insertar nuevo
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO erp_modulos (nombre, icono, icon_type, habilitado, orden) VALUES (?, ?, 'emote', 1, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, modulo.nombre);
            ps.setString(2, modulo.icono != null ? modulo.icono : "📦");
            ps.setInt(3, modulo.orden);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        }
        return 0;
    }

    private static void procesarTabla(Connection connSistema, Connection connCliente,
                                     long moduloId, TablaDTO tabla) throws SQLException {

        // Verificar si ya está registrada
        if (yaRegistrada(connSistema, tabla.nombre_logico)) {
            System.out.println("[API] Tabla ya registrada: " + tabla.nombre_logico);
            return;
        }

        // 1. Insertar en erp_meta_tablas
        long tablaId = 0;
        try (PreparedStatement ps = connSistema.prepareStatement(
                "INSERT INTO erp_meta_tablas (modulo_id, nombre_logico, nombre_amigable) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, moduloId);
            ps.setString(2, tabla.nombre_logico);
            ps.setString(3, tabla.nombre_amigable);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) tablaId = keys.getLong(1);
            }
        }
        if (tablaId == 0) return;

        // 2. Insertar columnas en erp_meta_columnas
        for (ColumnaDTO col : tabla.columnas) {
            insertarMetaColumna(connSistema, tablaId, col);
        }

        // 3. Crear tabla física en BD cliente
        crearTablaFisica(connCliente, tabla, tabla.columnas);
        System.out.println("[API] Tabla creada: " + tabla.nombre_logico);
    }

    private static void insertarMetaColumna(Connection conn, long tablaId, ColumnaDTO col) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO erp_meta_columnas " +
                "(tabla_id, nombre, tipo, nullable, es_contrasena, es_visible, es_sensible, es_archivo, autoincremental, unico, valor_defecto) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, tablaId);
            ps.setString(2, col.nombre);
            ps.setString(3, col.tipo);
            ps.setBoolean(4, col.nullable);
            ps.setBoolean(5, col.es_contrasena);
            ps.setBoolean(6, col.es_visible);
            ps.setBoolean(7, col.es_sensible);
            ps.setBoolean(8, col.es_archivo);
            ps.setBoolean(9, col.autoincremental);
            ps.setBoolean(10, col.unico);
            ps.setString(11, col.valor_defecto);
            ps.executeUpdate();
        }
    }

    private static void crearTablaFisica(Connection conn, TablaDTO tabla, List<ColumnaDTO> columnas) throws SQLException {
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS `")
                .append(tabla.nombre_logico).append("` (");

        List<String> pkColumns = new ArrayList<>();

        for (int i = 0; i < columnas.size(); i++) {
            ColumnaDTO col = columnas.get(i);
            sql.append("`").append(col.nombre).append("` ");

            // Mapear tipo JSON → MySQL
            sql.append(mapearTipoSQL(col.tipo));

            if (!col.nullable) sql.append(" NOT NULL");
            if (col.autoincremental) sql.append(" AUTO_INCREMENT");
            if (col.valor_defecto != null && !col.valor_defecto.isEmpty()) {
                if (col.tipo.equals("TEXTO_CORTO") || col.tipo.equals("TEXTO_LARGO") || col.tipo.equals("CONTRASENA")) {
                    sql.append(" DEFAULT '").append(col.valor_defecto.replace("'", "''")).append("'");
                } else {
                    sql.append(" DEFAULT ").append(col.valor_defecto);
                }
            }
            if (col.unico && !col.autoincremental) {
                sql.append(" UNIQUE");
            }
            if (col.autoincremental) {
                pkColumns.add(col.nombre);
            }

            if (i < columnas.size() - 1) sql.append(", ");
        }

        // Primary key
        if (!pkColumns.isEmpty()) {
            sql.append(", PRIMARY KEY (`").append(String.join("`,`", pkColumns)).append("`)");
        }

        sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql.toString());
        }
    }

    private static String mapearTipoSQL(String tipoJson) {
        if (tipoJson == null) return "VARCHAR(255)";
        switch (tipoJson) {
            case "ENTERO": return "INT";
            case "DECIMAL": return "DECIMAL(10,2)";
            case "FECHA": return "DATE";
            case "FECHA_HORA": return "DATETIME";
            case "TEXTO_LARGO": return "TEXT";
            case "ARCHIVO": return "VARCHAR(255)";
            case "CONTRASENA": return "VARCHAR(60)";
            case "BINARIO": return "TINYINT(1)";
            case "TEXTO_CORTO":
            default: return "VARCHAR(255)";
        }
    }

    private static boolean yaRegistrada(Connection conn, String nombreLogico) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM erp_meta_tablas WHERE nombre_logico = ?")) {
            ps.setString(1, nombreLogico);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
}