package apigenerica.service;

import apigenerica.config.AppConfig;
import apigenerica.config.ConexionMysql;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.mindrot.jbcrypt.BCrypt;

public class EmpleadoService {

    public boolean registrarEmpleadoConAcceso(String email, String password, String rol,
                                              String nombre, String apellido,
                                              String dni, String cargo) {
        String nombreRol = (rol == null || rol.trim().isEmpty()) ? "empleado" : rol.trim();
        String hashContrasena = BCrypt.hashpw(password, BCrypt.gensalt(10));
        long nuevoUserId;

        try (Connection connSistema = ConexionMysql.getConexion(AppConfig.DB_SISTEMA)) {
            connSistema.setAutoCommit(false);
            try {
                int rolId = obtenerRolId(connSistema, nombreRol);
                String sqlUser = "INSERT INTO erp_users (email, contrasena, rol_id, tipo, activo) VALUES (?, ?, ?, ?, 1)";
                try (PreparedStatement psUser = connSistema.prepareStatement(sqlUser, Statement.RETURN_GENERATED_KEYS)) {
                    psUser.setString(1, email);
                    psUser.setString(2, hashContrasena);
                    psUser.setInt(3, rolId);
                    psUser.setString(4, nombreRol);
                    psUser.executeUpdate();
                    try (ResultSet generados = psUser.getGeneratedKeys()) {
                        if (!generados.next())
                            throw new SQLException("No se obtuvo el ID generado para el nuevo usuario.");
                        nuevoUserId = generados.getLong(1);
                    }
                }
                connSistema.commit();
            } catch (SQLException e) {
                connSistema.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.err.println("[API] Error insertando en erp_users: " + e.getMessage());
            return false;
        }

        try (Connection connCliente = ConexionMysql.getConexion(AppConfig.DB_CLIENTE)) {
            connCliente.setAutoCommit(false);
            try {
                String sqlEmpleado =
                    "INSERT INTO empleados (user_id, correo_electronico, nombre, primer_apellido, dni_nie, cargo) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement psEmp = connCliente.prepareStatement(sqlEmpleado)) {
                    psEmp.setLong(1, nuevoUserId);
                    psEmp.setString(2, email);
                    psEmp.setString(3, nombre);
                    psEmp.setString(4, apellido);
                    psEmp.setString(5, dni);
                    psEmp.setString(6, cargo);
                    psEmp.executeUpdate();
                }
                connCliente.commit();
            } catch (SQLException e) {
                connCliente.rollback();
                try (Connection connDeshace = ConexionMysql.getConexion(AppConfig.DB_SISTEMA);
                     PreparedStatement psDel = connDeshace.prepareStatement("DELETE FROM erp_users WHERE id = ?")) {
                    psDel.setLong(1, nuevoUserId);
                    psDel.executeUpdate();
                    System.err.println("[API] Compensacion: usuario id=" + nuevoUserId + " eliminado de erp_users.");
                } catch (SQLException ex) {
                    System.err.println("[API] Error en compensacion: " + ex.getMessage());
                }
                throw e;
            }
        } catch (SQLException e) {
            System.err.println("[API] Error insertando en empleados: " + e.getMessage());
            return false;
        }

        System.out.println("[API] Empleado y usuario creados correctamente: " + email + " (user_id=" + nuevoUserId + ")");
        return true;
    }

    /**
     * PUT /api/empleados/{id}
     * Actualiza los campos de la tabla empleados para el id dado.
     * Acepta: nombre, primer_apellido, segundo_apellido, dni_nie, nss, iban,
     *         telefono, direccion, correo_electronico, email, cargo, foto_url / fotoUrl
     */
    public boolean actualizarEmpleado(int idEmpleado, Map<String, Object> datos) {
        if (datos == null || datos.isEmpty()) return false;

        // Mapa de nombres alternativos -> nombre real de columna en BD
        Map<String, String> alias = Map.of(
            "fotoUrl",            "foto_url",
            "primerApellido",     "primer_apellido",
            "segundoApellido",    "segundo_apellido",
            "dniNie",             "dni_nie",
            "email",              "correo_electronico"
        );

        // Columnas permitidas (las que existen en la tabla empleados)
        List<String> permitidas = List.of(
            "nombre", "primer_apellido", "segundo_apellido", "dni_nie",
            "nss", "iban", "telefono", "direccion", "correo_electronico",
            "cargo", "foto_url"
        );

        List<String> setClauses = new ArrayList<>();
        List<Object> valores    = new ArrayList<>();

        for (Map.Entry<String, Object> entry : datos.entrySet()) {
            String campo = entry.getKey();
            // Normalizar alias
            campo = alias.getOrDefault(campo, campo);
            if (!permitidas.contains(campo)) continue;
            setClauses.add(campo + " = ?");
            valores.add(entry.getValue());
        }

        if (setClauses.isEmpty()) return false;

        valores.add(idEmpleado);
        String sql = "UPDATE empleados SET " + String.join(", ", setClauses) + " WHERE id = ?";

        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_CLIENTE);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < valores.size(); i++) {
                ps.setObject(i + 1, valores.get(i));
            }
            int filas = ps.executeUpdate();
            return filas > 0;
        } catch (SQLException e) {
            System.err.println("[API] Error al actualizar empleado id=" + idEmpleado + ": " + e.getMessage());
            return false;
        }
    }

    public boolean cambiarRolEmpleado(int idEmpleado, String nuevoRol) {
        String sql =
            "UPDATE erp_users u " +
            "JOIN " + AppConfig.DB_CLIENTE + ".empleados e ON e.user_id = u.id " +
            "SET u.rol_id = ?, u.tipo = ? " +
            "WHERE e.id = ?";
        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA)) {
            String nombreRol = (nuevoRol == null || nuevoRol.trim().isEmpty()) ? "empleado" : nuevoRol.trim();
            int rolId = obtenerRolId(conn, nombreRol);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, rolId);
                ps.setString(2, nombreRol);
                ps.setInt(3, idEmpleado);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("[API] Error al cambiar rol del empleado " + idEmpleado + ": " + e.getMessage());
            return false;
        }
    }

    private int obtenerRolId(Connection conn, String nombreRol) {
        String sql = "SELECT id FROM erp_roles WHERE nombre = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nombreRol);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        } catch (SQLException e) {
            System.err.println("[API] Error al buscar ID de rol en la BD: " + e.getMessage());
        }
        if (nombreRol.equalsIgnoreCase("admin") || nombreRol.equalsIgnoreCase("administrador")) return 1;
        if (nombreRol.equalsIgnoreCase("cliente")) return 3;
        if (nombreRol.equalsIgnoreCase("recursos_humanos") || nombreRol.equalsIgnoreCase("recursos humanos")) return 4;
        return 2;
    }
}