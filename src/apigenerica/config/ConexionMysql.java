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

    /**
     * Inicializa el pool de conexiones y crea las tablas de metadatos del ERP
     * si no existen.
     */
    public static void inicializar() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(URL + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
        config.setUsername(USUARIO);
        config.setPassword(PWD);
        config.setMaximumPoolSize(10); // Máximo 10 conexiones simultáneas
        config.setMinimumIdle(2); // Mínimo 2 conexiones en espera
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        ds = new HikariDataSource(config);

        // Crear la BD y tablas de metadatos del ERP al arrancar
        crearEstructuraERP();
    }

    /**
     * Obtiene una conexión del pool SIN base de datos seleccionada. Usa
     * conn.setCatalog(nombreBd) para cambiar de BD.
     *
     * @return Conexión de MySQL
     * @throws java.sql.SQLException
     */
    public static Connection getConexion() throws SQLException {
        return ds.getConnection();
    }

    /**
     * Obtiene una conexión del pool CON una base de datos específica.
     *
     * @param baseDatos Nombre de la base de datos
     * @return Conexión de MySQL
     * @throws java.sql.SQLException
     */
    public static Connection getConexion(String baseDatos) throws SQLException {
        Connection conn = ds.getConnection();
        if (baseDatos != null && !baseDatos.trim().isEmpty()) {
            conn.setCatalog(baseDatos);
        }
        return conn;
    }

    /**
     * Cierra el pool de conexiones.
     */
    public static void cerrar() {
        if (ds != null) {
            ds.close();
        }
    }

    /**
     * Crea la base de datos 'erp_sistema' y las tablas de metadatos necesarias
     * para el funcionamiento del ERP.
     */
    private static void crearEstructuraERP() {
        try (Connection conn = getConexion(); Statement stmt = conn.createStatement()) {
            // Base de datos del sistema ERP
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS `erp_sistema` "
                    + "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            stmt.execute("USE `erp_sistema` ");

            // Tabla de configuración global de la empresa
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `erp_config` ("
                    + "  `clave` VARCHAR(100) PRIMARY KEY,"
                    + "  `valor` TEXT,"
                    + "  `actualizado` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // Tabla de usuarios de la aplicación
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `erp_users` ("
                    + "  `id` INT AUTO_INCREMENT PRIMARY KEY,"
                    + "  `email` VARCHAR(255) NOT NULL,"
                    + "  `contrasena` CHAR(60) NOT NULL,"
                    + "  `rol` VARCHAR(50) NOT NULL,"
                    + "  `activo` TINYINT(1) NOT NULL DEFAULT 1"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // Tabla de módulos instalados
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

            // Tabla de metadatos de tablas (modulo_id nullable para auto-registro)
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `erp_meta_tablas` ("
                    + "  `id` INT AUTO_INCREMENT PRIMARY KEY,"
                    + "  `modulo_id` INT DEFAULT NULL,"
                    + "  `nombre_logico` VARCHAR(100) NOT NULL UNIQUE,"
                    + "  `nombre_amigable` VARCHAR(200),"
                    + "  FOREIGN KEY (`modulo_id`) REFERENCES `erp_modulos`(`id`) ON DELETE SET NULL"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // Asegurar que modulo_id sea nullable en instalaciones existentes
            try {
                stmt.executeUpdate(
                        "ALTER TABLE `erp_meta_tablas` MODIFY `modulo_id` INT DEFAULT NULL"
                );
            } catch (SQLException ignored) {
                // Ya es nullable o no se puede cambiar
            }

            // Tabla de metadatos de columnas
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
                    + "  FOREIGN KEY (`tabla_id`) REFERENCES `erp_meta_tablas`(`id`) ON DELETE CASCADE"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // Tabla de metadatos de relaciones
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

            // Tabla de roles
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `erp_roles` ("
                    + "  `id` INT AUTO_INCREMENT PRIMARY KEY,"
                    + "  `nombre` VARCHAR(50) NOT NULL UNIQUE,"
                    + "  `descripcion` VARCHAR(255)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // Tabla de permisos por rol
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `erp_roles_permisos` ("
                    + "  `id` INT AUTO_INCREMENT PRIMARY KEY,"
                    + "  `rol` VARCHAR(50) NOT NULL,"
                    + "  `tabla` VARCHAR(100) NOT NULL,"
                    + "  `puede_ver` TINYINT(1) DEFAULT 0,"
                    + "  `puede_crear` TINYINT(1) DEFAULT 0,"
                    + "  `puede_editar` TINYINT(1) DEFAULT 0,"
                    + "  `puede_eliminar` TINYINT(1) DEFAULT 0,"
                    + "  UNIQUE KEY `uk_rol_tabla` (`rol`, `tabla`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // Asegurar que la BD del cliente exista
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + AppConfig.DB_CLIENTE + "` "
                    + "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");

            // Insertar rol admin por defecto si la tabla esta vacia
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM `erp_roles`")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.executeUpdate("INSERT INTO `erp_roles` (nombre, descripcion) VALUES "
                            + "('admin', 'Administrador con acceso total'), "
                            + "('empleado', 'Empleado con acceso basico')");
                    System.out.println("[API] Roles por defecto creados (admin, empleado).");
                }
            }

            //  NUEVO: índice MySQL de ficheros almacenados 
            // Permite listar, auditar y buscar ficheros sin abrir db4o.
            // La columna esta_en_disco indica si el contenido está en un .enc en disco
            // (archivos > 20 MB) o en db4o (archivos ≤ 20 MB).
            // tipo_detectado recoge el resultado del análisis de magic bytes cuando el
            // archivo no tenía extensión o su mime era application/octet-stream.
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `erp_ficheros` ("
                    + "  `id`             INT AUTO_INCREMENT PRIMARY KEY,"
                    + "  `uuid`           VARCHAR(36)   NOT NULL UNIQUE,"
                    + "  `nombre_original` VARCHAR(500),"
                    + "  `mime_type`      VARCHAR(100),"
                    + "  `tipo_detectado` VARCHAR(50),"
                    + // magic bytes: PDF, JPG, DESCONOCIDO…
                    "  `tamano_bytes`   BIGINT,"
                    + "  `esta_en_disco`  TINYINT(1)    DEFAULT 0,"
                    + // 0=db4o, 1=fichero .enc en disco
                    "  `ruta_disco`     VARCHAR(1000),"
                    + // solo si esta_en_disco=1
                    "  `tabla_origen`   VARCHAR(100),"
                    + // tabla del cliente que lo referencia
                    "  `fecha_subida`   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            System.out.println("[API] Estructura de metadatos sincronizada.");

            // Auto-registrar tablas del cliente que falten
            autoRegistrarTablas();

        } catch (SQLException e) {
            System.err.println("[API] Error creando estructura ERP: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Escanea la base de datos del cliente (DB_CLIENTE) y registra
     * automaticamente en erp_meta_tablas (+ erp_meta_columnas) cualquier tabla
     * que aun no tenga metadatos.
     */
    private static void autoRegistrarTablas() {
        String dbCliente = AppConfig.DB_CLIENTE;
        System.out.println("[API] Auto-registrando tablas de '" + dbCliente + "'...");

        try (Connection conn = getConexion(dbCliente)) {
            // 1. Obtener todas las tablas del cliente
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

            // 2. Por cada tabla, comprobar si ya esta registrada
            try (Connection connSistema = getConexion(AppConfig.DB_SISTEMA)) {
                for (String tabla : tablasCliente) {
                    if (yaRegistrada(connSistema, tabla)) {
                        continue;
                    }

                    // 3. Registrar la tabla
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

                    // 4. Registrar las columnas usando DESCRIBE
                    try (Statement stmtDesc = conn.createStatement(); ResultSet rsDesc = stmtDesc.executeQuery("DESCRIBE `" + tabla + "`")) {
                        while (rsDesc.next()) {
                            String colNombre = rsDesc.getString("Field");
                            String colTipo = rsDesc.getString("Type");
                            String colNull = rsDesc.getString("Null");
                            String colKey = rsDesc.getString("Key");
                            String colExtra = rsDesc.getString("Extra");

                            boolean nullable = "YES".equalsIgnoreCase(colNull);
                            boolean autoInc = colExtra != null && colExtra.contains("auto_increment");
                            boolean esPK = "PRI".equalsIgnoreCase(colKey);
                            boolean esUnico = "UNI".equalsIgnoreCase(colKey) || esPK;

                            String tipoGenerico = mapearTipoMysql(colTipo);

                            try (PreparedStatement pstmtCol = connSistema.prepareStatement(
                                    "INSERT INTO `erp_meta_columnas` "
                                    + "(tabla_id, nombre, tipo, nullable, es_contrasena, es_visible, es_sensible, es_archivo, autoincremental, unico, valor_defecto) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)")) {
                                pstmtCol.setLong(1, tablaId);
                                pstmtCol.setString(2, colNombre);
                                pstmtCol.setString(3, tipoGenerico);
                                pstmtCol.setBoolean(4, nullable);
                                pstmtCol.setBoolean(5, colNombre.toLowerCase().contains("contrasena")
                                        || colNombre.toLowerCase().contains("password"));
                                pstmtCol.setBoolean(6, true);  // visible
                                pstmtCol.setBoolean(7, false); // no sensible
                                pstmtCol.setBoolean(8, false); // no archivo
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

    /**
     * Mapea un tipo de dato MySQL al tipo generico de la API (TipoDatoMapper).
     */
    private static String mapearTipoMysql(String tipoMysql) {
        if (tipoMysql == null) {
            return "TEXTO_CORTO";
        }
        String tipoUpper = tipoMysql.toUpperCase();

        if (tipoUpper.equals("TINYINT(1)")) {
            return "BINARIO";
        }
        if (tipoUpper.startsWith("INT") || tipoUpper.startsWith("BIGINT")
                || tipoUpper.startsWith("SMALLINT") || tipoUpper.startsWith("TINYINT")
                || tipoUpper.startsWith("MEDIUMINT")) {
            return "ENTERO";
        }
        if (tipoUpper.startsWith("DECIMAL") || tipoUpper.startsWith("DOUBLE")
                || tipoUpper.startsWith("FLOAT") || tipoUpper.startsWith("NUMERIC")) {
            return "DECIMAL";
        }
        if (tipoUpper.startsWith("DATE") && !tipoUpper.startsWith("DATETIME")) {
            return "FECHA";
        }
        if (tipoUpper.startsWith("DATETIME") || tipoUpper.startsWith("TIMESTAMP")) {
            return "FECHA_HORA";
        }
        if (tipoUpper.startsWith("TEXT") || tipoUpper.startsWith("LONGTEXT")
                || tipoUpper.startsWith("MEDIUMTEXT")) {
            return "TEXTO_LARGO";
        }
        if (tipoUpper.startsWith("BLOB") || tipoUpper.startsWith("LONGBLOB")) {
            return "ARCHIVO";
        }
        if (tipoUpper.startsWith("CHAR") && tipoUpper.contains("60")) {
            return "CONTRASENA";
        }
        return "TEXTO_CORTO";
    }
}
