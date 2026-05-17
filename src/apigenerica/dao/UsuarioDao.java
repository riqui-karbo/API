/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
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
 *
 * @author Grupo1
 */
public class UsuarioDao {

    /**
     * Consulta la base de datos para obtener rol de un usuario
     * de la aplicación a partir de su id
     *
     * @param id ID del usuario
     * @return Rol obtenido
     */
    public String obtenerRol(Long id) {
        String sql = "SELECT rol FROM erp_users WHERE id = ? AND activo = 1";

        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("rol");
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al obtener el rol de la base de datos.", e);
        }
        return null;
    }

    /**
     * Consulta la base de datos para obtener el id, rol y hash de la contraseña
     * de un usuario de la aplicación a partir de su email
     * 
     * @param email Email del usuario
     * @return Id, rol y hash de la contraseña del usuario especificado
     */
    public EntidadDinamica obtenerDatosLogin(String email) {
        String sql = "SELECT id, rol, contrasena FROM `erp_users` WHERE `email` = ? AND `activo` = 1";
        
        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA); 
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    EntidadDinamica usuario = new EntidadDinamica();
                    usuario.setId(rs.getLong("id"));
                    usuario.set("rol", rs.getString("rol"));
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
