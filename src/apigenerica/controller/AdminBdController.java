package apigenerica.controller;

import apigenerica.config.ConexionMysql;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.excepciones.ValidacionException;
import apigenerica.model.ApiRespuesta;
import io.javalin.http.Context;
import io.javalin.http.HttpCode;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Controlador para operaciones administrativas sobre bases de datos. Permite
 * crear, listar y eliminar BDs, listar tablas, describir estructura, leer datos
 * completos e insertar registros en cualquier tabla de cualquier BD. Estos
 * endpoints permiten que el frontend delegue TODAS las operaciones SQL a la
 * API, en lugar de ejecutar JDBC directamente.
 *
 * @author Grupo1
 */
public class AdminBdController {
// Lista de BDs del sistema que no se deben mostrar ni permitir borrar

    private static final Set<String> BDS_SISTEMA = new HashSet<>(Arrays.asList(
            "information_schema", "performance_schema", "mysql", "sys"
    ));
// ════════════════════════════════════════════════════════
// GET /api/admin/bd — Listar todas las bases de datos
// ════════════════════════════════════════════════════════

    public void listarBDs(Context ctx) {
        List<String> bds = new ArrayList<>();
        try (Connection conn = ConexionMysql.getConexion(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SHOW DATABASES")) {
            while (rs.next()) {
                String nombre = rs.getString(1);
                if (!BDS_SISTEMA.contains(nombre.toLowerCase())) {
                    bds.add(nombre);
                }
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al listar bases de datos.", e);
        }
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok(bds));
    }
// ════════════════════════════════════════════════════════
// POST /api/admin/bd — Crear una nueva base de datos
// Body: {  "nombre ":  "mi_nueva_bd " }
// ════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    public void crearBD(Context ctx) {
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String nombre = body.get("nombre");
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new ValidacionException("El campo 'nombre' es obligatorio. ");
        }
        nombre = nombre.trim().replaceAll("\\s+", "_");
// Validar que no sea una BD del sistema
        if (BDS_SISTEMA.contains(nombre.toLowerCase())) {
            throw new ValidacionException("No se puede crear una BD con ese nombre reservado. ");
        }
        try (Connection conn = ConexionMysql.getConexion(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS  `" + nombre + "` CHARACTER SET utf8mb4 ");
        } catch (SQLException e) {
            throw new BaseDatosException("Error al crear la base de datos ' " + nombre + "'. ", e);
        }
        ctx.status(HttpCode.CREATED).json(ApiRespuesta.ok("Base de datos ' " + nombre + "' creada correctamente. "));
    }
// ════════════════════════════════════════════════════════
// DELETE /api/admin/bd/{nombre} — Borrar una base de datos
// ════════════════════════════════════════════════════════

    public void borrarBD(Context ctx) {
        String nombre = ctx.pathParam("nombre ");
        if (BDS_SISTEMA.contains(nombre.toLowerCase())) {
            throw new ValidacionException("No se puede borrar una BD del sistema. ");
        }
        try (Connection conn = ConexionMysql.getConexion(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP DATABASE  `" + nombre + "` ");
        } catch (SQLException e) {
            throw new BaseDatosException("Error al borrar la base de datos ' " + nombre + "'. ", e);
        }
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok("Base de datos ' " + nombre + "' eliminada. "));
    }
// ════════════════════════════════════════════════════════
// GET /api/admin/bd/{nombre}/tablas — Listar tablas de una BD
// ════════════════════════════════════════════════════════

    public void listarTablas(Context ctx) {
        String bd = ctx.pathParam("nombre");
        List<String> tablas = new ArrayList<>();
        try (Connection conn = ConexionMysql.getConexion(bd); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
            while (rs.next()) {
                tablas.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al listar tablas de '" + bd + "'.", e);
        }
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok(tablas));
    }
// ════════════════════════════════════════════════════════
// GET /api/admin/bd/{nombre}/tablas/{tabla}/estructura
// Devuelve DESCRIBE tabla (campo, tipo, null, clave, extra)
//  ════════════════════════════════════════════════════════

    public void describirTabla(Context ctx) {
        String bd = ctx.pathParam("nombre ");
        String tabla = ctx.pathParam("tabla ");
        List<Map<String, String>> columnas = new ArrayList<>();
        try (Connection conn = ConexionMysql.getConexion(bd); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("DESCRIBE  `" + tabla + "` ")) {
            while (rs.next()) {
                Map<String, String> col = new LinkedHashMap<>();
                col.put("Field ", rs.getString("Field "));
                col.put("Type ", rs.getString("Type "));
                col.put("Null ", rs.getString("Null "));
                col.put("Key ", rs.getString("Key "));
                col.put("Extra ", rs.getString("Extra "));
                columnas.add(col);
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al describir tabla ' " + tabla + "' en BD ' " + bd + "'. ", e);
        }
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok(columnas));
    }
// ════════════════════════════════════════════════════════
// GET /api/admin/bd/{nombre}/tablas/{tabla}/datos
// Devuelve SELECT * FROM tabla (cabeceras + filas)
// ════════════════════════════════════════════════════════

    public void leerDatos(Context ctx) {
        String bd = ctx.pathParam("nombre ");
        String tabla = ctx.pathParam("tabla ");
        Map<String, Object> resultado = new LinkedHashMap<>();
        List<String> cabeceras = new ArrayList<>();
        List<List<String>> filas = new ArrayList<>();
        try (Connection conn = ConexionMysql.getConexion(bd); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM ` " + tabla + "` ")) {

            ResultSetMetaData meta = rs.getMetaData();
            int numCols = meta.getColumnCount();
            for (int i = 1; i <= numCols; i++) {
                cabeceras.add(meta.getColumnName(i));
            }
            while (rs.next()) {
                List<String> fila = new ArrayList<>();
                for (int i = 1; i <= numCols; i++) {
                    String val = rs.getString(i);
                    fila.add(val != null ? val : "NULL ");
                }
                filas.add(fila);
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al leer datos de ' " + tabla + "'. ", e);
        }

        resultado.put("cabeceras ", cabeceras);
        resultado.put("filas ", filas);
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok(resultado));
    }
// ════════════════════════════════════════════════════════
// POST /api/admin/bd/{nombre}/tablas/{tabla}/datos
// Inserta un registro. Body: { "columnas": [...], "valores": [...] }
// ════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    public void insertarRegistro(Context ctx) {
        String bd = ctx.pathParam("nombre");
        String tabla = ctx.pathParam("tabla");
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        List<String> columnas = (List<String>) body.get("columnas ");
        List<String> valores = (List<String>) body.get("valores ");

        if (columnas == null || valores == null || columnas.isEmpty() || columnas.size() != valores.size()) {
            throw new ValidacionException("Los campos 'columnas' y 'valores' son obligatorios y deben tener la misma longitud. ");
        }

        StringBuilder sql = new StringBuilder("INSERT INTO ` " + tabla + "` ( ");
        StringBuilder placeholders = new StringBuilder("VALUES ( ");
        for (int i = 0; i < columnas.size(); i++) {
            sql.append("` ").append(columnas.get(i)).append("` ");
            placeholders.append("? ");
            if (i < columnas.size() - 1) {
                sql.append(",  ");
                placeholders.append(",  ");
            }
        }
        sql.append(")  ").append(placeholders).append(") ");

        try (Connection conn = ConexionMysql.getConexion(bd); PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < valores.size(); i++) {
                pstmt.setObject(i + 1, valores.get(i));
            }
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new BaseDatosException("Error al insertar registro en ' " + tabla + "'. ", e);
        }
        ctx.status(HttpCode.CREATED).json(ApiRespuesta.ok("Registro insertado correctamente. "));
    }
// ════════════════════════════════════════════════════════
// POST /api/admin/bd/{nombre}/tablas — Crear tabla con columnas
// Body: {  "nombre ":  "tabla ",  "columnas ": [ {  "nombre ": "col ",  "tipo ": "VARCHAR(100) ", ... } ] }
// ════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked ")
    public void crearTabla(Context ctx) {
        String bd = ctx.pathParam("nombre ");
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String nombreTabla = (String) body.get("nombre ");
        List<Map<String, Object>> columnas = (List<Map<String, Object>>) body.get("columnas ");
        if (nombreTabla == null || nombreTabla.trim().isEmpty()) {
            throw new ValidacionException("El campo 'nombre' de la tabla es obligatorio. ");
        }
        if (columnas == null || columnas.isEmpty()) {
            throw new ValidacionException("Debe definir al menos una columna. ");
        }

        StringBuilder sql = new StringBuilder("CREATE TABLE ` " + nombreTabla.trim() + "` ( ");
        List<String> pks = new ArrayList<>();
        List<String> fks = new ArrayList<>();

        for (int i = 0; i < columnas.size(); i++) {
            Map<String, Object> col = columnas.get(i);
            String colNombre = (String) col.get("nombre ");
            String colTipo = (String) col.get("tipo ");
            Boolean notNull = col.get("notNull ") != null ? (Boolean) col.get("notNull ") : false;
            Boolean isPK = col.get("isPK ") != null ? (Boolean) col.get("isPK ") : false;
            String tablaFK = col.get("tablaForanea ") != null ? (String) col.get("tablaForanea ") : " ";
            String colFK = col.get("columnaForanea ") != null ? (String) col.get("columnaForanea ") : " ";

            sql.append("` ").append(colNombre).append("`  ").append(colTipo);
            if (notNull) {
                sql.append(" NOT NULL ");
            }
            if (isPK) {
                pks.add("` " + colNombre + "` ");
            }
            if (tablaFK != null && !tablaFK.isEmpty()) {
                fks.add("FOREIGN KEY (` " + colNombre + "`) REFERENCES ` "
                        + tablaFK + "`(` " + colFK + "`) ON DELETE CASCADE ON UPDATE CASCADE ");
            }
            if (i < columnas.size() - 1) {
                sql.append(",  ");
            }
        }

        if (!pks.isEmpty()) {
            sql.append(", PRIMARY KEY ( ").append(String.join(",  ", pks)).append(") ");
        }
        for (String fk : fks) {
            sql.append(",  ").append(fk);
        }
        sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4; ");

        try (Connection conn = ConexionMysql.getConexion(bd); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql.toString());
        } catch (SQLException e) {
            throw new BaseDatosException("Error al crear tabla ' " + nombreTabla + "' en BD ' " + bd + "'. ", e);
        }
        ctx.status(HttpCode.CREATED).json(ApiRespuesta.ok("Tabla ' " + nombreTabla + "' creada correctamente en ' " + bd + "'. "));
    }
// ════════════════════════════════════════════════════════
// DELETE /api/admin/bd/{nombre}/tablas/{tabla} — Borrar tabla
// ════════════════════════════════════════════════════════

    public void borrarTabla(Context ctx) {
        String bd = ctx.pathParam("nombre ");
        String tabla = ctx.pathParam("tabla ");
        try (Connection conn = ConexionMysql.getConexion(bd); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP TABLE  `" + tabla + "` ");
        } catch (SQLException e) {
            throw new BaseDatosException("Error al borrar tabla ' " + tabla + "' en BD ' " + bd + "'. ", e);
        }
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok("Tabla ' " + tabla + "' eliminada de ' " + bd + "'. "));
    }
// ════════════════════════════════════════════════════════
// POST /api/admin/bd/{nombre}/backup — Crear backup SQL
// ════════════════════════════════════════════════════════

    public void crearBackup(Context ctx) {
        String bd = ctx.pathParam("nombre");
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        String fileName = "backup_" + bd + "_" + timestamp + ".sql";
        File backupDir = new File("backups ");
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }
        File backupFile = new File(backupDir, fileName);

        try (Connection conn = ConexionMysql.getConexion(bd); Statement stmt = conn.createStatement(); PrintWriter pw = new PrintWriter(new FileWriter(backupFile))) {

            pw.println("-- Backup de ' " + bd + "' generado el  " + timestamp);
            pw.println("-- Generado por la API ERP ");
            pw.println();
            pw.println("CREATE DATABASE IF NOT EXISTS ` " + bd + "` CHARACTER SET utf8mb4; ");
            pw.println("USE ` " + bd + "`; ");
            pw.println();

            // Listar tablas
            ResultSet rsTables = stmt.executeQuery("SHOW TABLES ");
            List<String> tablas = new ArrayList<>();
            while (rsTables.next()) {
                tablas.add(rsTables.getString(1));
            }
            rsTables.close();

            for (String tabla : tablas) {
                // CREATE TABLE
                try (ResultSet rsCreate = stmt.executeQuery("SHOW CREATE TABLE ` " + tabla + "` ")) {
                    if (rsCreate.next()) {
                        pw.println("-- Tabla:  " + tabla);
                        pw.println("DROP TABLE IF EXISTS ` " + tabla + "`; ");
                        pw.println(rsCreate.getString(2) + "; ");
                        pw.println();
                    }
                }
                // INSERT
                try (ResultSet rsData = stmt.executeQuery("SELECT * FROM ` " + tabla + "` ")) {
                    ResultSetMetaData meta = rsData.getMetaData();
                    int cols = meta.getColumnCount();
                    while (rsData.next()) {
                        StringBuilder sb = new StringBuilder("INSERT INTO ` " + tabla + "` VALUES ( ");
                        for (int i = 1; i <= cols; i++) {
                            String val = rsData.getString(i);
                            if (val == null) {
                                sb.append("NULL ");
                            } else {
                                sb.append("' ").append(val.replace("'", "\\' ")).append("' ");
                            }
                            if (i < cols) {
                                sb.append(",  ");
                            }
                        }
                        sb.append("); ");
                        pw.println(sb.toString());
                    }
                    pw.println();
                }
            }

        } catch (Exception e) {
            throw new BaseDatosException("Error al crear backup de ' " + bd + "'. ", e);
        }

        Map<String, String> respuesta = new LinkedHashMap<>();
        respuesta.put("archivo ", backupFile.getAbsolutePath());
        respuesta.put("nombre ", fileName);
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok(respuesta));
    }
// ════════════════════════════════════════════════════════
// GET /api/admin/bd/{nombre}/conexion — Probar conexión
// ════════════════════════════════════════════════════════

    public void probarConexion(Context ctx) {
        try (Connection conn = ConexionMysql.getConexion()) {
            ctx.status(HttpCode.OK).json(ApiRespuesta.ok("Conexion OK"));
        } catch (SQLException e) {
            ctx.status(HttpCode.INTERNAL_SERVER_ERROR).json(ApiRespuesta.error("Sin conexion: " + e.getMessage()));
        }
    }
}
