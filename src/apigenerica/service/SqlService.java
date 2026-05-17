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
     *
     * @param tabla Metadatos de la tabla
     * @param relaciones Datos de relaciones de la tabla
     * @return SQL listo para ejecutar
     */
    public String generarCreateSql(TablaConfig tabla, List<RelacionConfig> relaciones) {
        try {
            String nombreTabla = tabla.getNombreLogico();
            List<ColumnaConfig> campos = tabla.getColumnas();
            validador.validarNombre(nombreTabla);
            validador.validarColumnasUnicas(campos);

            StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS `" + nombreTabla + "` (");
            sql.append("`id` BIGINT AUTO_INCREMENT PRIMARY KEY, ");

            // Crear columnas
            if (campos != null) {
                for (int i = 0; i < campos.size(); i++) {
                    String sqlCol = construirDefinicionColumna(campos.get(i));
                    if (!sqlCol.isEmpty()) {
                        sql.append(sqlCol);
                        sql.append(", "); // Separar de la siguiente columna
                    }
                }
            }

            // Añadir Foreign Keys
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
            // Quitar la última coma y espacio de las columnas
            if (sql.toString().endsWith(", ")) {
                sql.setLength(sql.length() - 2);
            }
            sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");
            return sql.toString();
        } catch (Exception e) {
            throw new ValidacionException("Error al generar SQL para '" + tabla.getNombreLogico() + "': " + e.getMessage());
        }
    }

    /**
     * Construye una sentencia CREATE DATABASE
     *
     * @param nombreDb Nombre de la base de datos
     * @return SQL listo para ejecutar
     */
    public String generarCreateDbSql(String nombreDb) {
        return "CREATE DATABASE IF NOT EXISTS `" + nombreDb
                + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;";
    }

    /**
     * Construye una sentencia ALTER TABLE ADD COLUMN
     *
     * @param tabla Nombre de la tabla que se modificará
     * @param col Metadatos de la columna a añadir
     * @return SQL listo para ejecutar
     */
    public String generarAddColumnSql(String tabla, ColumnaConfig col) {
        validador.validarNombre(tabla);
        validador.validarNombre(col.getNombre());
        
        String sqlCol = construirDefinicionColumna(col);
        if (sqlCol.isEmpty()) {
            throw new IllegalArgumentException("Definición de columna no válida.");
        }
        return String.format("ALTER TABLE `%s` ADD COLUMN `%s`", tabla, sqlCol);
    }

    /**
     * Construye una sentencia ALTER TABLE DROP COLUMN
     *
     * @param tabla Nombre de la tabla que se modificará
     * @param nombreColumna Nombre de la columna que se eliminará
     * @return SQL listo para ejecutar
     */
    public String generarDropColumnSql(String tabla, String nombreColumna) {
        validador.validarNombre(tabla);
        validador.validarNombre(nombreColumna);
        return String.format("ALTER TABLE `%s` DROP COLUMN `%s`", tabla, nombreColumna);
    }

    /**
     * Construye una sentencia ALTER TABLE MODIFY COLUMN
     *
     * @param tabla Nombre de la tabla que se modificará
     * @param col Nombre de la columna que se modificará
     * @return SQL listo para ejecutar
     */
    public String generarModifyColumnSql(String tabla, ColumnaConfig col) {
        validador.validarNombre(tabla);
        validador.validarNombre(col.getNombre());
        
        String definicion = construirDefinicionColumna(col);
        if (definicion.isEmpty()) {
            throw new IllegalArgumentException("Definición de columna no válida.");
        }
        return String.format("ALTER TABLE `%s` MODIFY COLUMN `%s`", tabla, definicion);
    }
 
    /**
     * Construye una sentencia ALTER TABLE RENAME COLUMN
     * 
     * @param tabla Nombre de la tabla que se modificará
     * @param nombreColumna Nombre actual de la columna
     * @param nuevoNombre Nombre nuevo de la columna
     * @return 
     */
    public String generarRenameColumnSql(String tabla, String nombreColumna, String nuevoNombre) {
        validador.validarNombre(tabla);
        validador.validarNombre(nombreColumna);
        validador.validarNombre(nuevoNombre);
        
        return String.format("ALTER TABLE `%s` RENAME COLUMN `%s` TO `%s`", tabla, nombreColumna, nuevoNombre);
    }

    /**
     * Construye una sentencia DROP table
     *
     * @param nombreTabla Nombre de la tabla a borrar
     * @return SQL listo para ejecutar
     */
    public String generarDropSql(String nombreTabla) {
        validador.validarNombre(nombreTabla);
        return "DROP TABLE IF EXISTS `" + nombreTabla + "`;";
    }

    /**
     * Ejecutar un script SQL
     *
     * @param db Base de datos en la que se ejecutará la sentencia
     * @param sql Sentencia a ejecutar
     * @throws SQLException
     */
    public void ejecutarSql(String db, String sql) throws SQLException {
        try (Connection conn = ConexionMysql.getConexion(db); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.err.println("Error ejecutando '" + sql + "'");
            throw e;
        }
    }

    /**
     * Construye la definición SQL completa de una columna (tipo, NOT NULL,
     * DEFAULT, etc.)
     *
     * @param columna Metadatos de la columna
     * @return String SQL construido
     */
    private String construirDefinicionColumna(ColumnaConfig columna) {
        StringBuilder sql = new StringBuilder();

        // Si el usuario envió una columna llamada id, se ignora
        if (columna.getNombre().equalsIgnoreCase("id")) {
            return "";
        }

        // Nombre y tipo
        sql.append("`").append(columna.getNombre()).append("` ").append(TipoDatoMapper.toSql(columna.getTipo()));

        // NOT NULL
        if (!columna.isNullable()) {
            sql.append(" NOT NULL");
        }

        // DEFAULT
        if (columna.getValorDefecto() != null && !columna.isAutoincremental()) {
            sql.append(" DEFAULT '").append(columna.getValorDefecto()).append("'");
        }

        // AUTO_INCREMENT
        if (columna.isAutoincremental() && TipoDatoMapper.toSql(columna.getTipo()).contains("INT")) {
            sql.append(" AUTO_INCREMENT");
        }

        // UNIQUE
        if (columna.isUnico()) {
            sql.append(" UNIQUE");
        }

        return sql.toString();
    }
}
