-- Backup general generado el 20260521_180508
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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `erp_ficheros` VALUES ('1', 'f081f55d-6a55-42f9-86f4-9271ca0f13d7', '2025-11-07 01-17-13.mp4', 'video/mp4', NULL, '59868', '0', NULL, 'documentos', '2026-05-21 17:53:03');

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
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `erp_meta_columnas` VALUES ('1', '1', 'id', 'ENTERO', '0', '0', '1', '0', '0', '1', '1', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('2', '1', 'correo_electronico', 'TEXTO_CORTO', '0', '0', '1', '0', '0', '0', '1', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('3', '1', 'nombre', 'TEXTO_CORTO', '0', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('4', '1', 'primer_apellido', 'TEXTO_CORTO', '0', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('5', '1', 'segundo_apellido', 'TEXTO_CORTO', '1', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('6', '1', 'dni_nie', 'TEXTO_CORTO', '0', '0', '1', '0', '0', '0', '1', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('7', '1', 'telefono', 'TEXTO_CORTO', '1', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('8', '1', 'direccion', 'TEXTO_CORTO', '1', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('9', '1', 'iban', 'TEXTO_CORTO', '1', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('10', '1', 'nss', 'TEXTO_CORTO', '1', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('11', '1', 'cargo', 'TEXTO_CORTO', '1', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('12', '1', 'foto_url', 'TEXTO_CORTO', '1', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('13', '1', 'user_id', 'ENTERO', '1', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('14', '2', 'id', 'ENTERO', '0', '0', '1', '0', '0', '1', '1', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('15', '2', 'nombre', 'TEXTO_CORTO', '0', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('16', '2', 'descripcion', 'TEXTO_LARGO', '1', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('17', '2', 'referencia', 'TEXTO_CORTO', '1', '0', '1', '0', '0', '0', '1', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('18', '2', 'precio', 'DECIMAL', '0', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('19', '2', 'esta_agotado', 'BINARIO', '1', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('20', '3', 'id', 'ENTERO', '0', '0', '1', '0', '0', '1', '1', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('21', '3', 'nombre', 'TEXTO_CORTO', '0', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('22', '3', 'apellido', 'TEXTO_CORTO', '1', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('23', '3', 'cif_nif', 'TEXTO_CORTO', '0', '0', '1', '0', '0', '0', '1', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('24', '3', 'telefono', 'TEXTO_CORTO', '1', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('25', '3', 'email', 'TEXTO_CORTO', '1', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('26', '3', 'direccion', 'TEXTO_LARGO', '1', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('27', '3', 'creado_en', 'FECHA_HORA', '0', '0', '1', '0', '0', '0', '0', NULL);
INSERT INTO `erp_meta_columnas` VALUES ('28', '3', 'user_id', 'ENTERO', '1', '0', '1', '0', '0', '0', '0', NULL);

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
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `erp_meta_tablas` VALUES ('1', '1', 'empleados', 'Gestión de Empleados');
INSERT INTO `erp_meta_tablas` VALUES ('2', '1', 'productos', 'Catálogo de Productos');
INSERT INTO `erp_meta_tablas` VALUES ('3', '1', 'clientes', 'Cartera de Clientes');

DROP TABLE IF EXISTS `erp_modulos`;
CREATE TABLE `erp_modulos` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `nombre` varchar(100) NOT NULL,
  `icono` varchar(50) DEFAULT '?',
  `icon_type` varchar(20) DEFAULT 'emote',
  `habilitado` tinyint(1) DEFAULT 1,
  `orden` int(11) DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `erp_modulos` VALUES ('1', 'Gestión Central', '⚙️', 'emote', '1', '1');

DROP TABLE IF EXISTS `erp_roles`;
CREATE TABLE `erp_roles` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `nombre` varchar(50) NOT NULL,
  `descripcion` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `nombre` (`nombre`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `erp_roles` VALUES ('1', 'admin', 'Administrador con acceso total');
INSERT INTO `erp_roles` VALUES ('2', 'empleado', 'Empleado con acceso basico');
INSERT INTO `erp_roles` VALUES ('3', 'cliente', 'Cliente con acceso a la tienda');

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
  `rol_id` int(11) DEFAULT NULL,
  `tipo` enum('empleado','cliente') NOT NULL DEFAULT 'empleado',
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`email`),
  KEY `fk_users_rol` (`rol_id`),
  CONSTRAINT `erp_users_ibfk_1` FOREIGN KEY (`rol_id`) REFERENCES `erp_roles` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_users_rol` FOREIGN KEY (`rol_id`) REFERENCES `erp_roles` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `erp_users` VALUES ('1', 'admin@empresa.com', '$2a$10$V7bGSGEjnHFEHD7LzMafP.7074ltDsOn9GEhBc0dAm3aEv59R/tq.', '1', 'empleado', '1');

-- ========== BD: erp_empresa ==========
CREATE DATABASE IF NOT EXISTS `erp_empresa` CHARACTER SET utf8mb4;
USE `erp_empresa`;

DROP TABLE IF EXISTS `clientes`;
CREATE TABLE `clientes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `nombre` varchar(100) NOT NULL,
  `apellido` varchar(100) DEFAULT NULL,
  `cif_nif` varchar(20) NOT NULL,
  `telefono` varchar(20) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `direccion` text DEFAULT NULL,
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  `user_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `cif_nif` (`cif_nif`),
  KEY `fk_clientes_user` (`user_id`),
  CONSTRAINT `fk_clientes_user` FOREIGN KEY (`user_id`) REFERENCES `erp_sistema`.`erp_users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


DROP TABLE IF EXISTS `empleados`;
CREATE TABLE `empleados` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `correo_electronico` varchar(255) NOT NULL,
  `nombre` varchar(100) NOT NULL,
  `primer_apellido` varchar(100) NOT NULL,
  `segundo_apellido` varchar(100) DEFAULT NULL,
  `dni_nie` varchar(20) NOT NULL,
  `telefono` varchar(20) DEFAULT NULL,
  `direccion` varchar(255) DEFAULT NULL,
  `iban` varchar(34) DEFAULT NULL,
  `nss` varchar(20) DEFAULT NULL,
  `cargo` varchar(100) DEFAULT 'Personal',
  `foto_url` varchar(255) DEFAULT NULL,
  `user_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `correo_electronico` (`correo_electronico`),
  UNIQUE KEY `dni_nie` (`dni_nie`),
  KEY `fk_empleados_user` (`user_id`),
  CONSTRAINT `fk_empleados_user` FOREIGN KEY (`user_id`) REFERENCES `erp_sistema`.`erp_users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


DROP TABLE IF EXISTS `productos`;
CREATE TABLE `productos` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `nombre` varchar(150) NOT NULL,
  `descripcion` text DEFAULT NULL,
  `referencia` varchar(150) DEFAULT NULL,
  `precio` decimal(10,2) NOT NULL,
  `esta_agotado` tinyint(1) DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `referencia` (`referencia`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


