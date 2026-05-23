package apigenerica.controller;

import apigenerica.backup.BackupConfig;
import apigenerica.config.AppConfig;
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

public class AdminBdController {

    private static final Set<String> BDS_SISTEMA = new HashSet<>(Arrays.asList(
            "information_schema", "performance_schema", "mysql", "sys"
    ));

    public void listarBDs(Context ctx) {
        List<String> bds = new ArrayList<>();
        try (Connection conn = ConexionMysql.getConexion();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW DATABASES")) {
            while (rs.next()) {
                String nombre = rs.getString(1);
                if (!BDS_SISTEMA.contains(nombre.toLowerCase())) bds.add(nombre);
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al listar bases de datos.", e);
        }
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok(bds));
    }

    @SuppressWarnings("unchecked")
    public void crearBD(Context ctx) {
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String nombre = body.get("nombre");
        if (nombre == null || nombre.trim().isEmpty())
            throw new ValidacionException("El campo 'nombre' es obligatorio.");
        nombre = nombre.trim().replaceAll("\\s+", "_");
        if (BDS_SISTEMA.contains(nombre.toLowerCase()))
            throw new ValidacionException("No se puede crear una BD con ese nombre reservado.");
        try (Connection conn = ConexionMysql.getConexion();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + nombre + "` CHARACTER SET utf8mb4");
        } catch (SQLException e) {
            throw new BaseDatosException("Error al crear la base de datos '" + nombre + "'.", e);
        }
        ctx.status(HttpCode.CREATED).json(ApiRespuesta.ok("Base de datos '" + nombre + "' creada correctamente."));
    }

    public void borrarBD(Context ctx) {
        String nombre = ctx.pathParam("nombre");
        if (BDS_SISTEMA.contains(nombre.toLowerCase()))
            throw new ValidacionException("No se puede borrar una BD del sistema.");
        try (Connection conn = ConexionMysql.getConexion();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP DATABASE `" + nombre + "`");
        } catch (SQLException e) {
            throw new BaseDatosException("Error al borrar la base de datos '" + nombre + "'.", e);
        }
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok("Base de datos '" + nombre + "' eliminada."));
    }

    public void listarTablas(Context ctx) {
        String bd = ctx.pathParam("nombre");
        List<String> tablas = new ArrayList<>();
        try (Connection conn = ConexionMysql.getConexion(bd);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
            while (rs.next()) tablas.add(rs.getString(1));
        } catch (SQLException e) {
            throw new BaseDatosException("Error al listar tablas de '" + bd + "'.", e);
        }
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok(tablas));
    }

    public void describirTabla(Context ctx) {
        String bd = ctx.pathParam("nombre");
        String tabla = ctx.pathParam("tabla");
        List<Map<String, String>> columnas = new ArrayList<>();
        try (Connection conn = ConexionMysql.getConexion(bd);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("DESCRIBE `" + tabla + "`")) {
            while (rs.next()) {
                Map<String, String> col = new LinkedHashMap<>();
                col.put("Field", rs.getString("Field"));
                col.put("Type",  rs.getString("Type"));
                col.put("Null",  rs.getString("Null"));
                col.put("Key",   rs.getString("Key"));
                col.put("Extra", rs.getString("Extra"));
                columnas.add(col);
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al describir tabla '" + tabla + "' en BD '" + bd + "'.", e);
        }
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok(columnas));
    }

    public void leerDatos(Context ctx) {
        String bd = ctx.pathParam("nombre");
        String tabla = ctx.pathParam("tabla");
        Map<String, Object> resultado = new LinkedHashMap<>();
        List<String> cabeceras = new ArrayList<>();
        List<List<String>> filas = new ArrayList<>();
        try (Connection conn = ConexionMysql.getConexion(bd);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM `" + tabla + "`")) {
            ResultSetMetaData meta = rs.getMetaData();
            int numCols = meta.getColumnCount();
            for (int i = 1; i <= numCols; i++) cabeceras.add(meta.getColumnName(i));
            while (rs.next()) {
                List<String> fila = new ArrayList<>();
                for (int i = 1; i <= numCols; i++) {
                    String val = rs.getString(i);
                    fila.add(val != null ? val : "NULL");
                }
                filas.add(fila);
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al leer datos de '" + tabla + "'.", e);
        }
        resultado.put("cabeceras", cabeceras);
        resultado.put("filas", filas);
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok(resultado));
    }

    @SuppressWarnings("unchecked")
    public void insertarRegistro(Context ctx) {
        String bd = ctx.pathParam("nombre");
        String tabla = ctx.pathParam("tabla");
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        List<String> columnas = (List<String>) body.get("columnas");
        List<String> valores  = (List<String>) body.get("valores");
        if (columnas == null || valores == null || columnas.isEmpty() || columnas.size() != valores.size())
            throw new ValidacionException("Los campos 'columnas' y 'valores' son obligatorios y deben tener la misma longitud.");
        StringBuilder sql = new StringBuilder("INSERT INTO `" + tabla + "` (");
        StringBuilder placeholders = new StringBuilder("VALUES (");
        for (int i = 0; i < columnas.size(); i++) {
            sql.append("`").append(columnas.get(i)).append("`");
            placeholders.append("?");
            if (i < columnas.size() - 1) { sql.append(","); placeholders.append(","); }
        }
        sql.append(")").append(placeholders).append(")");
        try (Connection conn = ConexionMysql.getConexion(bd);
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < valores.size(); i++) ps.setObject(i + 1, valores.get(i));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new BaseDatosException("Error al insertar registro en '" + tabla + "'.", e);
        }
        ctx.status(HttpCode.CREATED).json(ApiRespuesta.ok("Registro insertado correctamente."));
    }

    @SuppressWarnings("unchecked")
    public void crearTabla(Context ctx) {
        String bd = ctx.pathParam("nombre");
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String nombreTabla = (String) body.get("nombre");
        List<Map<String, Object>> columnas = (List<Map<String, Object>>) body.get("columnas");
        if (nombreTabla == null || nombreTabla.trim().isEmpty())
            throw new ValidacionException("El campo 'nombre' de la tabla es obligatorio.");
        if (columnas == null || columnas.isEmpty())
            throw new ValidacionException("Debe definir al menos una columna.");
        StringBuilder sql = new StringBuilder("CREATE TABLE `" + nombreTabla.trim() + "` (");
        List<String> pks = new ArrayList<>();
        List<String> fks = new ArrayList<>();
        for (int i = 0; i < columnas.size(); i++) {
            Map<String, Object> col = columnas.get(i);
            String colNombre = (String) col.get("nombre");
            String colTipo   = (String) col.get("tipo");
            Boolean notNull  = col.get("notNull")  != null ? (Boolean) col.get("notNull")  : false;
            Boolean isPK     = col.get("isPK")     != null ? (Boolean) col.get("isPK")     : false;
            String tablaFK   = col.get("tablaForanea")  != null ? (String) col.get("tablaForanea")  : "";
            String colFK     = col.get("columnaForanea") != null ? (String) col.get("columnaForanea") : "";
            sql.append("`").append(colNombre).append("`").append(colTipo);
            if (notNull) sql.append(" NOT NULL");
            if (isPK) pks.add("`" + colNombre + "`");
            if (tablaFK != null && !tablaFK.isEmpty())
                fks.add("FOREIGN KEY (`" + colNombre + "`) REFERENCES `" + tablaFK + "`(`" + colFK + "`) ON DELETE CASCADE ON UPDATE CASCADE");
            if (i < columnas.size() - 1) sql.append(",");
        }
        if (!pks.isEmpty()) sql.append(", PRIMARY KEY (").append(String.join(", ", pks)).append(")");
        for (String fk : fks) sql.append(", ").append(fk);
        sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
        try (Connection conn = ConexionMysql.getConexion(bd);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql.toString());
        } catch (SQLException e) {
            throw new BaseDatosException("Error al crear tabla '" + nombreTabla + "' en BD '" + bd + "'.", e);
        }
        ctx.status(HttpCode.CREATED).json(ApiRespuesta.ok("Tabla '" + nombreTabla + "' creada correctamente en '" + bd + "'."));
    }

    public void borrarTabla(Context ctx) {
        String bd    = ctx.pathParam("nombre");
        String tabla = ctx.pathParam("tabla");
        try (Connection conn = ConexionMysql.getConexion(bd);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP TABLE `" + tabla + "`");
        } catch (SQLException e) {
            throw new BaseDatosException("Error al borrar tabla '" + tabla + "' en BD '" + bd + "'.", e);
        }
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok("Tabla '" + tabla + "' eliminada de '" + bd + "'."));
    }

    public void crearBackup(Context ctx) {
        String bd = ctx.pathParam("nombre");
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        String fileName = "backup_" + bd + "_" + timestamp + ".sql";
        File backupDir = BackupConfig.getDirectorioBackups();
        if (!backupDir.exists()) backupDir.mkdirs();
        File backupFile = new File(backupDir, fileName);
        try (Connection conn = ConexionMysql.getConexion(bd);
             Statement stmt = conn.createStatement();
             PrintWriter pw = new PrintWriter(new FileWriter(backupFile))) {
            pw.println("-- Backup de '" + bd + "' generado el " + timestamp);
            pw.println("-- Generado por la API ERP");
            pw.println();
            pw.println("CREATE DATABASE IF NOT EXISTS `" + bd + "` CHARACTER SET utf8mb4;");
            pw.println("USE `" + bd + "`;");
            pw.println();
            ResultSet rsTables = stmt.executeQuery("SHOW TABLES");
            List<String> tablas = new ArrayList<>();
            while (rsTables.next()) tablas.add(rsTables.getString(1));
            rsTables.close();
            for (String tabla : tablas) {
                try (ResultSet rsCreate = stmt.executeQuery("SHOW CREATE TABLE `" + tabla + "`")) {
                    if (rsCreate.next()) {
                        pw.println("-- Tabla: " + tabla);
                        pw.println("DROP TABLE IF EXISTS `" + tabla + "`;");
                        pw.println(rsCreate.getString(2) + ";");
                        pw.println();
                    }
                }
                try (ResultSet rsData = stmt.executeQuery("SELECT * FROM `" + tabla + "`")) {
                    ResultSetMetaData meta = rsData.getMetaData();
                    int cols = meta.getColumnCount();
                    while (rsData.next()) {
                        StringBuilder sb = new StringBuilder("INSERT INTO `" + tabla + "` VALUES (");
                        for (int i = 1; i <= cols; i++) {
                            String val = rsData.getString(i);
                            sb.append(val == null ? "NULL" : "'" + val.replace("'", "\\'") + "'");
                            if (i < cols) sb.append(",");
                        }
                        sb.append(");");
                        pw.println(sb.toString());
                    }
                    pw.println();
                }
            }
        } catch (Exception e) {
            throw new BaseDatosException("Error al crear backup de '" + bd + "'.", e);
        }
        Map<String, String> respuesta = new LinkedHashMap<>();
        respuesta.put("archivo", backupFile.getAbsolutePath());
        respuesta.put("nombre", fileName);
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok(respuesta));
    }

    public void probarConexion(Context ctx) {
        try (Connection conn = ConexionMysql.getConexion()) {
            ctx.status(HttpCode.OK).json(ApiRespuesta.ok("Conexion OK"));
        } catch (SQLException e) {
            ctx.status(HttpCode.INTERNAL_SERVER_ERROR).json(ApiRespuesta.error("Sin conexion: " + e.getMessage()));
        }
    }

    public void verificarBackup(Context ctx) {
        boolean mysqldumpDisponible = false;
        try {
            Process p = new ProcessBuilder("mysqldump", "--version").redirectErrorStream(true).start();
            p.waitFor();
            mysqldumpDisponible = (p.exitValue() == 0);
        } catch (Exception e) {
            mysqldumpDisponible = false;
        }
        File backupDir = BackupConfig.getDirectorioBackups();
        if (!backupDir.exists()) backupDir.mkdirs();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("mysqldump_disponible", mysqldumpDisponible);
        info.put("directorio_backups",   backupDir.getAbsolutePath());
        info.put("mysql_host",           AppConfig.DB_HOST + ":" + AppConfig.DB_PORT);
        info.put("directorio_existe",    backupDir.exists());
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok(info));
    }

    public void crearBackupGeneral(Context ctx) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        String fileName = "backup_general_" + timestamp + ".sql";
        File backupDir = BackupConfig.getDirectorioBackups();
        if (!backupDir.exists()) backupDir.mkdirs();
        File backupFile = new File(backupDir, fileName);
        String[] bases = { AppConfig.DB_SISTEMA, AppConfig.DB_CLIENTE };
        try (PrintWriter pw = new PrintWriter(new FileWriter(backupFile))) {
            pw.println("-- Backup general generado el " + timestamp);
            pw.println("-- Bases: " + AppConfig.DB_SISTEMA + ", " + AppConfig.DB_CLIENTE);
            pw.println();
            for (String bd : bases) {
                try (Connection conn = ConexionMysql.getConexion(bd);
                     Statement stmt = conn.createStatement()) {
                    pw.println("-- ========== BD: " + bd + " ==========");
                    pw.println("CREATE DATABASE IF NOT EXISTS `" + bd + "` CHARACTER SET utf8mb4;");
                    pw.println("USE `" + bd + "`;");
                    pw.println();
                    ResultSet rsTables = stmt.executeQuery("SHOW TABLES");
                    List<String> tablas = new ArrayList<>();
                    while (rsTables.next()) tablas.add(rsTables.getString(1));
                    rsTables.close();
                    for (String tabla : tablas) {
                        try (ResultSet rsCreate = stmt.executeQuery("SHOW CREATE TABLE `" + tabla + "`")) {
                            if (rsCreate.next()) {
                                pw.println("DROP TABLE IF EXISTS `" + tabla + "`;");
                                pw.println(rsCreate.getString(2) + ";");
                                pw.println();
                            }
                        }
                        try (ResultSet rsData = stmt.executeQuery("SELECT * FROM `" + tabla + "`")) {
                            ResultSetMetaData meta = rsData.getMetaData();
                            int cols = meta.getColumnCount();
                            while (rsData.next()) {
                                StringBuilder sb = new StringBuilder("INSERT INTO `" + tabla + "` VALUES (");
                                for (int i = 1; i <= cols; i++) {
                                    String val = rsData.getString(i);
                                    sb.append(val == null ? "NULL" : "'" + val.replace("'", "\\'") + "'");
                                    if (i < cols) sb.append(", ");
                                }
                                sb.append(");");
                                pw.println(sb.toString());
                            }
                            pw.println();
                        }
                    }
                } catch (SQLException e) {
                    pw.println("-- ERROR al hacer backup de " + bd + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new BaseDatosException("Error al crear backup general.", e);
        }
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("nombre",  fileName);
        respuesta.put("archivo", backupFile.getAbsolutePath());
        respuesta.put("tamano",  backupFile.length());
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok(respuesta));
    }

    public void listarBackups(Context ctx) {
        File backupDir = BackupConfig.getDirectorioBackups();
        List<Map<String, Object>> backups = new ArrayList<>();
        if (backupDir.exists() && backupDir.isDirectory()) {
            File[] ficheros = backupDir.listFiles((dir, name) -> name.endsWith(".zip") || name.endsWith(".sql"));
            if (ficheros != null) {
                Arrays.sort(ficheros, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                for (File f : ficheros) {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("nombre", f.getName());
                    info.put("tamano", f.length());
                    info.put("fecha",  f.lastModified());
                    info.put("ruta",   f.getAbsolutePath());
                    backups.add(info);
                }
            }
        }
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("backups", backups);
        resultado.put("total",   backups.size());
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok(resultado));
    }

    public void restaurarBackup(Context ctx) {
        String nombreBackup = ctx.queryParam("backup");
        String confirmacion = ctx.queryParam("confirmacion");
        if (nombreBackup == null || nombreBackup.trim().isEmpty())
            throw new ValidacionException("Parametro 'backup' requerido.");
        if (!"CONFIRMAR".equals(confirmacion))
            throw new ValidacionException("Debes pasar confirmacion=CONFIRMAR para ejecutar la restauracion.");
        File backupFile = new File(BackupConfig.getDirectorioBackups(), nombreBackup);
        if (!backupFile.exists())
            throw new ValidacionException("El fichero de backup no existe: " + nombreBackup);
        try (Connection conn = ConexionMysql.getConexion();
             Statement stmt = conn.createStatement();
             java.util.Scanner sc = new java.util.Scanner(backupFile, "UTF-8")) {
            conn.setAutoCommit(false);
            StringBuilder sentencia = new StringBuilder();
            while (sc.hasNextLine()) {
                String linea = sc.nextLine().trim();
                if (linea.startsWith("--") || linea.isEmpty()) continue;
                sentencia.append(linea).append(" ");
                if (linea.endsWith(";")) {
                    String sql = sentencia.toString().trim();
                    if (!sql.isEmpty()) stmt.execute(sql);
                    sentencia = new StringBuilder();
                }
            }
            conn.commit();
            conn.setAutoCommit(true);
        } catch (Exception e) {
            throw new BaseDatosException("Error al restaurar el backup '" + nombreBackup + "'.", e);
        }
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("backup_restaurado", nombreBackup);
        resultado.put("mensaje", "Restauracion completada correctamente.");
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok(resultado));
    }
}