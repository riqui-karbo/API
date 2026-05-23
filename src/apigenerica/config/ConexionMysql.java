package apigenerica.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Pool de conexiones MySQL usando HikariCP.
 * Host, puerto, usuario, contrasena y nombres de BD se leen desde db.properties via AppConfig.
 * @author Grupo1
 */
public class ConexionMysql {

    private static HikariDataSource ds;

    private static final String URL     = "jdbc:mysql://" + AppConfig.DB_HOST + ":" + AppConfig.DB_PORT + "/mysql";
    private static final String USUARIO = AppConfig.DB_USUARIO;
    private static final String PWD     = AppConfig.DB_PASSWORD;

    private static final List<String> TABLAS_NUCLEO = java.util.Arrays.asList("empleados", "productos", "clientes", "incidencias");

    public static void inicializar() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(URL + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
        config.setUsername(USUARIO);
        config.setPassword(PWD);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        ds = new HikariDataSource(config);

        crearEstructuraERP();
    }

    public static Connection getConexion() throws SQLException {
        return ds.getConnection();
    }

    public static Connection getConexion(String baseDatos) throws SQLException {
        Connection conn = ds.getConnection();
        if (baseDatos != null && !baseDatos.trim().isEmpty()) {
            conn.setCatalog(baseDatos);
        }
        return conn;
    }

    public static void cerrar() {
        if (ds != null) ds.close();
    }

    private static void crearEstructuraERP() {
        try (Connection conn = getConexion(); Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + AppConfig.DB_SISTEMA + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            stmt.execute("USE " + AppConfig.DB_SISTEMA + "");

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS erp_config (" +
                "  clave VARCHAR(100) PRIMARY KEY," +
                "  valor TEXT," +
                "  actualizado TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS erp_roles (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  nombre VARCHAR(50) NOT NULL UNIQUE," +
                "  descripcion VARCHAR(255)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS erp_roles_permisos (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  rol VARCHAR(50) NOT NULL," +
                "  tabla VARCHAR(100) NOT NULL," +
                "  puede_ver TINYINT(1) DEFAULT 0," +
                "  puede_crear TINYINT(1) DEFAULT 0," +
                "  puede_editar TINYINT(1) DEFAULT 0," +
                "  puede_eliminar TINYINT(1) DEFAULT 0," +
                "  UNIQUE KEY uk_rol_tabla (rol, tabla)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS erp_users (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  email VARCHAR(255) NOT NULL UNIQUE," +
                "  contrasena CHAR(60) NOT NULL," +
                "  rol_id INT DEFAULT NULL," +
                "  tipo ENUM('empleado','cliente') NOT NULL DEFAULT 'empleado'," +
                "  activo TINYINT(1) NOT NULL DEFAULT 1," +
                "  FOREIGN KEY (rol_id) REFERENCES erp_roles(id) ON DELETE SET NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS erp_ficheros (" +
                "  id             INT AUTO_INCREMENT PRIMARY KEY," +
                "  uuid           VARCHAR(36)   NOT NULL UNIQUE," +
                "  nombre_original VARCHAR(500)," +
                "  mime_type      VARCHAR(100)," +
                "  tipo_detectado VARCHAR(50)," +
                "  tamano_bytes   BIGINT," +
                "  esta_en_disco  TINYINT(1)    DEFAULT 0," +
                "  ruta_disco     VARCHAR(1000)," +
                "  tabla_origen   VARCHAR(100)," +
                "  fecha_subida   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            try { stmt.executeUpdate("ALTER TABLE erp_users ADD COLUMN rol_id INT DEFAULT NULL"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE erp_users ADD COLUMN tipo ENUM('empleado','cliente') NOT NULL DEFAULT 'empleado'"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE erp_users ADD CONSTRAINT fk_users_rol FOREIGN KEY (rol_id) REFERENCES erp_roles(id) ON DELETE SET NULL"); } catch (SQLException ignored) {}

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS erp_modulos (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  nombre VARCHAR(100) NOT NULL," +
                "  icono VARCHAR(50) DEFAULT '📦'," +
                "  icon_type VARCHAR(20) DEFAULT 'emote'," +
                "  habilitado TINYINT(1) DEFAULT 1," +
                "  orden INT DEFAULT 0" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS erp_meta_tablas (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  modulo_id INT DEFAULT NULL," +
                "  nombre_logico VARCHAR(100) NOT NULL UNIQUE," +
                "  nombre_amigable VARCHAR(200)," +
                "  FOREIGN KEY (modulo_id) REFERENCES erp_modulos(id) ON DELETE SET NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            try { stmt.executeUpdate("ALTER TABLE erp_meta_tablas MODIFY modulo_id INT DEFAULT NULL"); } catch (SQLException ignored) {}

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS erp_meta_columnas (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  tabla_id INT NOT NULL," +
                "  nombre VARCHAR(100) NOT NULL," +
                "  tipo VARCHAR(50) NOT NULL," +
                "  nullable TINYINT(1) DEFAULT 1," +
                "  es_contrasena TINYINT(1) DEFAULT 0," +
                "  es_visible TINYINT(1) DEFAULT 1," +
                "  es_sensible TINYINT(1) DEFAULT 0," +
                "  es_archivo TINYINT(1) DEFAULT 0," +
                "  autoincremental TINYINT(1) DEFAULT 0," +
                "  unico TINYINT(1) DEFAULT 0," +
                "  valor_defecto VARCHAR(255)," +
                "  FOREIGN KEY (tabla_id) REFERENCES erp_meta_tablas(id) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS erp_meta_relaciones (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  nombre VARCHAR(100) NOT NULL," +
                "  tabla_origen INT NOT NULL," +
                "  fk_columna VARCHAR(100) NOT NULL," +
                "  tabla_destino VARCHAR(100) NOT NULL," +
                "  cardinalidad VARCHAR(10) NOT NULL," +
                "  FOREIGN KEY (tabla_origen) REFERENCES erp_meta_tablas(id) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + AppConfig.DB_CLIENTE + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            stmt.execute("USE " + AppConfig.DB_CLIENTE + "");

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS empleados (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  correo_electronico VARCHAR(255) NOT NULL UNIQUE," +
                "  nombre VARCHAR(100) NOT NULL," +
                "  primer_apellido VARCHAR(100) NOT NULL," +
                "  segundo_apellido VARCHAR(100) DEFAULT NULL," +
                "  dni_nie VARCHAR(20) NOT NULL UNIQUE," +
                "  telefono VARCHAR(20) DEFAULT NULL," +
                "  direccion VARCHAR(255) DEFAULT NULL," +
                "  iban VARCHAR(34) DEFAULT NULL," +
                "  nss VARCHAR(20) DEFAULT NULL," +
                "  cargo VARCHAR(100) DEFAULT 'Personal'," +
                "  foto_url VARCHAR(255) DEFAULT NULL," +
                "  user_id INT DEFAULT NULL," +
                "  CONSTRAINT fk_empleados_user" +
                "    FOREIGN KEY (user_id) REFERENCES " + AppConfig.DB_SISTEMA + ".erp_users(id)" +
                "    ON DELETE SET NULL ON UPDATE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            try {
                stmt.executeUpdate("ALTER TABLE empleados ADD COLUMN user_id INT DEFAULT NULL");
                stmt.executeUpdate(
                    "ALTER TABLE empleados ADD CONSTRAINT fk_empleados_user " +
                    "FOREIGN KEY (user_id) REFERENCES " + AppConfig.DB_SISTEMA + ".erp_users(id) " +
                    "ON DELETE SET NULL ON UPDATE CASCADE"
                );
            } catch (SQLException ignored) {}

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS productos (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  nombre VARCHAR(150) NOT NULL," +
                "  descripcion TEXT DEFAULT NULL," +
                "  referencia VARCHAR(150) DEFAULT NULL UNIQUE," +
                "  cantidad INT DEFAULT NULL," +
                "  precio DECIMAL(10, 2) NOT NULL," +
                "  imagen VARCHAR(255) DEFAULT NULL," +
                "  esta_agotado BOOLEAN NOT NULL DEFAULT FALSE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            try {
                stmt.executeUpdate("ALTER TABLE productos ADD COLUMN imagen VARCHAR(255) DEFAULT NULL");
            } catch (SQLException ignored) {}

            // Limpieza de versiones anteriores: productos solo debe tener un campo de imagen.
            eliminarColumnaSiExiste(AppConfig.DB_CLIENTE, "productos", "foto_url");

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS clientes (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  nombre VARCHAR(100) NOT NULL," +
                "  apellido VARCHAR(100) DEFAULT NULL," +
                "  cif_nif VARCHAR(20) NOT NULL UNIQUE," +
                "  telefono VARCHAR(20) DEFAULT NULL," +
                "  email VARCHAR(100) DEFAULT NULL," +
                "  direccion TEXT DEFAULT NULL," +
                "  creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  user_id INT DEFAULT NULL," +
                "  CONSTRAINT fk_clientes_user" +
                "    FOREIGN KEY (user_id) REFERENCES " + AppConfig.DB_SISTEMA + ".erp_users(id)" +
                "    ON DELETE SET NULL ON UPDATE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS incidencias (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  nombre VARCHAR(255) NOT NULL," +
                "  email VARCHAR(255) NOT NULL," +
                "  descripcion TEXT NOT NULL," +
                "  fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            try {
                stmt.executeUpdate("ALTER TABLE clientes ADD COLUMN user_id INT DEFAULT NULL");
                stmt.executeUpdate(
                    "ALTER TABLE clientes ADD CONSTRAINT fk_clientes_user " +
                    "FOREIGN KEY (user_id) REFERENCES " + AppConfig.DB_SISTEMA + ".erp_users(id) " +
                    "ON DELETE SET NULL ON UPDATE CASCADE"
                );
            } catch (SQLException ignored) {}

            stmt.execute("USE " + AppConfig.DB_SISTEMA + "");

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM erp_roles")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.executeUpdate(
                        "INSERT INTO erp_roles (nombre, descripcion) VALUES " +
                        "('admin',    'Administrador con acceso total')," +
                        "('empleado', 'Empleado con acceso basico')," +
                        "('cliente',  'Cliente con acceso a la tienda')"
                    );
                    System.out.println("[API] Roles por defecto creados (admin, empleado, cliente).");
                }
            }

            try (ResultSet rsU = stmt.executeQuery("SELECT COUNT(*) FROM erp_users")) {
                if (rsU.next() && rsU.getInt(1) == 0) {
                    String hashAdmin = org.mindrot.jbcrypt.BCrypt.hashpw(
                        "admin1234", org.mindrot.jbcrypt.BCrypt.gensalt(10));
                    try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO erp_users (email, contrasena, rol_id, tipo, activo) " +
                        "VALUES (?, ?, (SELECT id FROM erp_roles WHERE nombre = 'admin'), 'empleado', 1)")) {
                        ps.setString(1, "admin@empresa.com");
                        ps.setString(2, hashAdmin);
                        ps.executeUpdate();
                    }
                    System.out.println("[API] ================================================");
                    System.out.println("[API] Usuario admin creado:");
                    System.out.println("[API] Email: admin@empresa.com");
                    System.out.println("[API] Contrasena: admin1234");
                    System.out.println("[API] Cambia la contrasena tras el primer login.");
                    System.out.println("[API] ================================================");
                }
            }

            inicializarModulosYTablasDelNucleo(conn);

            System.out.println("[API] Estructura de metadatos sincronizada.");
            autoRegistrarTablas();

        } catch (SQLException e) {
            System.err.println("[API] Error creando estructura ERP: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private static void eliminarColumnaSiExiste(String baseDatos, String tabla, String columna) {
        try (Connection conn = getConexion(baseDatos)) {
            boolean existe = false;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?")) {
                ps.setString(1, baseDatos);
                ps.setString(2, tabla);
                ps.setString(3, columna);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) existe = rs.getInt(1) > 0;
                }
            }
            if (existe) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("ALTER TABLE " + tabla + " DROP COLUMN " + columna + "");
                    System.out.println("[API] Columna obsoleta eliminada: " + tabla + "." + columna);
                }
            }
        } catch (SQLException e) {
            System.err.println("[API] No se pudo eliminar la columna obsoleta " + tabla + "." + columna + ": " + e.getMessage());
        }
    }

    private static void eliminarMetaColumna(Connection connSistema, String tabla, String columna) throws SQLException {
        long tablaId = 0;
        try (PreparedStatement ps = connSistema.prepareStatement(
                "SELECT id FROM erp_meta_tablas WHERE nombre_logico = ?")) {
            ps.setString(1, tabla);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) tablaId = rs.getLong("id");
            }
        }
        if (tablaId == 0) return;

        try (PreparedStatement ps = connSistema.prepareStatement(
                "DELETE FROM erp_meta_columnas WHERE tabla_id = ? AND nombre = ?")) {
            ps.setLong(1, tablaId);
            ps.setString(2, columna);
            ps.executeUpdate();
        }
    }

    private static void inicializarModulosYTablasDelNucleo(Connection conn) throws SQLException {
        conn.setCatalog(AppConfig.DB_SISTEMA);
        long idModuloUnico = asegurarModulo(conn, "Gestion Central", "⚙️", 1);
        asegurarMetaTabla(conn, idModuloUnico, "empleados",   "Gestion de Empleados");
        asegurarMetaTabla(conn, idModuloUnico, "productos",   "Catalogo de Productos");
        asegurarMetaTabla(conn, idModuloUnico, "clientes",    "Cartera de Clientes");
        asegurarMetaTabla(conn, idModuloUnico, "incidencias", "Gestion de Incidencias");

        // Campos que almacenan UUIDs de ficheros en db4o: deben constar como ARCHIVO.
        asegurarMetaColumnaArchivo(conn, "empleados", "foto_url");
        asegurarMetaColumnaArchivo(conn, "productos", "imagen");
        eliminarMetaColumna(conn, "productos", "foto_url");
    }

    private static long asegurarModulo(Connection conn, String nombre, String icono, int orden) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM erp_modulos WHERE nombre = ?")) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("id");
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO erp_modulos (nombre, icono, icon_type, habilitado, orden) VALUES (?, ?, 'emote', 1, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.setString(2, icono);
            ps.setInt(3, orden);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        }
        return 0;
    }

    private static void asegurarMetaTabla(Connection conn, long moduloId, String nombreLogico, String nombreAmigable) throws SQLException {
        if (yaRegistrada(conn, nombreLogico)) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE erp_meta_tablas SET modulo_id = ?, nombre_amigable = ? WHERE nombre_logico = ?")) {
                ps.setLong(1, moduloId);
                ps.setString(2, nombreAmigable);
                ps.setString(3, nombreLogico);
                ps.executeUpdate();
            }
            return;
        }
        long tablaId = 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO erp_meta_tablas (modulo_id, nombre_logico, nombre_amigable) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, moduloId);
            ps.setString(2, nombreLogico);
            ps.setString(3, nombreAmigable);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) tablaId = keys.getLong(1);
            }
        }
        if (tablaId > 0) {
            try (Connection connCliente = getConexion(AppConfig.DB_CLIENTE);
                 Statement stmtDesc = connCliente.createStatement();
                 ResultSet rsDesc = stmtDesc.executeQuery("DESCRIBE " + nombreLogico + "")) {
                while (rsDesc.next()) {
                    String colNombre = rsDesc.getString("Field");
                    String colTipo   = rsDesc.getString("Type");
                    String colNull   = rsDesc.getString("Null");
                    String colKey    = rsDesc.getString("Key");
                    String colExtra  = rsDesc.getString("Extra");
                    boolean nullable = "YES".equalsIgnoreCase(colNull);
                    boolean autoInc  = colExtra != null && colExtra.contains("auto_increment");
                    boolean esPK     = "PRI".equalsIgnoreCase(colKey);
                    boolean esUnico  = "UNI".equalsIgnoreCase(colKey) || esPK;
                    try (PreparedStatement psCol = conn.prepareStatement(
                            "INSERT INTO erp_meta_columnas " +
                            "(tabla_id, nombre, tipo, nullable, es_contrasena, es_visible, es_sensible, es_archivo, autoincremental, unico, valor_defecto) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)")) {
                        psCol.setLong(1, tablaId);
                        psCol.setString(2, colNombre);
                        psCol.setString(3, mapearTipoMysql(colTipo));
                        psCol.setBoolean(4, nullable);
                        psCol.setBoolean(5, colNombre.toLowerCase().contains("contrasena") || colNombre.toLowerCase().contains("password"));
                        psCol.setBoolean(6, true);
                        psCol.setBoolean(7, false);
                        psCol.setBoolean(8, false);
                        psCol.setBoolean(9, autoInc);
                        psCol.setBoolean(10, esUnico);
                        psCol.executeUpdate();
                    }
                }
            }
        }
    }

    private static void autoRegistrarTablas() {
        String dbCliente = AppConfig.DB_CLIENTE;
        System.out.println("[API] Auto-registrando tablas de '" + dbCliente + "'...");
        try (Connection conn = getConexion(dbCliente)) {
            List<String> tablasCliente = new ArrayList<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
                while (rs.next()) tablasCliente.add(rs.getString(1));
            }
            if (tablasCliente.isEmpty()) {
                System.out.println("[API] No hay tablas en '" + dbCliente + "'. Nada que registrar.");
                return;
            }
            try (Connection connSistema = getConexion(AppConfig.DB_SISTEMA)) {
                for (String tabla : tablasCliente) {
                    if (TABLAS_NUCLEO.contains(tabla.toLowerCase())) continue;
                    if (yaRegistrada(connSistema, tabla)) continue;
                    String nombreAmigable = tabla.replace("_", " ");
                    nombreAmigable = nombreAmigable.substring(0, 1).toUpperCase() + nombreAmigable.substring(1);
                    long tablaId = 0;
                    try (PreparedStatement ps = connSistema.prepareStatement(
                            "INSERT INTO erp_meta_tablas (modulo_id, nombre_logico, nombre_amigable) VALUES (NULL, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, tabla);
                        ps.setString(2, nombreAmigable);
                        ps.executeUpdate();
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            if (keys.next()) tablaId = keys.getLong(1);
                        }
                    }
                    if (tablaId == 0) continue;
                    try (Statement stmtDesc = conn.createStatement();
                         ResultSet rsDesc = stmtDesc.executeQuery("DESCRIBE " + tabla + "")) {
                        while (rsDesc.next()) {
                            String colNombre = rsDesc.getString("Field");
                            String colTipo   = rsDesc.getString("Type");
                            String colNull   = rsDesc.getString("Null");
                            String colKey    = rsDesc.getString("Key");
                            String colExtra  = rsDesc.getString("Extra");
                            boolean nullable = "YES".equalsIgnoreCase(colNull);
                            boolean autoInc  = colExtra != null && colExtra.contains("auto_increment");
                            boolean esPK     = "PRI".equalsIgnoreCase(colKey);
                            boolean esUnico  = "UNI".equalsIgnoreCase(colKey) || esPK;
                            try (PreparedStatement psCol = connSistema.prepareStatement(
                                    "INSERT INTO erp_meta_columnas " +
                                    "(tabla_id, nombre, tipo, nullable, es_contrasena, es_visible, es_sensible, es_archivo, autoincremental, unico, valor_defecto) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)")) {
                                psCol.setLong(1, tablaId);
                                psCol.setString(2, colNombre);
                                psCol.setString(3, mapearTipoMysql(colTipo));
                                psCol.setBoolean(4, nullable);
                                psCol.setBoolean(5, colNombre.toLowerCase().contains("contrasena") || colNombre.toLowerCase().contains("password"));
                                psCol.setBoolean(6, true);
                                psCol.setBoolean(7, false);
                                psCol.setBoolean(8, false);
                                psCol.setBoolean(9, autoInc);
                                psCol.setBoolean(10, esUnico);
                                psCol.executeUpdate();
                            }
                        }
                    }
                    System.out.println("[API] Tabla auto-registrada: " + tabla);
                }
            }
            System.out.println("[API] Auto-registro completado.");
        } catch (SQLException e) {
            System.err.println("[API] Error en auto-registro de tablas: " + e.getMessage());
        }
    }


    private static void asegurarMetaColumnaArchivo(Connection connSistema, String tabla, String columna) throws SQLException {
        long tablaId = 0;
        try (PreparedStatement ps = connSistema.prepareStatement(
                "SELECT id FROM erp_meta_tablas WHERE nombre_logico = ?")) {
            ps.setString(1, tabla);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) tablaId = rs.getLong("id");
            }
        }
        if (tablaId == 0) return;

        int existe = 0;
        try (PreparedStatement ps = connSistema.prepareStatement(
                "SELECT COUNT(*) FROM erp_meta_columnas WHERE tabla_id = ? AND nombre = ?")) {
            ps.setLong(1, tablaId);
            ps.setString(2, columna);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) existe = rs.getInt(1);
            }
        }

        if (existe > 0) {
            try (PreparedStatement ps = connSistema.prepareStatement(
                    "UPDATE erp_meta_columnas SET tipo = 'ARCHIVO', es_archivo = 1, es_visible = 1 WHERE tabla_id = ? AND nombre = ?")) {
                ps.setLong(1, tablaId);
                ps.setString(2, columna);
                ps.executeUpdate();
            }
        } else {
            try (PreparedStatement ps = connSistema.prepareStatement(
                    "INSERT INTO erp_meta_columnas " +
                    "(tabla_id, nombre, tipo, nullable, es_contrasena, es_visible, es_sensible, es_archivo, autoincremental, unico, valor_defecto) " +
                    "VALUES (?, ?, 'ARCHIVO', 1, 0, 1, 0, 1, 0, 0, NULL)")) {
                ps.setLong(1, tablaId);
                ps.setString(2, columna);
                ps.executeUpdate();
            }
        }
    }

    private static boolean yaRegistrada(Connection connSistema, String nombreLogico) throws SQLException {
        try (PreparedStatement ps = connSistema.prepareStatement(
                "SELECT COUNT(*) FROM erp_meta_tablas WHERE nombre_logico = ?")) {
            ps.setString(1, nombreLogico);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static String mapearTipoMysql(String tipoMysql) {
        if (tipoMysql == null) return "TEXTO_CORTO";
        String t = tipoMysql.toUpperCase();
        if (t.equals("TINYINT(1)")) return "BINARIO";
        if (t.startsWith("INT") || t.startsWith("BIGINT") || t.startsWith("SMALLINT") || t.startsWith("TINYINT") || t.startsWith("MEDIUMINT")) return "ENTERO";
        if (t.startsWith("DECIMAL") || t.startsWith("DOUBLE") || t.startsWith("FLOAT") || t.startsWith("NUMERIC")) return "DECIMAL";
        if (t.startsWith("DATE") && !t.startsWith("DATETIME")) return "FECHA";
        if (t.startsWith("DATETIME") || t.startsWith("TIMESTAMP")) return "FECHA_HORA";
        if (t.startsWith("TEXT") || t.startsWith("LONGTEXT") || t.startsWith("MEDIUMTEXT")) return "TEXTO_LARGO";
        if (t.startsWith("BLOB") || t.startsWith("LONGBLOB")) return "ARCHIVO";
        if (t.startsWith("CHAR") && t.contains("60")) return "CONTRASENA";
        return "TEXTO_CORTO";
    }
}