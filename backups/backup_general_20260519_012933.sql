-- Backup general generado el 20260519_012933
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
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `erp_ficheros` VALUES ('10', '95700b47-6e7b-468a-b632-6eee0ca629d0', '1DAM-PROG-U01-Introducción a los lenguajes de programación.pdf', 'application/pdf', NULL, '2367005', '0', NULL, 'documentos', '2026-05-17 17:53:14');
INSERT INTO `erp_ficheros` VALUES ('11', '669507b1-2783-4182-af31-a510e94fe74f', '2025-12-05 00-04-25.mp4', 'video/mp4', NULL, '23206097', '1', 'storage\ficheros_seguros\documentos\669507b1-2783-4182-af31-a510e94fe74f_2025-12-05 00-04-25.mp4.enc', 'documentos', '2026-05-18 20:04:54');
INSERT INTO `erp_ficheros` VALUES ('12', 'f8f50c43-7a37-41dc-bccb-2a63a5b41f3a', '2025-12-04 23-59-32.mp4', 'video/mp4', NULL, '21249059', '1', 'storage\ficheros_seguros\documentos\f8f50c43-7a37-41dc-bccb-2a63a5b41f3a_2025-12-04 23-59-32.mp4.enc', 'documentos', '2026-05-19 01:18:03');

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


DROP TABLE IF EXISTS `erp_modulos`;
CREATE TABLE `erp_modulos` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `nombre` varchar(100) NOT NULL,
  `icono` varchar(50) DEFAULT '?',
  `icon_type` varchar(20) DEFAULT 'emote',
  `habilitado` tinyint(1) DEFAULT 1,
  `orden` int(11) DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


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
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO `erp_users` VALUES ('2', 'admin@erp.com', '$2a$10$7QJ8z5v6Xk3mN9pL2wR4uOYvKjH1sDfGtCnE8aBqMxIoWlPeZr0yi', 'admin', '1');

-- ========== BD: erp_empresa ==========
CREATE DATABASE IF NOT EXISTS `erp_empresa` CHARACTER SET utf8mb4;
USE `erp_empresa`;

