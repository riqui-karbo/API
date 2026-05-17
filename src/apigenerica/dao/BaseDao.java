/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.dao;

import apigenerica.EntidadMapper;
import apigenerica.model.ColumnaConfig;
import apigenerica.model.EntidadDinamica;
import apigenerica.model.RelacionConfig;
import java.sql.*;
import java.util.*;

/**
 * @author Grupo1 
 * Consultas CRUD con las tablas
 */
public class BaseDao {

    private final EntidadMapper mapper = new EntidadMapper();

    /**
     * Construir y ejecutar una consulta INSERT en una tabla MySQL
     *
     * @param conn Conexión a MySQL
     * @param nombreTabla Nombre de la tabla en la que se insertará el registro
     * @param entidad Registro a insertar
     * @return ID del registro insertado
     * @throws SQLException
     */
    public long insertar(Connection conn, String nombreTabla, EntidadDinamica entidad) throws SQLException {
        Map<String, Object> datos = entidad.getTodo();

        if (datos == null || datos.isEmpty()) {
            throw new IllegalArgumentException("No hay datos para insertar");
        }

        List<String> columnas = new ArrayList<>(datos.keySet());

        // Construir sentencia de INSERT
        StringBuilder sql = new StringBuilder("INSERT INTO `").append(nombreTabla).append("` (");
        StringBuilder placeholders = new StringBuilder("VALUES ("); // String con placeholders (?) de los valores

        List<Object> valores = new ArrayList<>();

        for (int i = 0; i < columnas.size(); i++) {
            String col = columnas.get(i);
            sql.append("`").append(col).append("`"); // `Nombre de la columna`
            placeholders.append("?");
            valores.add(datos.get(col));

            // Separar columnasHijas y ? por comas
            if (i < columnas.size() - 1) {
                sql.append(", ");
                placeholders.append(", ");
            }
        }
        // Agregar placeholders al final de la sentencia
        sql.append(") ").append(placeholders).append(")");

        // RETURN_GENERATED_KEYS para obtener el id generado
        try (PreparedStatement stmt = conn.prepareStatement(sql.toString(), Statement.RETURN_GENERATED_KEYS)) {
            // Preparar consulta enlazando los valores
            for (int j = 0; j < valores.size(); j++) {
                stmt.setObject(j + 1, valores.get(j));
            }
            stmt.executeUpdate();

            // Recuperar ID autoincremental
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return 0; // No tiene ID autoincremental
    }

    /**
     * Construir y ejecutar una sentencia UPDATE en una tabla MySQL
     *
     * @param conn Conexión con MySQL
     * @param nombreTabla Nombre de la tabla en la que se realizará la operación
     * @param entidad Registro a insertar
     * @param valorPk Valor de la clave primaria
     * @return Número de filas afectadas
     * @throws SQLException
     */
    public int actualizar(Connection conn, String nombreTabla, EntidadDinamica entidad, Object valorPk) throws SQLException {
        Map<String, Object> datos = entidad.getTodo();
        if (datos.isEmpty()) {
            return 0;
        }

        // Detectar columna PK real
        String pkCol = detectarPK(conn, nombreTabla);

        StringBuilder sql = new StringBuilder("UPDATE `").append(nombreTabla).append("` SET ");
        List<Object> valores = new ArrayList<>();

        // Solo iterar sobre los datos que vienen en el objeto
        List<String> columnasPresentes = new ArrayList<>(datos.keySet());
        boolean primero = true;

        for (int i = 0; i < columnasPresentes.size(); i++) {
            String col = columnasPresentes.get(i);
            // Ignorar la PK si viene en el mapa de datos, ya que va en el WHERE
            if (col.equalsIgnoreCase(pkCol)) {
                continue;
            }

            if (!primero) sql.append(", ");
            sql.append("`").append(col).append("` = ?");
            valores.add(datos.get(col));
            primero = false;
        }

        if (valores.isEmpty()) return 0;

        sql.append(" WHERE `").append(pkCol).append("` = ?");
        valores.add(valorPk);

        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int j = 0; j < valores.size(); j++) {
                stmt.setObject(j + 1, valores.get(j));
            }
            return stmt.executeUpdate();
        }
    }

    /**
     * Construir y ejecutar una sentencia DELETE en una tabla MySQL
     *
     * @param conn Conexión con MySQL
     * @param nombreTabla Nombre de la tabla en la que se realizará la operación
     * @param valorPk Valor de la clave primaria
     * @return Número de filas afectadas
     * @throws SQLException
     */
    public int eliminar(Connection conn, String nombreTabla, Object valorPk) throws SQLException {
        String pkCol = detectarPK(conn, nombreTabla);
        String sql = "DELETE FROM `" + nombreTabla + "` WHERE `" + pkCol + "` = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, valorPk);
            return stmt.executeUpdate();
        }
    }

    /**
     * Insertar en varias tablas en una sola transacción
     *
     * @param conn Conexión con MySQL
     * @param ordenTablas Lista con el orden de inserción (padres primero, hijos
     * después)
     * @param datosPorTabla Mapa con los datos a insertar por cada tabla
     * @param relaciones Metadatos de las relaciones
     * @return Mapa con los IDs generados
     * @throws SQLException
     */
    public Map<String, Long> insertarTransaccional(Connection conn,
            List<String> ordenTablas, Map<String, EntidadDinamica> datosPorTabla,
            List<RelacionConfig> relaciones) throws SQLException {
        // ID autoincremental generado y tabla a la que pertenece
        Map<String, Long> idsGenerados = new LinkedHashMap<>();

        for (String tabla : ordenTablas) {
            EntidadDinamica datos = datosPorTabla.get(tabla);
            if (datos == null) {
                continue;
            }

            // Si la tabla tiene foreign key
            if (relaciones != null) {
                for (RelacionConfig rel : relaciones) {
                    // Si la tabla a insertar es el hijo (destino)
                    if (rel.getTablaDestino().equalsIgnoreCase(tabla)) {
                        String tablaPadre = rel.getTablaOrigen();
                        String columnaFk = rel.getFkColumna();

                        // Buscar si el padre ya fue insertado
                        Long idGeneradoPadre = idsGenerados.get(tablaPadre);

                        // Si el padre fue insertado, inyectar su ID en el hijo
                        if (idGeneradoPadre != null) {
                            datos.set(columnaFk, idGeneradoPadre);
                        }
                    }
                }
            }
            // Guardar tabla con las fks inyectadas y el ID generado
            idsGenerados.put(tabla, insertar(conn, tabla, datos));
        }
        return idsGenerados;
    }

    /**
     * Actualizar varias tablas en una sola transacción
     *
     * @param conn Conexión con MySQL
     * @param ordenTablas
     * @param datosPorTabla Nombre de la tabla y sus datos
     * @param valorPk Valor de la clave primaria
     * @return Número de filas afectadas
     * @throws SQLException
     */
    public int actualizarTransaccional(Connection conn, List<String> ordenTablas,
            Map<String, EntidadDinamica> datosPorTabla, Object valorPk) throws SQLException {
        int filasAfectadas = 0;
        for (String tabla : ordenTablas) {
            EntidadDinamica datos = datosPorTabla.get(tabla);
            if (datos == null) {
                continue;
            }
            filasAfectadas += actualizar(conn, tabla, datos, valorPk);
        }
        return filasAfectadas;
    }

    /**
     * Elimina varias tablas en una sola transacción
     *
     * @param conn Conexión con MySQL
     * @param ordenTablas
     * @param valorPk Valor de la clave primaria
     * @return Número de filas afectadas
     * @throws SQLException
     */
    public int eliminarTransaccional(Connection conn, List<String> ordenTablas,
            Object valorPk) throws SQLException {
        int filasAfectadas = 0;
        // Eliminar en orden inverso: de hijos a padres
        for (int i = ordenTablas.size() - 1; i >= 0; i--) {
            String tabla = ordenTablas.get(i);
            filasAfectadas += eliminar(conn, tabla, valorPk);
        }
        return filasAfectadas;
    }

    /**
     * SELECT de un registro por su ID
     *
     * @param conn Conexión con MySQL
     * @param nombreTabla Nombre de la tabla
     * @param columnas Metadatos de las columnasHijas para mapear
     * @param id Valor de la clave primaria
     * @return EntidadDinamica con el registro encontrado o null
     * @throws SQLException
     */
    public EntidadDinamica buscarPorId(Connection conn, String nombreTabla,
            List<ColumnaConfig> columnas, Long id) throws SQLException {
        String pkCol = detectarPK(conn, nombreTabla);
        String sql = "SELECT * FROM `" + nombreTabla + "` WHERE `" + pkCol + "` = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapper.mapear(rs, columnas);
                }
            }
        }
        return null; // Si no hay resultados
    }

    /**
     * Construir y ejecutar una consulta SELECT con o sin filtros (WHERE con
     * condición y ORDER BY) en una tabla MySQL
     *
     * @param conn Conexión con MySQL
     * @param nombreTabla Nombre de la tabla en la que se realizará la consulta
     * @param columnas Metadatos de las columnasHijas para mapear
     * @param filtros
     * @param sort Columna por la que se hace ORDER BY
     * @param order Criterio de ordenación: ASC para orden ascendente; DESC,
     * para descendente
     * @param limite Registros máximos buscados
     * @param offset Registro desde el que se comienza la búsqueda
     * @return Registros encontrados
     * @throws java.sql.SQLException
     */
    public List<EntidadDinamica> buscarTodo(Connection conn, String nombreTabla,
            List<ColumnaConfig> columnas, Map<String, String> filtros,
            String sort, String order, int limite, int offset) throws SQLException {

        // Validar límite y offset
        if (limite <= 0) {
            throw new IllegalArgumentException("El límite debe ser mayor que 0");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("El offset no puede ser negativo");
        }

        QueryResult qr = construirFiltrado(nombreTabla, columnas, filtros, sort, order, limite, offset);
        List<EntidadDinamica> resultados = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(qr.sql)) {
            for (int i = 0; i < qr.valores.size(); i++) {
                stmt.setObject(i + 1, qr.valores.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    resultados.add(mapper.mapear(rs, columnas));
                }
            }
        }
        return resultados;
    }

    /**
     * SELECT con JOIN para todos los registros de una tabla
     *
     * @param conn Conexión con MySQL
     * @param tablaPrincipal Nombre de la tabla principal
     * @param columnasPrincipal
     * @param relaciones
     * @param colsHijas
     * @param filtros
     * @param sort
     * @param order
     * @param limite
     * @param offset
     * @return
     * @throws SQLException
     */
    public List<EntidadDinamica> buscarConIncludes(Connection conn, String tablaPrincipal,
            List<ColumnaConfig> columnasPrincipal, List<RelacionConfig> relaciones,
            Map<String, List<ColumnaConfig>> colsHijas, Map<String, String> filtros,
            String sort, String order, int limite, int offset) throws SQLException {

        // Construir SQL con JOINS
        String[] sqlIncludes = construirIncludes(tablaPrincipal, relaciones, colsHijas);

        // Construir la subconsulta con filtros para la tabla principal
        QueryResult qr = construirFiltrado(tablaPrincipal, columnasPrincipal, filtros, sort, order, limite, offset);

        // Unirlas
        String sql = "SELECT t1.*" + sqlIncludes[0]
                + " FROM (" + qr.sql + ") t1"
                + sqlIncludes[1];

        // LinkedHashMap para mantener el orden de los resultados
        Map<Long, EntidadDinamica> mapaResultados = new LinkedHashMap<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < qr.valores.size(); i++) {
                stmt.setObject(i + 1, qr.valores.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Long idPadre = rs.getLong("id");
                    // Buscar padre para ver si ya ha sido procesado
                    EntidadDinamica padre = mapaResultados.get(idPadre);

                    // Si es la primera vez, se crea
                    if (padre == null) {
                        padre = mapper.mapear(rs, columnasPrincipal);
                        mapaResultados.put(idPadre, padre);
                    }
                    // Añadir hijos
                    mapper.agregarHijos(rs, tablaPrincipal, padre, relaciones, colsHijas);
                }
            }
        }
        // Convertir el mapa de nuevo a lista
        return new ArrayList<>(mapaResultados.values());
    }

    /**
     * SELECT con JOIN para un registro identificado por su ID
     *
     * @param conn Conexión con MySQL
     * @param tablaPrincipal Nombre de la tabla principal
     * @param id Valor de la clave primaria
     * @param columnasPrincipal Metadatos de la tabla principal para mapear
     * @param relaciones Datos sobre las relaciones de la tabla principal y el
     * resto de tablas de la consulta (includes)
     * @param colsHijas Colección con el nombre de cada tabla del includes y los
     * metadatos de sus columnasHijas para mapear
     * @return Registro encontrado o null
     * @throws SQLException
     */
    public EntidadDinamica buscarPorIdConIncludes(
            Connection conn, String tablaPrincipal, Long id,
            List<ColumnaConfig> columnasPrincipal, List<RelacionConfig> relaciones,
            Map<String, List<ColumnaConfig>> colsHijas) throws SQLException {

        String[] sqlIncludes = construirIncludes(tablaPrincipal, relaciones, colsHijas);

        String sql = "SELECT t1.*" + sqlIncludes[0]
                + " FROM `" + tablaPrincipal + "` t1"
                + sqlIncludes[1]
                + " WHERE t1.`id` = ?";

        EntidadDinamica padre = null;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    // Si es la primera fila, crear padre
                    if (padre == null) {
                        padre = mapper.mapear(rs, columnasPrincipal);
                    }
                    // Añadir hijos al padre
                    mapper.agregarHijos(rs, tablaPrincipal, padre, relaciones, colsHijas);
                }
            }
        }
        return padre;
    }

    private QueryResult construirFiltrado(String nombreTabla, List<ColumnaConfig> columnasConfig,
            Map<String, String> filtros, String sort, String order,
            int limite, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM `").append(nombreTabla).append("` ");
        List<Object> valores = new ArrayList<>();

        if (filtros != null && !filtros.isEmpty()) {
            sql.append(" WHERE ");
            List<String> condiciones = new ArrayList<>();
            for (Map.Entry<String, String> f : filtros.entrySet()) {
                String fullKey = f.getKey(); // Nombre de columna + prefijo
                String valorRaw = f.getValue(); // Condición

                // Extraer operador y nombre de columna 
                String[] partes = fullKey.split("__");
                String nombreColumna = partes[0]; // Nombre real de la columna
                String sufijo = (partes.length > 1) ? partes[1] : "eq"; // Operación (eq por defecto)

                // Validar que la columna existe en los metadatos
                boolean existeColumna = columnasConfig.stream()
                        .anyMatch(c -> c.getNombre().equalsIgnoreCase(nombreColumna));

                if (!existeColumna) {
                    continue; // Ignorar si la columna no existe
                }
                mapearSufijos(sufijo, nombreColumna, valorRaw, condiciones, valores);
            }
            sql.append(String.join(" AND ", condiciones));
        }

        if (sort != null && !sort.trim().isEmpty()) {
            sql.append(" ORDER BY `").append(sort).append("` ")
                    .append("desc".equalsIgnoreCase(order) ? "DESC" : "ASC");
        }

        sql.append(" LIMIT ? OFFSET ?");
        valores.add(limite);
        valores.add(offset);

        return new QueryResult(sql.toString(), valores);
    }

    /**
     * Construye una sentencia JOIN de SQL
     *
     * @param tablaPrincipal
     * @param relaciones
     * @param colsHijas
     * @return
     */
    private String[] construirIncludes(String tablaPrincipal, List<RelacionConfig> relaciones,
            Map<String, List<ColumnaConfig>> colsHijas) {
        StringBuilder columnasHijas = new StringBuilder();
        StringBuilder joins = new StringBuilder();

        int aliasCount = 2;
        for (RelacionConfig rel : relaciones) {
            String alias = "t" + aliasCount;  // Alias de la tabla
            // Buscar tabla origen y unirla a la de destino
            boolean isOrigen = rel.getTablaOrigen().equalsIgnoreCase(tablaPrincipal);
            String tablaAUnir = isOrigen ? rel.getTablaDestino() : rel.getTablaOrigen();
            // Obtener prefijo
            String prefijo = tablaAUnir + "_";

            // Extraer columnasHijas de la tabla hija
            List<ColumnaConfig> columnasHijo = colsHijas.get(tablaAUnir);
            if (columnasHijo != null) {
                // , alias.`columna` AS `prefijo_columna`
                for (ColumnaConfig col : columnasHijo) {
                    columnasHijas.append(", ").append(alias).append(".`").append(col.getNombre())
                            .append("` AS `").append(prefijo).append(col.getNombre()).append("`");
                }
            }

            // Construir el JOIN
            joins.append(" LEFT JOIN `").append(tablaAUnir).append("` ").append(alias);
            if (isOrigen) {
                joins.append(" ON t1.`").append(rel.getFkColumna()).append("` = ")
                        .append(alias).append(".`id` ");
            } else {
                joins.append(" ON t1.`id` = ")
                        .append(alias).append(".`").append(rel.getFkColumna()).append("` ");
            }
            aliasCount++;
        }

        return new String[]{columnasHijas.toString(), joins.toString()};
    }

    /**
     * Cuenta el total de registros de una tabla. Se utiliza para que el
     * frontend pueda conocer el número de páginas
     *
     * @param conn Conexión con MySQL
     * @param nombreTabla Nombre de la tabla cuyos registros se calcularán
     * @param filtros
     * @return Número total de registros de la tabla
     * @throws java.sql.SQLException
     */
    public long contarRegistros(Connection conn, String nombreTabla, Map<String, String> filtros) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM `").append(nombreTabla).append("` ");
        List<Object> valores = new ArrayList<>();

        if (filtros != null && !filtros.isEmpty()) {
            sql.append(" WHERE ");
            List<String> condiciones = new ArrayList<>();
            for (Map.Entry<String, String> f : filtros.entrySet()) {
                condiciones.add("`" + f.getKey() + "` = ?");
                valores.add(f.getValue());
            }
            sql.append(String.join(" AND ", condiciones));
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < valores.size(); i++) {
                stmt.setObject(i + 1, valores.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    /**
     * Obtiene un sufijo, correspondiente a una operación válida WHERE y
     * construye la sentencia SQL equivalente. Ejemplo: el sufijo "eq" se
     * traduce a `columna` = ?
     *
     * @param sufijo Sufijo para filtro WHERE (eq, lt, gt, lte, gte, contains)
     * @param nombreColumna Nombre de la columna
     * @param valor Valor de la condición
     * @param condiciones Lista de condiciones (array de sentencias)
     * @param valores Lista de valores (valor que reemplaza a ?)
     */
    private void mapearSufijos(String sufijo, String nombreColumna, String valor,
            List<String> condiciones, List<Object> valores) {
        switch (sufijo) {
            case "gt": // Mayor que (>)
                condiciones.add("`" + nombreColumna + "` > ?");
                valores.add(valor);
                break;
            case "lt": // Menor que (<)
                condiciones.add("`" + nombreColumna + "` < ?");
                valores.add(valor);
                break;
            case "gte": // Mayor o igual que (>=)
                condiciones.add("`" + nombreColumna + "` >= ?");
                valores.add(valor);
                break;
            case "lte": // Menor o igual que (<=)
                condiciones.add("`" + nombreColumna + "` <= ?");
                valores.add(valor);
                break;
            case "contains": // LIKE %val%
                condiciones.add("`" + nombreColumna + "` LIKE ?");
                valores.add("%" + valor + "%");
                break;
            default: // Igualdad (=)
                condiciones.add("`" + nombreColumna + "` = ?");
                valores.add(valor);
                break;
        }
    }

    // Clase auxiliar para transportar el SQL y sus parámetros
    private static class QueryResult {

        String sql;
        List<Object> valores;

        QueryResult(String sql, List<Object> valores) {
            this.sql = sql;
            this.valores = valores;
        }
    }

    /**
     * Detecta la columna PK real de una tabla usando SHOW KEYS.
     * Si no encuentra, devuelve "id" como fallback.
     */
    private String detectarPK(Connection conn, String nombreTabla) {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SHOW KEYS FROM `" + nombreTabla + "` WHERE Key_name = 'PRIMARY'")) {
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("Column_name");
                }
            }
        } catch (SQLException ignored) { }
        return "id";
    }
}
