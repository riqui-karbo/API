package apigenerica.dao;

import apigenerica.config.AppConfig;
import apigenerica.config.ConexionMysql;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.excepciones.RecursoNoEncontradoException;
import apigenerica.model.EntidadDinamica;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * @author Grupo1
 */
public class UsuarioDao {

    /**
     * Devuelve el nombre del rol de un usuario a partir de su id.
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
    public EntidadDinamica obtenerPorId(Long id) {
        String sql = "SELECT u.id, u.email, u.rol as rol_id, r.nombre as rol_nombre, u.activo " +
                 "FROM `erp_users` u " +
                 "JOIN `erp_roles` r ON u.rol = r.id " +
                 "WHERE u.id = ?";

        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    EntidadDinamica usuario = new EntidadDinamica();
                    usuario.setId(rs.getLong("id"));
                    usuario.set("email", rs.getString("email"));
                    usuario.set("rol_id", rs.getInt("rol_id"));
                    usuario.set("rol", rs.getString("rol_nombre"));
                    usuario.set("activo", rs.getInt("activo"));
                    return usuario;
                }
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al obtener el usuario por ID.", e);
        }
        return null;
    }
    
    public long crearUsuario(String email, String hash, int rolId) {
        String sql = "INSERT INTO `erp_users` (email, contrasena, rol) VALUES (?, ?, ?)";
        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA); PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, email);
            ps.setString(2, hash);
            ps.setInt(3, rolId);
            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        } catch (SQLException e) {
            throw new BaseDatosException("Error al registrar usuario (posible email duplicado).", e);
        }
    }

    public void actualizarUsuario(Long id, String email, int rolId, int activo) {
        String sql = "UPDATE `erp_users` SET email = ?, rol = ?, activo = ? WHERE id = ?";

        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);
            ps.setInt(2, rolId);
            ps.setInt(3, activo);
            ps.setLong(4, id);

            if (ps.executeUpdate() == 0) {
                throw new RecursoNoEncontradoException("Usuario no encontrado.");
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al actualizar usuario.", e);
        }
    }

    public void eliminarUsuario(Long id) {
        String sql = "DELETE FROM `erp_users` WHERE id = ?";
        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            if (ps.executeUpdate() == 0) {
                throw new RecursoNoEncontradoException("Usuario no encontrado.");
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al eliminar usuario.", e);
        }
    }
}