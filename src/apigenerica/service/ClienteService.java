/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.service;

import apigenerica.config.AppConfig;
import apigenerica.config.ConexionMysql;
import java.sql.*;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Registra un nuevo cliente:
 *  1. Crea cuenta en erp_sistema.erp_users  (tipo='cliente', rol='cliente')
 *  2. Crea fila en erp_empresa.clientes vinculada por user_id
 * Si el paso 2 falla, el usuario recién creado se elimina (compensación manual).
 */
public class ClienteService {

    public boolean registrarClienteConAcceso(
            String email, String password,
            String nombre, String apellido,
            String cifNif, String telefono, String direccion) {

        String hashContrasena = BCrypt.hashpw(password, BCrypt.gensalt(10));
        long nuevoUserId;

        // ── PASO 1: erp_users ─────────────────────────────────────────────────
        try (Connection connSistema = ConexionMysql.getConexion(AppConfig.DB_SISTEMA)) {
            connSistema.setAutoCommit(false);
            try {
                int rolId = obtenerRolId(connSistema, "cliente");

                String sql = "INSERT INTO erp_users (email, contrasena, rol_id, tipo, activo) VALUES (?, ?, ?, 'cliente', 1)";
                try (PreparedStatement ps = connSistema.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, email);
                    ps.setString(2, hashContrasena);
                    ps.setInt(3, rolId);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Sin ID generado para erp_users.");
                        nuevoUserId = keys.getLong(1);
                    }
                }
                connSistema.commit();
            } catch (SQLException e) {
                connSistema.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.err.println("[API] Error insertando cliente en erp_users: " + e.getMessage());
            return false;
        }

        // ── PASO 2: clientes ──────────────────────────────────────────────────
        try (Connection connCliente = ConexionMysql.getConexion(AppConfig.DB_CLIENTE)) {
            connCliente.setAutoCommit(false);
            try {
                String sql = "INSERT INTO clientes (nombre, apellido, cif_nif, email, telefono, direccion, user_id) "
                           + "VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = connCliente.prepareStatement(sql)) {
                    ps.setString(1, nombre);
                    ps.setString(2, apellido.isEmpty() ? null : apellido);
                    ps.setString(3, cifNif);
                    ps.setString(4, email);
                    ps.setString(5, telefono.isEmpty() ? null : telefono);
                    ps.setString(6, direccion.isEmpty() ? null : direccion);
                    ps.setLong(7, nuevoUserId);
                    ps.executeUpdate();
                }
                connCliente.commit();
            } catch (SQLException e) {
                connCliente.rollback();
                // Compensación: borrar el usuario recién creado
                try (Connection connDeshace = ConexionMysql.getConexion(AppConfig.DB_SISTEMA);
                     PreparedStatement psDel = connDeshace.prepareStatement("DELETE FROM erp_users WHERE id = ?")) {
                    psDel.setLong(1, nuevoUserId);
                    psDel.executeUpdate();
                    System.err.println("[API] Compensación: usuario id=" + nuevoUserId + " eliminado de erp_users.");
                } catch (SQLException ex) {
                    System.err.println("[API] Error en compensación: " + ex.getMessage());
                }
                throw e;
            }
        } catch (SQLException e) {
            System.err.println("[API] Error insertando en clientes: " + e.getMessage());
            return false;
        }

        System.out.println("[API] Cliente registrado: " + email + " (user_id=" + nuevoUserId + ")");
        return true;
    }

    private int obtenerRolId(Connection conn, String nombreRol) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM erp_roles WHERE nombre = ?")) {
            ps.setString(1, nombreRol);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        } catch (SQLException e) {
            System.err.println("[API] Error buscando rol '" + nombreRol + "': " + e.getMessage());
        }
        return 3; // fallback: id del rol 'cliente' por defecto
    }
}