package apigenerica.dao;

import apigenerica.config.AppConfig;
import apigenerica.config.ConexionMysql;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.model.RolConfig;
import apigenerica.model.PermisoConfig;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la gestión de roles y permisos en erp_sistema.
 * Accede a las tablas erp_roles y erp_roles_permisos.
 *
 * CAMBIOS RESPECTO A LA VERSIÓN ANTERIOR:
 * ─────────────────────────────────────────────────────────────────────────────
 * obtenerPermisos(String rol): antes el parámetro podía llegar como null
 * (lo llamaba obtenerPermisosPorRolYTabla desde RolController con null).
 * En MySQL "WHERE rol = NULL" no devuelve nada porque NULL no es comparable
 * con =, hay que usar IS NULL. Además no tiene sentido devolver permisos sin
 * saber de qué rol son.
 *
 * El fix tiene dos partes:
 *   1. Si rol es null o vacío, devolvemos lista vacía inmediatamente
 *      en lugar de lanzar una query que siempre retorna 0 filas.
 *   2. obtenerPermisosPorRolYTabla (el endpoint /api/erp/permisos que usa
 *      el frontend legacy) ahora extrae el rol del query param 'rol_nombre'
 *      antes de llamar a obtenerPermisos, evitando pasar null.
 * 
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * @author Grupo1
 */
public class RolDao {

    // ══════════════════════════════════════════════════
    // OPERACIONES CON erp_roles
    // ══════════════════════════════════════════════════

    /**
     * Devuelve la lista de todos los roles definidos.
     */
    public List<RolConfig> listarRoles() {
        String sql = "SELECT id, nombre, descripcion FROM erp_roles ORDER BY nombre";
        List<RolConfig> roles = new ArrayList<>();
        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA);
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                roles.add(new RolConfig(
                    rs.getLong("id"),
                    rs.getString("nombre"),
                    rs.getString("descripcion")
                ));
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al listar roles.", e);
        }
        return roles;
    }

    /**
     * Crea un nuevo rol. Lanza excepción si ya existe.
     */
    public void crearRol(String nombre, String descripcion) {
        String sql = "INSERT INTO erp_roles (nombre, descripcion) VALUES (?, ?)";
        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, nombre);
            stmt.setString(2, descripcion);
            stmt.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) { // Duplicate key
                throw new BaseDatosException("Ya existe un rol con el nombre '" + nombre + "'.", e);
            }
            throw new BaseDatosException("Error al crear el rol.", e);
        }
    }

    /**
     * Elimina un rol y todos sus permisos asociados (en transacción).
     */
    public boolean eliminarRol(String nombre) {
        String sqlPermisos = "DELETE FROM erp_roles_permisos WHERE rol = ?";
        String sqlRol      = "DELETE FROM erp_roles WHERE nombre = ?";
        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA)) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement stmtP = conn.prepareStatement(sqlPermisos)) {
                    stmtP.setString(1, nombre);
                    stmtP.executeUpdate();
                }
                try (PreparedStatement stmtR = conn.prepareStatement(sqlRol)) {
                    stmtR.setString(1, nombre);
                    int filas = stmtR.executeUpdate();
                    conn.commit();
                    return filas > 0;
                }
            } catch (Exception e) {
                conn.rollback();
                throw new BaseDatosException("Error al eliminar el rol.", e);
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error de conexión al eliminar rol.", e);
        }
    }

    /**
     * Comprueba si un rol existe por nombre.
     */
    public boolean existeRol(String nombre) {
        String sql = "SELECT 1 FROM erp_roles WHERE nombre = ?";
        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, nombre);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al verificar el rol.", e);
        }
    }

    // ══════════════════════════════════════════════════
    // OPERACIONES CON erp_roles_permisos
    // ══════════════════════════════════════════════════

    /**
     * Devuelve los permisos de un rol sobre todas las tablas/secciones.
     *
     * CAMBIO: si rol es null o vacío devolvemos lista vacía de inmediato.
     * Antes se ejecutaba "WHERE rol = ?" con null, que en MySQL nunca
     * hace match (NULL != NULL con =) y siempre devolvía 0 filas de forma
     * silenciosa, lo cual era confuso y un bug difícil de detectar.
     */
    public List<PermisoConfig> obtenerPermisos(String rol) {
        // Guard: sin rol no tiene sentido consultar
        if (rol == null || rol.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String sql = "SELECT tabla, puede_ver, puede_crear, puede_editar, puede_eliminar "
                   + "FROM erp_roles_permisos WHERE rol = ? ORDER BY tabla";
        List<PermisoConfig> permisos = new ArrayList<>();
        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, rol);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    permisos.add(new PermisoConfig(
                        rol,
                        rs.getString("tabla"),
                        rs.getBoolean("puede_ver"),
                        rs.getBoolean("puede_crear"),
                        rs.getBoolean("puede_editar"),
                        rs.getBoolean("puede_eliminar")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al obtener permisos del rol.", e);
        }
        return permisos;
    }

    /**
     * Guarda (upsert) los permisos de un rol sobre una tabla concreta.
     * Si ya existe el registro lo actualiza; si no, lo inserta.
     */
    public void guardarPermiso(PermisoConfig permiso) {
        String sql = "INSERT INTO erp_roles_permisos (rol, tabla, puede_ver, puede_crear, puede_editar, puede_eliminar) "
                   + "VALUES (?, ?, ?, ?, ?, ?) "
                   + "ON DUPLICATE KEY UPDATE "
                   + "puede_ver = VALUES(puede_ver), "
                   + "puede_crear = VALUES(puede_crear), "
                   + "puede_editar = VALUES(puede_editar), "
                   + "puede_eliminar = VALUES(puede_eliminar)";
        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, permiso.getRol());
            stmt.setString(2, permiso.getTabla());
            stmt.setBoolean(3, permiso.isPuedeVer());
            stmt.setBoolean(4, permiso.isPuedeCrear());
            stmt.setBoolean(5, permiso.isPuedeEditar());
            stmt.setBoolean(6, permiso.isPuedeEliminar());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new BaseDatosException("Error al guardar el permiso.", e);
        }
    }
}