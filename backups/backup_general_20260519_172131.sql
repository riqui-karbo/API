-- Backup general generado el 20260519_172131
-- Bases: erp_sistema, erp_empresa

-- ========== BD: erp_sistema ==========
CREATE DATABASE IF NOT EXISTS `erp_sistema` CHARACTER SET utf8mb4;
USE `erp_sistema`;

DROP TABLE IF EXISTS `erp_config`;
CREATE TABLE `erp_config` (
  `clave` varchar(100) NOT NULL,
  `valor` text DEFAULT NULL,
  `actualizado` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`clave`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


DROP TABLE IF EXISTS `erp_ficheros`;
CREATE TABLE `erp_ficheros` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `uuid` varchar(36) NOT NULL,
  `nombre_original` varchar(500) DEFAULT NULL,
  `mime_type` varchar(100) DEFAULT NULL,
  `tipo_detectado` varchar(50) DEFAULT NULL,
  `tamano_bytes` bigint(20) DEFAULT NULL,
  `esta_en_disco` tinyint(1) DEFAULT 0,
  `ruta_disco` varchar(1000) DEFAULT NULL,
  `tabla_origen` varchar(100) DEFAULT NULL,
  `fecha_subida` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uuid` (`uuid`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `erp_ficheros` VALUES ('2', '827d13f0-2ee8-44c4-8945-f562b4b9ab11', 'Ingles.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', NULL, '16848', '0', NULL, 'documentos', '2026-05-18 18:51:47');
INSERT INTO `erp_ficheros` VALUES ('3', '8c5d3a21-f620-452b-9180-2af57e3298d7', 'OFERTAS FICTICIAS.pdf', 'application/pdf', NULL, '4002140', '0', NULL, 'documentos', '2026-05-19 17:20:56');

DROP TABLE IF EXISTS `erp_meta_columnas`;
CREATE TABLE `erp_meta_columnas` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `tabla_id` int(11) NOT NULL,
  `nombre` varchar(100) NOT NULL,
  `tipo` varchar(50) NOT NULL,
  `nullable` tinyint(1) DEFAULT 1,
  `es_contrasena` tinyint(1) DEFAULT 0,
  `es_visible` tinyint(1) DEFAULT 1,
  `es_sensible` tinyint(1) DEFAULT 0,
  `es_archivo` tinyint(1) DEFAULT 0,
  `autoincremental` tinyint(1) DEFAULT 0,
  `unico` tinyint(1) DEFAULT 0,
  `valor_defecto` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `tabla_id` (`tabla_id`),
  CONSTRAINT `erp_meta_columnas_ibfk_1` FOREIGN KEY (`tabla_id`) REFERENCES `erp_meta_tablas` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `erp_meta_columnas` VALUES ('8', '4', 'id', 'ENTERO', '0', '0', '1', '0', '0', '1', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('9', '4', 'descripcion', 'TEXTO_CORTO', '1', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('10', '4', 'password_test', 'CONTRASENA', '0', '1', '0', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('11', '5', 'id', 'ENTERO', '0', '0', '1', '0', '0', '1', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('12', '5', 'nombre', 'TEXTO_CORTO', '0', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('13', '6', 'id', 'entero', '0', '0', '1', '0', '0', '1', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('14', '6', 'hola', 'cadena', '1', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('15', '6', 'hihitler', 'DECIMAL(10,2)', '1', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('16', '7', 'id', 'ENTERO', '0', '0', '1', '0', '0', '1', '1', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('17', '7', 'hola', 'TEXTO_CORTO', '1', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('18', '7', 'hihitler', 'TEXTO_CORTO', '1', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('22', '9', 'id', 'entero', '0', '0', '1', '0', '0', '1', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('23', '9', 'hola', 'cadena', '1', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('24', '9', 'hitler', 'TEXTO_CORTO', '1', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('25', '9', 'franco', 'TEXTO_CORTO', '1', '0', '1', '1', '0', '0', '0', NULL);

DROP TABLE IF EXISTS `erp_meta_relaciones`;
CREATE TABLE `erp_meta_relaciones` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `nombre` varchar(100) NOT NULL,
  `tabla_origen` int(11) NOT NULL,
  `fk_columna` varchar(100) NOT NULL,
  `tabla_destino` varchar(100) NOT NULL,
  `cardinalidad` varchar(10) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `tabla_origen` (`tabla_origen`),
  CONSTRAINT `erp_meta_relaciones_ibfk_1` FOREIGN KEY (`tabla_origen`) REFERENCES `erp_meta_tablas` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


DROP TABLE IF EXISTS `erp_meta_tablas`;
CREATE TABLE `erp_meta_tablas` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `modulo_id` int(11) DEFAULT NULL,
  `nombre_logico` varchar(100) NOT NULL,
  `nombre_amigable` varchar(200) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `nombre_logico` (`nombre_logico`),
  KEY `modulo_id` (`modulo_id`),
  CONSTRAINT `erp_meta_tablas_ibfk_1` FOREIGN KEY (`modulo_id`) REFERENCES `erp_modulos` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `erp_meta_tablas` VALUES ('4', '1', 'productos_test', 'Tabla de Productos Test');
INSERT INTO `erp_meta_tablas` VALUES ('5', '1', 'marcas_test', 'Marcas de Productos');
INSERT INTO `erp_meta_tablas` VALUES ('6', '1', 'prueba123456', 'prueba123456');
INSERT INTO `erp_meta_tablas` VALUES ('7', NULL, 'prueba1234', 'Prueba1234');
INSERT INTO `erp_meta_tablas` VALUES ('9', '1', 'prueba123', 'prueba123');

DROP TABLE IF EXISTS `erp_modulos`;
CREATE TABLE `erp_modulos` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `nombre` varchar(100) NOT NULL,
  `icono` varchar(50) DEFAULT '?',
  `icon_type` varchar(20) DEFAULT 'emote',
  `habilitado` tinyint(1) DEFAULT 1,
  `orden` int(11) DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `erp_modulos` VALUES ('1', 'hola', '📦', 'emote', '1', '1');
INSERT INTO `erp_modulos` VALUES ('2', 'hola2', '📦', 'emote', '1', '1');

DROP TABLE IF EXISTS `erp_roles`;
CREATE TABLE `erp_roles` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `nombre` varchar(50) NOT NULL,
  `descripcion` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `nombre` (`nombre`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `erp_roles` VALUES ('1', 'admin', 'Administrador con acceso total');
INSERT INTO `erp_roles` VALUES ('2', 'empleado', 'Empleado con acceso basico');

DROP TABLE IF EXISTS `erp_roles_permisos`;
CREATE TABLE `erp_roles_permisos` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `rol` varchar(50) NOT NULL,
  `tabla` varchar(100) NOT NULL,
  `puede_ver` tinyint(1) DEFAULT 0,
  `puede_crear` tinyint(1) DEFAULT 0,
  `puede_editar` tinyint(1) DEFAULT 0,
  `puede_eliminar` tinyint(1) DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rol_tabla` (`rol`,`tabla`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


DROP TABLE IF EXISTS `erp_users`;
CREATE TABLE `erp_users` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `email` varchar(255) NOT NULL,
  `contrasena` char(60) NOT NULL,
  `rol` varchar(50) NOT NULL,
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `erp_users` VALUES ('1', 'admin@erp.com', '$2a$10$7QJ8z5v6Xk3mN9pL2wR4uOYvKjH1sDfGtCnE8aBqMxIoWlPeZr0yi', 'admin', '1');

-- ========== BD: erp_empresa ==========
CREATE DATABASE IF NOT EXISTS `erp_empresa` CHARACTER SET utf8mb4;
USE `erp_empresa`;

DROP TABLE IF EXISTS `marcas_test`;
CREATE TABLE `marcas_test` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `nombre` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


DROP TABLE IF EXISTS `productos_test`;
CREATE TABLE `productos_test` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `descripcion` varchar(255) DEFAULT NULL,
  `password_test` char(60) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


DROP TABLE IF EXISTS `prueba123`;
CREATE TABLE `prueba123` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `hola` varchar(255) DEFAULT NULL,
  `hitler` varchar(255) DEFAULT NULL,
  `franco` varchar(1) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `prueba123` VALUES ('1', '[value-2]', NULL, NULL);
INSERT INTO `prueba123` VALUES ('2', 'puto', 'el que', NULL);

DROP TABLE IF EXISTS `prueba1234`;
CREATE TABLE `prueba1234` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `hola` varchar(255) DEFAULT NULL,
  `hihitler` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


