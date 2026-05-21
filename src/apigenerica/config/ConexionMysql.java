package apigenerica.config;

import apigenerica.TipoDatoMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pool de conexiones MySQL usando HikariCP. La URL base incluye erp_sistema
 * como base de datos por defecto.
 *
 * @author Grupo1
 */
public class ConexionMysql {

    private static HikariDataSource ds;
    private static final String URL = "jdbc:mysql://localhost:3306/mysql";
    private static final String USUARIO = "root";
    private static final String PWD = "";

    private static final List<String> TABLAS_NUCLEO = java.util.Arrays.asList("empleados", "productos", "clientes");

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

        // Crear estructura ERP al arrancar
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
        if (ds != null) {
            ds.close();
        }
    }

    // ── Crear tablas de metadatos del ERP si no existen ───────────────────
    private static void crearEstructuraERP() {
        try (Connection conn = getConexion(); Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS `erp_sistema` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            stmt.execute("USE `erp_sistema`");

            // Tabla de configuración global
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `erp_config` ("
                    + "  `clave` VARCHAR(100) PRIMARY KEY,"
                    + "  `valor` TEXT,"
                    + "  `actualizado` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // ── Tabla de roles ────────────────────────────────────────────────────
            // Se crea ANTES que erp_users porque esta tabla la referencia con FK.
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `erp_roles` ("
                    + "  `id` INT AUTO_INCREMENT PRIMARY KEY,"
                    + "  `nombre` VARCHAR(50) NOT NULL UNIQUE,"
                    + "  `descripcion` VARCHAR(255)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `erp_roles_permisos` ("
                    + "  `id` INT AUTO_INCREMENT PRIMARY KEY,"
                    + "  `rol_id` INT NOT NULL,"
                    + "  `tabla_id` INT NOT NULL,"
                    + "  `puede_ver` TINYINT(1) DEFAULT 0,"
                    + "  `puede_crear` TINYINT(1) DEFAULT 0,"
                    + "  `puede_editar` TINYINT(1) DEFAULT 0,"
                    + "  `puede_eliminar` TINYINT(1) DEFAULT 0,"
                    + "  FOREIGN KEY (`tabla_id`) REFERENCES `erp_meta_tablas`(`id`) ON DELETE CASCADE,"
                    + "  FOREIGN KEY (`rol_id`) REFERENCES `erp_roles`(`id`) ON DELETE CASCADE,"
                    + "  UNIQUE KEY `uk_rol_tabla` (`rol`, `tabla`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // ── Tabla de usuarios ─────────────────────────────────────────────────
            //   - Se añade `tipo` ENUM('empleado','cliente'): indica si el usuario
            //     corresponde a la tabla empleados o a la tabla clientes. Antes esto
            //     se deducía del valor del rol ('cliente' → tienda), lo que era frágil
            //     y acoplaba el nombre del rol a la lógica de redirección.
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `erp_users` ("
                    + "  `id` INT AUTO_INCREMENT PRIMARY KEY,"
                    + "  `email` VARCHAR(255) NOT NULL UNIQUE,"
                    + "  `contrasena` CHAR(60) NOT NULL,"
                    + "  `rol_id` INT DEFAULT NULL,"
                    + "  `tipo` ENUM('empleado','cliente') NOT NULL DEFAULT 'empleado',"
                    + "  `activo` TINYINT(1) NOT NULL DEFAULT 1,"
                    + "  FOREIGN KEY (`rol_id`) REFERENCES `erp_roles`(`id`) ON DELETE SET NULL"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // Tabla de módulos
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `erp_modulos` ("
                    + "  `id` INT AUTO_INCREMENT PRIMARY KEY,"
                    + "  `nombre` VARCHAR(100) NOT NULL,"
                    + "  `icono` VARCHAR(50) DEFAULT '📦',"
                    + "  `icon_type` VARCHAR(20) DEFAULT 'emote',"
                    + "  `habilitado` TINYINT(1) DEFAULT 1,"
                    + "  `orden` INT DEFAULT 0"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // Metadatos de tablas
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `erp_meta_tablas` ("
                    + "  `id` INT AUTO_INCREMENT PRIMARY KEY,"
                    + "  `modulo_id` INT DEFAULT NULL,"
                    + "  `nombre_logico` VARCHAR(100) NOT NULL UNIQUE,"
                    + "  `nombre_amigable` VARCHAR(200),"
                    + "  FOREIGN KEY (`modulo_id`) REFERENCES `erp_modulos`(`id`) ON DELETE SET NULL"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // Metadatos de columnas
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `erp_meta_columnas` ("
                    + "  `id` INT AUTO_INCREMENT PRIMARY KEY,"
                    + "  `tabla_id` INT NOT NULL,"
                    + "  `nombre` VARCHAR(100) NOT NULL,"
                    + "  `tipo` VARCHAR(50) NOT NULL,"
                    + "  `nullable` TINYINT(1) DEFAULT 1,"
                    + "  `es_contrasena` TINYINT(1) DEFAULT 0,"
                    + "  `es_visible` TINYINT(1) DEFAULT 1,"
                    + "  `es_sensible` TINYINT(1) DEFAULT 0,"
                    + "  `es_archivo` TINYINT(1) DEFAULT 0,"
                    + "  `autoincremental` TINYINT(1) DEFAULT 0,"
                    + "  `unico` TINYINT(1) DEFAULT 0,"
                    + "  `valor_defecto` VARCHAR(255),"
                    + "  FOREIGN KEY (`tabla_id`) REFERENCES `erp_meta_tablas`(`id`) ON DELETE CASCADE,"
                    + "  UNIQUE KEY `uk_tabla_nombre` (`tabla_id`, `nombre`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // Metadatos de relaciones
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `erp_meta_relaciones` ("
                    + "  `id` INT AUTO_INCREMENT PRIMARY KEY,"
                    + "  `nombre` VARCHAR(100) NOT NULL,"
                    + "  `tabla_origen` INT NOT NULL,"
                    + "  `fk_columna` VARCHAR(100) NOT NULL,"
                    + "  `tabla_destino` VARCHAR(100) NOT NULL,"
                    + "  `cardinalidad` VARCHAR(10) NOT NULL,"
                    + "  FOREIGN KEY (`tabla_origen`) REFERENCES `erp_meta_tablas`(`id`) ON DELETE CASCADE"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // ── BD del cliente ────────────────────────────────────────────────────
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + AppConfig.DB_CLIENTE + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            stmt.execute("USE `" + AppConfig.DB_CLIENTE + "`");

            // ── Tabla de Empleados ────────────────────────────────────────────────
            // CAMBIO: se añade `user_id` INT NULL FK → erp_sistema.erp_users(id).
            //   - NULL por defecto: un empleado puede existir en el sistema sin
            //     tener acceso web (p.ej. empleados dados de alta manualmente que
            //     aún no tienen cuenta). Solo se rellena cuando se les crea usuario.
            //   - ON DELETE SET NULL: si se borra el usuario de erp_users el
            //     empleado sigue existiendo pero pierde el vínculo con la cuenta.
            //   - `cargo` se mantiene como VARCHAR: representa la función laboral
            //     (Contable, Técnico...) y es independiente del rol de acceso web.
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS productos (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  nombre VARCHAR(150) NOT NULL," +
                "  descripcion TEXT DEFAULT NULL," +
                "  referencia VARCHAR(150) DEFAULT NULL UNIQUE," +
                "  cantidad INT DEFAULT NULL," +
                "  precio DECIMAL(10, 2) NOT NULL," +
                "  foto_url VARCHAR(255) DEFAULT NULL," +
                "  esta_agotado BOOLEAN NOT NULL DEFAULT FALSE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            // Tabla de Producto
            stmt.executeUpdate(
                 "CREATE TABLE IF NOT EXISTS productos (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  nombre VARCHAR(150) NOT NULL," +
                "  descripcion TEXT DEFAULT NULL," +
                "  referencia VARCHAR(150) DEFAULT NULL UNIQUE," +
                "  cantidad INT DEFAULT NULL," +
                "  precio DECIMAL(10, 2) NOT NULL," +
                "  foto_url VARCHAR(255) DEFAULT NULL," +
                "  esta_agotado BOOLEAN NOT NULL DEFAULT FALSE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
            // ── Tabla de Clientes ─────────────────────────────────────────────────
            // CAMBIO: se añade `user_id` INT NULL FK → erp_sistema.erp_users(id).
            //   Misma lógica que empleados: NULL cuando el cliente solo existe como
            //   registro de negocio, se rellena cuando se registra en la tienda web.
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `clientes` ("
                    + "  `id` INT AUTO_INCREMENT PRIMARY KEY,"
                    + "  `nombre` VARCHAR(100) NOT NULL,"
                    + "  `apellido` VARCHAR(100) DEFAULT NULL,"
                    + "  `cif_nif` VARCHAR(20) NOT NULL UNIQUE,"
                    + "  `telefono` VARCHAR(20) DEFAULT NULL,"
                    + "  `email` VARCHAR(100) DEFAULT NULL,"
                    + "  `direccion` TEXT DEFAULT NULL,"
                    + "  `creado_en` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                    + "  `user_id` INT DEFAULT NULL,"
                    + "  CONSTRAINT `fk_clientes_user`"
                    + "    FOREIGN KEY (`user_id`) REFERENCES `erp_sistema`.`erp_users`(`id`)"
                    + "    ON DELETE SET NULL ON UPDATE CASCADE"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            // ── Datos iniciales ───────────────────────────────────────────────────
            stmt.execute("USE `erp_sistema`");

            // Insertar roles por defecto si la tabla está vacía
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM `erp_roles`")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.executeUpdate(
                            "INSERT INTO `erp_roles` (nombre, descripcion) VALUES "
                            + "('admin',    'Administrador con acceso total'),"
                            + "('empleado', 'Empleado con acceso basico'),"
                            + "('cliente',  'Cliente con acceso a la tienda')"
                    );
                    System.out.println("[API] Roles por defecto creados (admin, empleado, cliente).");
                }
            }

            // ── Usuario admin por defecto ─────────────────────────────────────────
            // Se crea solo si no existe ningún usuario en erp_users.
            // La contraseña se hashea con BCrypt (cost 10) igual que hace el resto
            // de la API, así el login funciona sin ningún cambio adicional.
            // Credenciales iniciales: admin@empresa.com / admin1234
            // Cambia la contraseña en el primer arranque en producción.
            try (ResultSet rsU = stmt.executeQuery("SELECT COUNT(*) FROM `erp_users`")) {
                if (rsU.next() && rsU.getInt(1) == 0) {
                    String hashAdmin = org.mindrot.jbcrypt.BCrypt.hashpw(
                            "admin1234",
                            org.mindrot.jbcrypt.BCrypt.gensalt(10)
                    );
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO `erp_users` (email, contrasena, rol_id, tipo, activo) "
                            + "VALUES (?, ?, (SELECT id FROM `erp_roles` WHERE nombre = 'admin'), 'empleado', 1)")) {
                        ps.setString(1, "admin@empresa.com");
                        ps.setString(2, hashAdmin);
                        ps.executeUpdate();
                    }
                    System.out.println("[API] ================================================");
                    System.out.println("[API] Usuario admin creado:");
                    System.out.println("[API] Email: admin@empresa.com");
                    System.out.println("[API] Contraseña: admin1234");
                    System.out.println("[API] Cambia la contraseña tras el primer login.");
                    System.out.println("[API] ================================================");
                }
            }

            inicializarModulosYTablasDelNucleo(conn);

            System.out.println("[API] Estructura de metadatos sincronizada.");

            // Auto-registrar tablas existentes en la BD del cliente
            autoRegistrarTablas();

        } catch (SQLException e) {
            System.err.println("[API] Error creando estructura ERP: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void inicializarModulosYTablasDelNucleo(Connection conn) throws SQLException {
        conn.setCatalog(AppConfig.DB_SISTEMA);

        // Crear el único módulo contenedor
        long idModuloUnico = asegurarModulo(conn, "Gestión Central", "⚙️", 1);

        // Asociar las tres tablas al mismo idModuloUnico
        asegurarMetaTabla(conn, idModuloUnico, "empleados", "Gestión de Empleados");
        asegurarMetaTabla(conn, idModuloUnico, "productos", "Catálogo de Productos");
        asegurarMetaTabla(conn, idModuloUnico, "clientes", "Cartera de Clientes");
    }

    private static long asegurarModulo(Connection conn, String nombre, String icono, int orden) throws SQLException {
        String queryBusqueda = "SELECT id FROM `erp_modulos` WHERE nombre = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(queryBusqueda)) {
            pstmt.setString(1, nombre);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        }

        String queryInsert = "INSERT INTO `erp_modulos` (nombre, icono, icon_type, habilitado, orden) VALUES (?, ?, 'emote', 1, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(queryInsert, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, nombre);
            pstmt.setString(2, icono);
            pstmt.setInt(3, orden);
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return 0;
    }

    private static void asegurarMetaTabla(Connection conn, long moduloId, String nombreLogico, String nombreAmigable) throws SQLException {
        if (yaRegistrada(conn, nombreLogico)) {
            String queryUpdate = "UPDATE `erp_meta_tablas` SET modulo_id = ?, nombre_amigable = ? WHERE nombre_logico = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(queryUpdate)) {
                pstmt.setLong(1, moduloId);
                pstmt.setString(2, nombreAmigable);
                pstmt.setString(3, nombreLogico);
                pstmt.executeUpdate();
            }
            return;
        }

        String queryInsert = "INSERT INTO `erp_meta_tablas` (modulo_id, nombre_logico, nombre_amigable) VALUES (?, ?, ?)";
        long tablaId = 0;
        try (PreparedStatement pstmt = conn.prepareStatement(queryInsert, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, moduloId);
            pstmt.setString(2, nombreLogico);
            pstmt.setString(3, nombreAmigable);
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) {
                    tablaId = keys.getLong(1);
                }
            }
        }

        if (tablaId > 0) {
            try (Connection connCliente = getConexion(AppConfig.DB_CLIENTE)) {
                registrarColumnas(connCliente, conn, nombreLogico, tablaId);
            }
        }
    }

    // ── Auto-registro: escanea BD cliente y registra tablas sin metadatos ─
    private static void autoRegistrarTablas() throws SQLException {
        String dbCliente = AppConfig.DB_CLIENTE;
        System.out.println("[API] Auto-registrando tablas de '" + dbCliente + "'...");

        try (Connection conn = getConexion(dbCliente)) {

            // Obtener todas las tablas del cliente
            List<String> tablasCliente = new ArrayList<>();
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
                while (rs.next()) {
                    tablasCliente.add(rs.getString(1));
                }
            }

            if (tablasCliente.isEmpty()) {
                System.out.println("[API] No hay tablas en '" + dbCliente + "'. Nada que registrar.");
                return;
            }

            // Registrar las que falten en erp_meta_tablas
            try (Connection connSistema = getConexion(AppConfig.DB_SISTEMA)) {
                for (String tabla : tablasCliente) {
                    if (TABLAS_NUCLEO.contains(tabla.toLowerCase())) {
                        continue;
                    }

                    if (yaRegistrada(connSistema, tabla)) {
                        continue;
                    }

                    String nombreAmigable = tabla.replace("_", " ");
                    nombreAmigable = nombreAmigable.substring(0, 1).toUpperCase() + nombreAmigable.substring(1);

                    long tablaId = 0;
                    try (PreparedStatement pstmt = connSistema.prepareStatement(
                            "INSERT INTO `erp_meta_tablas` (modulo_id, nombre_logico, nombre_amigable) VALUES (NULL, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        pstmt.setString(1, tabla);
                        pstmt.setString(2, nombreAmigable);
                        pstmt.executeUpdate();
                        try (ResultSet keys = pstmt.getGeneratedKeys()) {
                            if (keys.next()) {
                                tablaId = keys.getLong(1);
                            }
                        }
                    }

                    if (tablaId == 0) {
                        continue;
                    }

                    // Registrar columnas usando DatabaseMetaData
                    registrarColumnas(conn, connSistema, tabla, tablaId);
                }
            }
        }
    }

    private static void registrarColumnas(Connection connCliente, Connection connSistema,
            String tabla, long tablaId) throws SQLException {
        DatabaseMetaData meta = connCliente.getMetaData();

        try (ResultSet rsCols = meta.getColumns(connCliente.getCatalog(), null,
                tabla, null)) {
            Set<String> primaryKeys = new HashSet<>();
            Set<String> uniqueKeys = new HashSet<>();

            try (ResultSet rsPk = meta.getPrimaryKeys(connCliente.getCatalog(),
                    null, tabla)) {
                while (rsPk.next()) {
                    primaryKeys.add(rsPk.getString("COLUMN_NAME").toLowerCase());
                }
            }
            
            try (ResultSet rsIdx = meta.getIndexInfo(connCliente.getCatalog(),
                    null, tabla, true, false)) {
                while (rsIdx.next()) {
                    uniqueKeys.add(rsIdx.getString("COLUMN_NAME").toLowerCase());
                }
            }
            
                while (rsCols.next()) {
                    String colNombre = rsCols.getString("COLUMN_NAME");
                    int sqlType = rsCols.getInt("DATA_TYPE");
                    String tipoGenerico = TipoDatoMapper.toTexto(sqlType);
                    boolean nullable = DatabaseMetaData.columnNullable == rsCols.getInt("NULLABLE");
                    boolean autoInc = "YES".equalsIgnoreCase(rsCols.getString("IS_AUTOINCREMENT"));
                    boolean esPk = primaryKeys.contains(colNombre.toLowerCase());
                    boolean esUnico = esPk || uniqueKeys.contains(colNombre.toLowerCase());
                    String valorDefecto = rsCols.getString("COLUMN_DEF");

                    try (PreparedStatement pstmtCol = connSistema.prepareStatement(
                            "INSERT INTO `erp_meta_columnas` "
                            + "(tabla_id, nombre, tipo, nullable, "
                            + "es_contrasena, es_visible, "
                            + "es_sensible, es_archivo, "
                            + "autoincremental, unico, valor_defecto) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {

                        pstmtCol.setLong(1, tablaId);
                        pstmtCol.setString(2, colNombre);
                        pstmtCol.setString(3, tipoGenerico);
                        pstmtCol.setBoolean(4, nullable);
                        pstmtCol.setBoolean(5, colNombre.toLowerCase().contains("contrasena")
                                || colNombre.toLowerCase().contains("password"));
                        pstmtCol.setBoolean(6, true);
                        pstmtCol.setBoolean(7, false);
                        pstmtCol.setBoolean(8, false);
                        pstmtCol.setBoolean(9, autoInc);
                        pstmtCol.setBoolean(10, esUnico);
                        pstmtCol.setString(11, valorDefecto);

                        pstmtCol.executeUpdate();
                    }
                }
            }
        }

    

    private static boolean yaRegistrada(Connection connSistema, String nombreLogico) throws SQLException {
        try (PreparedStatement pstmt = connSistema.prepareStatement(
                "SELECT COUNT(*) FROM `erp_meta_tablas` WHERE nombre_logico = ?")) {
            pstmt.setString(1, nombreLogico);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
}
