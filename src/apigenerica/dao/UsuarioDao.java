package apigenerica.dao;

import apigenerica.config.AppConfig;
import apigenerica.config.ConexionMysql;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.model.EntidadDinamica;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Grupo1
 */
public class UsuarioDao {

    /**
     * Devuelve el nombre del rol de un usuario a partir de su id.
     * CAMBIO: antes leía la columna `rol` VARCHAR directamente de erp_users.
     * Ahora hace JOIN con erp_roles para obtener el nombre del rol a través
     * de la FK rol_id, que es la columna que existe en el nuevo esquema.
     */
    public String obtenerRol(Long id) {
        String sql =
            "SELECT r.nombre AS rol " +
            "FROM erp_users u " +
            "LEFT JOIN erp_roles r ON r.id = u.rol_id " +
            "WHERE u.id = ? AND u.activo = 1";

        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("rol");
                }
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al obtener el rol de la base de datos.", e);
        }
        return null;
    }

    /**
     * Devuelve los datos necesarios para el login: id, nombre del rol,
     * tipo (empleado/cliente) y hash de la contraseña.
     * CAMBIO: antes seleccionaba `rol` VARCHAR de erp_users directamente.
     * Ahora hace JOIN con erp_roles para obtener el nombre del rol, y además
     * recupera el campo `tipo` para que el frontend sepa a qué panel redirigir
     * al usuario sin depender del nombre del rol.
     */
    public EntidadDinamica obtenerDatosLogin(String email) {
        String sql =
            "SELECT u.id, u.contrasena, u.tipo, r.nombre AS rol " +
            "FROM erp_users u " +
            "LEFT JOIN erp_roles r ON r.id = u.rol_id " +
            "WHERE u.email = ? AND u.activo = 1";

        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    EntidadDinamica usuario = new EntidadDinamica();
                    usuario.setId(rs.getLong("id"));
                    usuario.set("rol",  rs.getString("rol"));
                    usuario.set("tipo", rs.getString("tipo"));
                    usuario.set("hash", rs.getString("contrasena"));
                    return usuario;
                }
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al obtener el login de la base de datos.", e);
        }
        return null;
    }
}