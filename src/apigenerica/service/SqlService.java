/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.service;

import apigenerica.model.ColumnaConfig;
import apigenerica.TipoDatoMapper;
import apigenerica.config.ConexionMysql;
import apigenerica.excepciones.ValidacionException;
import apigenerica.model.RelacionConfig;
import apigenerica.model.TablaConfig;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * @author Grupo1 
 * Operaciones SQL genéricas (no CRUD)
 */
public class SqlService {

    private final ValidadorService validador;

    public SqlService(ValidadorService validador) {
        this.validador = validador;
    }

    /**
     * Construye una sentencia CREATE TABLE
     */
    public String generarCreateSql(TablaConfig tabla, List<RelacionConfig> relaciones) {
        try {
            String nombreTabla = tabla.getNombreLogico();
            List<ColumnaConfig> campos = tabla.getColumnas();
            validador.validarNombre(nombreTabla);
            validador.validarColumnasUnicas(campos);

            StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS `" + nombreTabla + "` (");
            sql.append("`id` BIGINT AUTO_INCREMENT PRIMARY KEY, ");

            if (campos != null) {
                for (int i = 0; i < campos.size(); i++) {
                    String sqlCol = construirDefinicionColumna(campos.get(i));
                    if (!sqlCol.isEmpty()) {
                        sql.append(sqlCol);
                        sql.append(", ");
                    }
                }
            }

            if (relaciones != null && !relaciones.isEmpty()) {
                for (int i = 0; i < relaciones.size(); i++) {
                    RelacionConfig rel = relaciones.get(i);
                    sql.append("CONSTRAINT `fk_").append(nombreTabla).append("_").append(rel.getTablaDestino()).append("` ")
                            .append("FOREIGN KEY (`").append(rel.getFkColumna()).append("`) ")
                            .append("REFERENCES `").append(rel.getTablaDestino()).append("`(`id`) ")
                            .append("ON DELETE CASCADE ON UPDATE CASCADE");
                    if (i < relaciones.size() - 1) {
                        sql.append(", ");
                    }
                }
            }
            if (sql.toString().endsWith(", ")) {
                sql.setLength(sql.length() - 2);
            }
            sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");
            return sql.toString();
        } catch (Exception e) {
            throw new ValidacionException("Error al generar SQL para '" + tabla.getNombreLogico() + "': " + e.getMessage());
        }
    }

    public String generarCreateDbSql(String nombreDb) {
        return "CREATE DATABASE IF NOT EXISTS `" + nombreDb
                + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;";
    }

    public String generarAddColumnSql(String tabla, ColumnaConfig col) {
        validador.validarNombre(tabla);
        validador.validarNombre(col.getNombre());
        String sqlCol = construirDefinicionColumna(col);
        if (sqlCol.isEmpty()) {
            throw new IllegalArgumentException("Definicion de columna no valida.");
        }
        return String.format("ALTER TABLE `%s` ADD COLUMN %s", tabla, sqlCol);
    }

    public String generarDropColumnSql(String tabla, String nombreColumna) {
        validador.validarNombre(tabla);
        validador.validarNombre(nombreColumna);
        return String.format("ALTER TABLE `%s` DROP COLUMN `%s`", tabla, nombreColumna);
    }

    public String generarModifyColumnSql(String tabla, ColumnaConfig col) {
        validador.validarNombre(tabla);
        validador.validarNombre(col.getNombre());
        String definicion = construirDefinicionColumna(col);
        if (definicion.isEmpty()) {
            throw new IllegalArgumentException("Definicion de columna no valida.");
        }
        return String.format("ALTER TABLE `%s` MODIFY COLUMN %s", tabla, definicion);
    }

    public String generarRenameColumnSql(String tabla, String nombreColumna, String nuevoNombre) {
        validador.validarNombre(tabla);
        validador.validarNombre(nombreColumna);
        validador.validarNombre(nuevoNombre);
        return String.format("ALTER TABLE `%s` RENAME COLUMN `%s` TO `%s`", tabla, nombreColumna, nuevoNombre);
    }

    public String generarDropSql(String nombreTabla) {
        validador.validarNombre(nombreTabla);
        return "DROP TABLE IF EXISTS `" + nombreTabla + "`;";
    }

    public void ejecutarSql(String db, String sql) throws SQLException {
        try (Connection conn = ConexionMysql.getConexion(db); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.err.println("Error ejecutando '" + sql + "'");
            throw e;
        }
    }

    /**
     * Construye la definicion SQL de una columna.
     *
     * COLUMNAS SENSIBLES: en MySQL solo se crea un placeholder VARCHAR(1) NULL DEFAULT NULL.
     * El valor real se cifra y almacena exclusivamente en Paradox (base_paradox.db).
     * MySQL nunca vera ni guardara el dato sensible real.
     */
    private String construirDefinicionColumna(ColumnaConfig columna) {
        StringBuilder sql = new StringBuilder();

        if (columna.getNombre().equalsIgnoreCase("id")) {
            return "";
        }

        // Columna sensible -> solo placeholder en MySQL, dato real va a Paradox
        if (columna.isSensible()) {
            sql.append("`").append(columna.getNombre()).append("` VARCHAR(1) NULL DEFAULT NULL");
            return sql.toString();
        }

        sql.append("`").append(columna.getNombre()).append("` ").append(TipoDatoMapper.toSql(columna.getTipo()));

        if (!columna.isNullable()) {
            sql.append(" NOT NULL");
        }

        if (columna.getValorDefecto() != null && !columna.isAutoincremental()) {
            sql.append(" DEFAULT '").append(columna.getValorDefecto()).append("'");
        }

        if (columna.isAutoincremental() && TipoDatoMapper.toSql(columna.getTipo()).contains("INT")) {
            sql.append(" AUTO_INCREMENT");
        }

        if (columna.isUnico()) {
            sql.append(" UNIQUE");
        }

        return sql.toString();
    }
}