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
 * La URL base incluye erp_sistema como base de datos por defecto.
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
        if (ds != null) ds.close();
    }

    // ── Crear tablas de metadatos del ERP si no existen ───────────────────
    private static void crearEstructuraERP() {
        try (Connection conn = getConexion(); Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS `erp_sistema` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            stmt.execute("USE `erp_sistema`");

            // Tabla de configuración global
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `erp_config` (" +
                "  `clave` VARCHAR(100) PRIMARY KEY," +
                "  `valor` TEXT," +
                "  `actualizado` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // ── Tabla de roles ────────────────────────────────────────────────────
            // Se crea ANTES que erp_users porque esta tabla la referencia con FK.
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `erp_roles` (" +
                "  `id` INT AUTO_INCREMENT PRIMARY KEY," +
                "  `nombre` VARCHAR(50) NOT NULL UNIQUE," +
                "  `descripcion` VARCHAR(255)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `erp_roles_permisos` (" +
                "  `id` INT AUTO_INCREMENT PRIMARY KEY," +
                "  `rol` VARCHAR(50) NOT NULL," +
                "  `tabla` VARCHAR(100) NOT NULL," +
                "  `puede_ver` TINYINT(1) DEFAULT 0," +
                "  `puede_crear` TINYINT(1) DEFAULT 0," +
                "  `puede_editar` TINYINT(1) DEFAULT 0," +
                "  `puede_eliminar` TINYINT(1) DEFAULT 0," +
                "  UNIQUE KEY `uk_rol_tabla` (`rol`, `tabla`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // ── Tabla de usuarios ─────────────────────────────────────────────────
            // CAMBIOS respecto a la versión anterior:
            //   - Se elimina `rol` VARCHAR(50): era un string suelto sin FK real,
            //     lo que permitía valores inválidos y se desincronizaba con erp_roles.
            //   - Se añade `rol_id` INT FK → erp_roles(id): ahora la BD garantiza
            //     que el rol asignado siempre existe. ON DELETE SET NULL para que
            //     si se borra un rol los usuarios queden con rol_id NULL en vez de
            //     que falle la eliminación.
            //   - Se añade `tipo` ENUM('empleado','cliente'): indica si el usuario
            //     corresponde a la tabla empleados o a la tabla clientes. Antes esto
            //     se deducía del valor del rol ('cliente' → tienda), lo que era frágil
            //     y acoplaba el nombre del rol a la lógica de redirección.
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `erp_users` (" +
                "  `id` INT AUTO_INCREMENT PRIMARY KEY," +
                "  `email` VARCHAR(255) NOT NULL UNIQUE," +
                "  `contrasena` CHAR(60) NOT NULL," +
                "  `rol_id` INT DEFAULT NULL," +
                "  `tipo` ENUM('empleado','cliente') NOT NULL DEFAULT 'empleado'," +
                "  `activo` TINYINT(1) NOT NULL DEFAULT 1," +
                "  FOREIGN KEY (`rol_id`) REFERENCES `erp_roles`(`id`) ON DELETE SET NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            
            //  NUEVO: índice MySQL de ficheros almacenados 
            // Permite listar, auditar y buscar ficheros sin abrir db4o.
            // La columna esta_en_disco indica si el contenido está en un .enc en disco
            // (archivos > 20 MB) o en db4o (archivos ≤ 20 MB).
            // tipo_detectado recoge el resultado del análisis de magic bytes cuando el
            // archivo no tenía extensión o su mime era application/octet-stream.
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS erp_ficheros ("
                    + "  id             INT AUTO_INCREMENT PRIMARY KEY,"
                    + "  uuid           VARCHAR(36)   NOT NULL UNIQUE,"
                    + "  nombre_original VARCHAR(500),"
                    + "  mime_type      VARCHAR(100),"
                    + "  tipo_detectado VARCHAR(50),"
                    + // magic bytes: PDF, JPG, DESCONOCIDO…
                    "  tamano_bytes   BIGINT,"
                    + "  esta_en_disco  TINYINT(1)    DEFAULT 0,"
                    + // 0=db4o, 1=fichero .enc en disco
                    "  ruta_disco     VARCHAR(1000),"
                    + // solo si esta_en_disco=1
                    "  tabla_origen   VARCHAR(100),"
                    + // tabla del cliente que lo referencia
                    "  fecha_subida   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            
            // Migración idempotente: si la tabla ya existía con la columna `rol`
            // antigua, añadimos las nuevas columnas sin tocar los datos existentes.
            // Los try/catch evitan que falle si las columnas ya fueron añadidas en
            // un arranque anterior.
            try {
                stmt.executeUpdate("ALTER TABLE `erp_users` ADD COLUMN `rol_id` INT DEFAULT NULL");
                System.out.println("[API] Columna rol_id añadida a erp_users.");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE `erp_users` ADD COLUMN `tipo` ENUM('empleado','cliente') NOT NULL DEFAULT 'empleado'");
                System.out.println("[API] Columna tipo añadida a erp_users.");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE `erp_users` ADD CONSTRAINT `fk_users_rol` FOREIGN KEY (`rol_id`) REFERENCES `erp_roles`(`id`) ON DELETE SET NULL");
                System.out.println("[API] FK fk_users_rol añadida a erp_users.");
            } catch (SQLException ignored) {}

            // Tabla de módulos
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `erp_modulos` (" +
                "  `id` INT AUTO_INCREMENT PRIMARY KEY," +
                "  `nombre` VARCHAR(100) NOT NULL," +
                "  `icono` VARCHAR(50) DEFAULT '📦'," +
                "  `icon_type` VARCHAR(20) DEFAULT 'emote'," +
                "  `habilitado` TINYINT(1) DEFAULT 1," +
                "  `orden` INT DEFAULT 0" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // Metadatos de tablas (modulo_id nullable para auto-registro)
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `erp_meta_tablas` (" +
                "  `id` INT AUTO_INCREMENT PRIMARY KEY," +
                "  `modulo_id` INT DEFAULT NULL," +
                "  `nombre_logico` VARCHAR(100) NOT NULL UNIQUE," +
                "  `nombre_amigable` VARCHAR(200)," +
                "  FOREIGN KEY (`modulo_id`) REFERENCES `erp_modulos`(`id`) ON DELETE SET NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // Asegurar que modulo_id sea nullable
            try {
                stmt.executeUpdate("ALTER TABLE `erp_meta_tablas` MODIFY `modulo_id` INT DEFAULT NULL");
            } catch (SQLException ignored) {}

            // Metadatos de columnas
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `erp_meta_columnas` (" +
                "  `id` INT AUTO_INCREMENT PRIMARY KEY," +
                "  `tabla_id` INT NOT NULL," +
                "  `nombre` VARCHAR(100) NOT NULL," +
                "  `tipo` VARCHAR(50) NOT NULL," +
                "  `nullable` TINYINT(1) DEFAULT 1," +
                "  `es_contrasena` TINYINT(1) DEFAULT 0," +
                "  `es_visible` TINYINT(1) DEFAULT 1," +
                "  `es_sensible` TINYINT(1) DEFAULT 0," +
                "  `es_archivo` TINYINT(1) DEFAULT 0," +
                "  `autoincremental` TINYINT(1) DEFAULT 0," +
                "  `unico` TINYINT(1) DEFAULT 0," +
                "  `valor_defecto` VARCHAR(255)," +
                "  FOREIGN KEY (`tabla_id`) REFERENCES `erp_meta_tablas`(`id`) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // Metadatos de relaciones
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `erp_meta_relaciones` (" +
                "  `id` INT AUTO_INCREMENT PRIMARY KEY," +
                "  `nombre` VARCHAR(100) NOT NULL," +
                "  `tabla_origen` INT NOT NULL," +
                "  `fk_columna` VARCHAR(100) NOT NULL," +
                "  `tabla_destino` VARCHAR(100) NOT NULL," +
                "  `cardinalidad` VARCHAR(10) NOT NULL," +
                "  FOREIGN KEY (`tabla_origen`) REFERENCES `erp_meta_tablas`(`id`) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
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
                "CREATE TABLE IF NOT EXISTS `empleados` (" +
                "  `id` INT AUTO_INCREMENT PRIMARY KEY," +
                "  `correo_electronico` VARCHAR(255) NOT NULL UNIQUE," +
                "  `nombre` VARCHAR(100) NOT NULL," +
                "  `primer_apellido` VARCHAR(100) NOT NULL," +
                "  `segundo_apellido` VARCHAR(100) DEFAULT NULL," +
                "  `dni_nie` VARCHAR(20) NOT NULL UNIQUE," +
                "  `telefono` VARCHAR(20) DEFAULT NULL," +
                "  `direccion` VARCHAR(255) DEFAULT NULL," +
                "  `iban` VARCHAR(34) DEFAULT NULL," +
                "  `nss` VARCHAR(20) DEFAULT NULL," +
                "  `cargo` VARCHAR(100) DEFAULT 'Personal'," +
                "  `foto_url` VARCHAR(255) DEFAULT NULL," +
                "  `user_id` INT DEFAULT NULL," +
                "  CONSTRAINT `fk_empleados_user`" +
                "    FOREIGN KEY (`user_id`) REFERENCES `erp_sistema`.`erp_users`(`id`)" +
                "    ON DELETE SET NULL ON UPDATE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            // Migración idempotente para empleados que ya existan sin user_id
            try {
                stmt.executeUpdate("ALTER TABLE `empleados` ADD COLUMN `user_id` INT DEFAULT NULL");
                stmt.executeUpdate(
                    "ALTER TABLE `empleados` ADD CONSTRAINT `fk_empleados_user` " +
                    "FOREIGN KEY (`user_id`) REFERENCES `erp_sistema`.`erp_users`(`id`) " +
                    "ON DELETE SET NULL ON UPDATE CASCADE"
                );
                System.out.println("[API] Columna user_id añadida a empleados.");
            } catch (SQLException ignored) {}

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
                "CREATE TABLE IF NOT EXISTS `clientes` (" +
                "  `id` INT AUTO_INCREMENT PRIMARY KEY," +
                "  `nombre` VARCHAR(100) NOT NULL," +
                "  `apellido` VARCHAR(100) DEFAULT NULL," +
                "  `cif_nif` VARCHAR(20) NOT NULL UNIQUE," +
                "  `telefono` VARCHAR(20) DEFAULT NULL," +
                "  `email` VARCHAR(100) DEFAULT NULL," +
                "  `direccion` TEXT DEFAULT NULL," +
                "  `creado_en` TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  `user_id` INT DEFAULT NULL," +
                "  CONSTRAINT `fk_clientes_user`" +
                "    FOREIGN KEY (`user_id`) REFERENCES `erp_sistema`.`erp_users`(`id`)" +
                "    ON DELETE SET NULL ON UPDATE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
            
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `incidencias` (" +
                "  `id` INT AUTO_INCREMENT PRIMARY KEY," +
                "  `nombre` VARCHAR(255) NOT NULL," +
                "  `email` VARCHAR(255) NOT NULL," +
                "  `descripcion` TEXT NOT NULL," +
                "  `fecha` TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
            

            // Migración idempotente para clientes que ya existan sin user_id
            try {
                stmt.executeUpdate("ALTER TABLE `clientes` ADD COLUMN `user_id` INT DEFAULT NULL");
                stmt.executeUpdate(
                    "ALTER TABLE `clientes` ADD CONSTRAINT `fk_clientes_user` " +
                    "FOREIGN KEY (`user_id`) REFERENCES `erp_sistema`.`erp_users`(`id`) " +
                    "ON DELETE SET NULL ON UPDATE CASCADE"
                );
                System.out.println("[API] Columna user_id añadida a clientes.");
            } catch (SQLException ignored) {}

            // ── Datos iniciales ───────────────────────────────────────────────────
            stmt.execute("USE `erp_sistema`");

            // Insertar roles por defecto si la tabla está vacía
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM `erp_roles`")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.executeUpdate(
                        "INSERT INTO `erp_roles` (nombre, descripcion) VALUES " +
                        "('admin',    'Administrador con acceso total')," +
                        "('empleado', 'Empleado con acceso basico')," +
                        "('cliente',  'Cliente con acceso a la tienda')"
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
                        "INSERT INTO `erp_users` (email, contrasena, rol_id, tipo, activo) " +
                        "VALUES (?, ?, (SELECT id FROM `erp_roles` WHERE nombre = 'admin'), 'empleado', 1)")) {
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

        // 1. Crear el único módulo contenedor
        long idModuloUnico = asegurarModulo(conn, "Gestión Central", "⚙️", 1);

        // 2. Asociar las tres tablas al mismo idModuloUnico
        asegurarMetaTabla(conn, idModuloUnico, "empleados", "Gestión de Empleados");
        asegurarMetaTabla(conn, idModuloUnico, "productos",  "Catálogo de Productos");
        asegurarMetaTabla(conn, idModuloUnico, "clientes",   "Cartera de Clientes");
    }

    private static long asegurarModulo(Connection conn, String nombre, String icono, int orden) throws SQLException {
        String queryBusqueda = "SELECT id FROM `erp_modulos` WHERE nombre = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(queryBusqueda)) {
            pstmt.setString(1, nombre);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getLong("id");
            }
        }

        String queryInsert = "INSERT INTO `erp_modulos` (nombre, icono, icon_type, habilitado, orden) VALUES (?, ?, 'emote', 1, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(queryInsert, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, nombre);
            pstmt.setString(2, icono);
            pstmt.setInt(3, orden);
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
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
                if (keys.next()) tablaId = keys.getLong(1);
            }
        }

        if (tablaId > 0) {
            try (Connection connCliente = getConexion(AppConfig.DB_CLIENTE);
                 Statement stmtDesc = connCliente.createStatement();
                 ResultSet rsDesc = stmtDesc.executeQuery("DESCRIBE `" + nombreLogico + "`")) {
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
                    String tipoGenerico = mapearTipoMysql(colTipo);

                    try (PreparedStatement pstmtCol = conn.prepareStatement(
                            "INSERT INTO `erp_meta_columnas` " +
                            "(tabla_id, nombre, tipo, nullable, es_contrasena, es_visible, es_sensible, es_archivo, autoincremental, unico, valor_defecto) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)")) {
                        pstmtCol.setLong(1, tablaId);
                        pstmtCol.setString(2, colNombre);
                        pstmtCol.setString(3, tipoGenerico);
                        pstmtCol.setBoolean(4, nullable);
                        pstmtCol.setBoolean(5, colNombre.toLowerCase().contains("contrasena") || colNombre.toLowerCase().contains("password"));
                        pstmtCol.setBoolean(6, true);
                        pstmtCol.setBoolean(7, false);
                        pstmtCol.setBoolean(8, false);
                        pstmtCol.setBoolean(9, autoInc);
                        pstmtCol.setBoolean(10, esUnico);
                        pstmtCol.executeUpdate();
                    }
                }
            }
        }
    }

    // ── Auto-registro: escanea BD cliente y registra tablas sin metadatos ─
    private static void autoRegistrarTablas() {
        String dbCliente = AppConfig.DB_CLIENTE;
        System.out.println("[API] Auto-registrando tablas de '" + dbCliente + "'...");

        try (Connection conn = getConexion(dbCliente)) {

            // 1. Obtener todas las tablas del cliente
            List<String> tablasCliente = new ArrayList<>();
            try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
                while (rs.next()) {
                    tablasCliente.add(rs.getString(1));
                }
            }

            if (tablasCliente.isEmpty()) {
                System.out.println("[API] No hay tablas en '" + dbCliente + "'. Nada que registrar.");
                return;
            }

            // 2. Registrar las que falten en erp_meta_tablas
            try (Connection connSistema = getConexion(AppConfig.DB_SISTEMA)) {
                for (String tabla : tablasCliente) {
                    if (TABLAS_NUCLEO.contains(tabla.toLowerCase())) continue;

                    if (yaRegistrada(connSistema, tabla)) continue;

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
                            if (keys.next()) tablaId = keys.getLong(1);
                        }
                    }

                    if (tablaId == 0) continue;

                    // 3. Registrar columnas usando DESCRIBE
                    try (Statement stmtDesc = conn.createStatement();
                         ResultSet rsDesc = stmtDesc.executeQuery("DESCRIBE `" + tabla + "`")) {
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
                            String tipoGenerico = mapearTipoMysql(colTipo);

                            try (PreparedStatement pstmtCol = connSistema.prepareStatement(
                                    "INSERT INTO `erp_meta_columnas` " +
                                    "(tabla_id, nombre, tipo, nullable, es_contrasena, es_visible, es_sensible, es_archivo, autoincremental, unico, valor_defecto) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)")) {
                                pstmtCol.setLong(1, tablaId);
                                pstmtCol.setString(2, colNombre);
                                pstmtCol.setString(3, tipoGenerico);
                                pstmtCol.setBoolean(4, nullable);
                                pstmtCol.setBoolean(5, colNombre.toLowerCase().contains("contrasena") || colNombre.toLowerCase().contains("password"));
                                pstmtCol.setBoolean(6, true);   // visible
                                pstmtCol.setBoolean(7, false);  // no sensible
                                pstmtCol.setBoolean(8, false);  // no archivo
                                pstmtCol.setBoolean(9, autoInc);
                                pstmtCol.setBoolean(10, esUnico);
                                pstmtCol.executeUpdate();
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

    private static boolean yaRegistrada(Connection connSistema, String nombreLogico) throws SQLException {
        try (PreparedStatement pstmt = connSistema.prepareStatement(
                "SELECT COUNT(*) FROM `erp_meta_tablas` WHERE nombre_logico = ?")) {
            pstmt.setString(1, nombreLogico);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
    

    // ── Mapear tipo MySQL → tipo genérico de la API ───────────────────────
    private static String mapearTipoMysql(String tipoMysql) {
        if (tipoMysql == null) return "TEXTO_CORTO";
        String tipoUpper = tipoMysql.toUpperCase();

        if (tipoUpper.equals("TINYINT(1)")) return "BINARIO";
        if (tipoUpper.startsWith("INT") || tipoUpper.startsWith("BIGINT") ||
            tipoUpper.startsWith("SMALLINT") || tipoUpper.startsWith("TINYINT") ||
            tipoUpper.startsWith("MEDIUMINT")) return "ENTERO";
        if (tipoUpper.startsWith("DECIMAL") || tipoUpper.startsWith("DOUBLE") ||
            tipoUpper.startsWith("FLOAT") || tipoUpper.startsWith("NUMERIC")) return "DECIMAL";
        if (tipoUpper.startsWith("DATE") && !tipoUpper.startsWith("DATETIME")) return "FECHA";
        if (tipoUpper.startsWith("DATETIME") || tipoUpper.startsWith("TIMESTAMP")) return "FECHA_HORA";
        if (tipoUpper.startsWith("TEXT") || tipoUpper.startsWith("LONGTEXT") ||
            tipoUpper.startsWith("MEDIUMTEXT")) return "TEXTO_LARGO";
        if (tipoUpper.startsWith("BLOB") || tipoUpper.startsWith("LONGBLOB")) return "ARCHIVO";
        if (tipoUpper.startsWith("CHAR") && tipoUpper.contains("60")) return "CONTRASENA";

        return "TEXTO_CORTO";
    }
}