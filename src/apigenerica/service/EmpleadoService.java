package apigenerica.service;

import apigenerica.config.AppConfig;
import apigenerica.config.ConexionMysql;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Servicio para el registro doble de empleados adaptado a la base de datos real:
 * 1. Crea la cuenta de acceso en erp_sistema.erp_users mapeando el rol de texto a 'rol_id' (INT) y 'tipo' (VARCHAR).
 * 2. Crea el expediente personal en erp_empresa.empleados vinculando el user_id.
 */
public class EmpleadoService {

    /**
     * Registra un empleado en la base de datos del cliente y, al mismo tiempo,
     * le crea su cuenta de acceso en la base de datos del sistema.
     */
    public boolean registrarEmpleadoConAcceso(String email, String password, String rol,
                                              String nombre, String apellido,
                                              String dni, String cargo) {

        Connection connSistema = null;
        Connection connCliente = null;

        try {
            connSistema = ConexionMysql.getConexion(AppConfig.DB_SISTEMA);
            connCliente = ConexionMysql.getConexion(AppConfig.DB_CLIENTE);

            connSistema.setAutoCommit(false);
            connCliente.setAutoCommit(false);

            // ── 1. NORMALIZAR TEXTO Y BUSCAR ID DEL ROL ──────────────────────────
            String nombreRol = (rol == null || rol.trim().isEmpty()) ? "empleado" : rol.trim();
            int rolId = obtenerRolId(connSistema, nombreRol);

            // Cifrar la contraseña con BCrypt
            String hashContrasena = BCrypt.hashpw(password, BCrypt.gensalt(10));

            // ── 2. PASO A: INSERTAR EN ERP_USERS ──────────────────────────────────
            String sqlUser = "INSERT INTO erp_users (email, contrasena, rol_id, tipo, activo) VALUES (?, ?, ?, ?, 1)";
            long nuevoUserId;

            try (PreparedStatement psUser = connSistema.prepareStatement(sqlUser, Statement.RETURN_GENERATED_KEYS)) {

                psUser.setString(1, email);
                psUser.setString(2, hashContrasena);
                psUser.setInt(3, rolId);          // ID numérico correcto (ej: 4)
                psUser.setString(4, nombreRol);    // Texto descriptivo (ej: "recursos_humanos")
                psUser.executeUpdate();

                // Recuperamos el ID autogenerado para vincularlo en la otra BD
                try (ResultSet generados = psUser.getGeneratedKeys()) {
                    if (!generados.next()) {
                        throw new SQLException("No se obtuvo el ID generado para el nuevo usuario.");
                    }
                    nuevoUserId = generados.getLong(1);
                }
            }

            // ── 3. PASO B: INSERTAR EN EMPLEADOS CON EL USER_ID VINCULADO ───────────
            String sqlEmpleado =
                "INSERT INTO empleados (user_id, correo_electronico, nombre, primer_apellido, dni_nie, cargo) "
              + "VALUES (?, ?, ?, ?, ?, ?)";

            try (PreparedStatement psEmp = connCliente.prepareStatement(sqlEmpleado)) {
                psEmp.setLong(1, nuevoUserId);
                psEmp.setString(2, email);
                psEmp.setString(3, nombre);
                psEmp.setString(4, apellido);
                psEmp.setString(5, dni);
                psEmp.setString(6, cargo);
                psEmp.executeUpdate();
            }

            // ── Confirmar ambas transacciones si todo ha ido bien ────────────────
            connSistema.commit();
            connCliente.commit();

            System.out.println("[API] Empleado y usuario creados correctamente: " + email
                             + " (user_id=" + nuevoUserId + ")");
            return true;

        } catch (SQLException e) {
            System.err.println("[API] Error en el registro doble. Aplicando rollback... " + e.getMessage());
            try {
                if (connSistema != null) connSistema.rollback();
                if (connCliente != null) connCliente.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            return false;

        } finally {
            try {
                if (connSistema != null) connSistema.close();
                if (connCliente != null) connCliente.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Cambia el rol de un empleado actualizando erp_users en la BD del sistema.
     */
    public boolean cambiarRolEmpleado(int idEmpleado, String nuevoRol) {
        String sql =
            "UPDATE erp_users u " +
            "JOIN erp_empresa.empleados e ON e.user_id = u.id " +
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

    /**
     * Busca el ID numérico del rol utilizando únicamente la columna real 'nombre'.
     */
    private int obtenerRolId(Connection conn, String nombreRol) {
        // CORREGIDO: Eliminada la columna inexistente 'nombre_rol'
        String sql = "SELECT id FROM erp_roles WHERE nombre = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nombreRol);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            System.err.println("[API] Error al buscar ID de rol en la BD: " + e.getMessage());
        }

        // MAPEO DE RESPALDO SEGURO (En caso de que la conexión falle)
        if (nombreRol.equalsIgnoreCase("admin") || nombreRol.equalsIgnoreCase("administrador")) {
            return 1;
        }
        if (nombreRol.equalsIgnoreCase("cliente")) {
            return 3;
        }
        if (nombreRol.equalsIgnoreCase("recursos_humanos") || nombreRol.equalsIgnoreCase("recursos humanos")) {
            return 4; // ID correspondiente según tus logs de salida
        }
        return 2;     // "empleado" por defecto
    }
}