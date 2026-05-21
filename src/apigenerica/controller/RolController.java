package apigenerica.controller;

import apigenerica.dao.RolDao;
import apigenerica.excepciones.RecursoNoEncontradoException;
import apigenerica.excepciones.ValidacionException;
import apigenerica.model.ApiRespuesta;
import apigenerica.model.PermisoConfig;
import apigenerica.model.RolConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpCode;
import java.util.List;
import java.util.Map;

/**
 * Controlador para la gestión de roles y permisos del ERP.
 * Solo accesible por usuarios con rol 'admin'.
 *
 * Endpoints:
 *   GET    /api/roles                          → listarRoles
 *   POST   /api/roles                          → crearRol
 *   DELETE /api/roles/{nombre}                 → eliminarRol
 *   GET    /api/roles/{nombre}/permisos        → obtenerPermisos
 *   PUT    /api/roles/{nombre}/permisos        → guardarPermiso
 *
 * @author Grupo1
 */
public class RolController {

    private final RolDao rolDao;

    public RolController(RolDao rolDao) {
        this.rolDao = rolDao;
    }

    // ── GET /api/roles ────────────────────────────────────────────────────────
    /**
     * Lista todos los roles definidos en el sistema.
     */
    public void listarRoles(Context ctx) {
        List<RolConfig> roles = rolDao.listarRoles();
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok(roles));
    }

    // ── POST /api/roles ───────────────────────────────────────────────────────
    /**
     * Crea un nuevo rol.
     * Body JSON: { "nombre": "rrhh", "descripcion": "Recursos Humanos" }
     */
    @SuppressWarnings("unchecked")
    public void crearRol(Context ctx) {
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String nombre = body.get("nombre");
        String descripcion = body.getOrDefault("descripcion", "");

        if (nombre == null || nombre.trim().isEmpty()) {
            throw new ValidacionException("El campo 'nombre' es obligatorio.");
        }
        // Normalizar: minúsculas, sin espacios
        nombre = nombre.trim().toLowerCase().replaceAll("\\s+", "_");

        if (rolDao.existeRol(nombre)) {
            throw new ValidacionException("Ya existe un rol con el nombre '" + nombre + "'.");
        }

        rolDao.crearRol(nombre, descripcion.trim());
        ctx.status(HttpCode.CREATED).json(ApiRespuesta.ok("Rol '" + nombre + "' creado correctamente."));
    }

    // ── DELETE /api/roles/{nombre} ────────────────────────────────────────────
    /**
     * Elimina un rol y todos sus permisos asociados.
     */
    public void eliminarRol(Context ctx) {
        String nombre = ctx.pathParam("nombre");

        if (!rolDao.existeRol(nombre)) {
            throw new RecursoNoEncontradoException("No existe el rol '" + nombre + "'.");
        }

        // Proteger el rol 'admin' de ser eliminado
        if ("admin".equalsIgnoreCase(nombre)) {
            throw new ValidacionException("El rol 'admin' no puede eliminarse.");
        }

        boolean eliminado = rolDao.eliminarRol(nombre);
        if (eliminado) {
            ctx.status(HttpCode.OK).json(ApiRespuesta.ok("Rol '" + nombre + "' eliminado correctamente."));
        } else {
            throw new RecursoNoEncontradoException("No se encontró el rol '" + nombre + "'.");
        }
    }

    // ── GET /api/roles/{nombre}/permisos ──────────────────────────────────────
    /**
     * Devuelve la lista de permisos de un rol sobre todas las tablas/secciones.
     */
    public void obtenerPermisos(Context ctx) {
        String nombre = ctx.pathParam("nombre");

        if (!rolDao.existeRol(nombre)) {
            throw new RecursoNoEncontradoException("No existe el rol '" + nombre + "'.");
        }

        List<PermisoConfig> permisos = rolDao.obtenerPermisos(nombre);
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok(permisos));
    }

    // ── PUT /api/roles/{nombre}/permisos ──────────────────────────────────────
    /**
     * Guarda o actualiza los permisos de un rol sobre una tabla/sección.
     * Body JSON:
     * {
     *   "tabla": "empleados",
     *   "puedeVer": true,
     *   "puedeCrear": false,
     *   "puedeEditar": false,
     *   "puedeEliminar": false
     * }
     */
    public void guardarPermiso(Context ctx) {
        // Puede venir como /api/roles/{nombre}/permisos (con path param)
        // o como /api/erp/permisos (sin path param, el rol viene en el body)
        String nombre = null;
        try { nombre = ctx.pathParam("nombre"); } catch (Exception ignored) {}

        PermisoConfig permiso = ctx.bodyAsClass(PermisoConfig.class);

        // Si el rol no viene del path, intentar sacarlo del body (compatibilidad /erp/permisos)
        if (nombre == null || nombre.isEmpty()) {
            nombre = permiso.getRol();
        }

        if (nombre == null || nombre.trim().isEmpty()) {
            throw new ValidacionException("Se debe especificar el rol.");
        }

        if (!rolDao.existeRol(nombre)) {
            throw new RecursoNoEncontradoException("No existe el rol '" + nombre + "'.");
        }

        if (permiso.getTabla() == null || permiso.getTabla().trim().isEmpty()) {
            throw new ValidacionException("El campo 'tabla' es obligatorio.");
        }

        permiso.setRol(nombre);
        rolDao.guardarPermiso(permiso);
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok(
            "Permisos del rol '" + nombre + "' sobre '" + permiso.getTabla() + "' guardados correctamente."
        ));
    }

    /**
     * GET /api/erp/permisos?rol_id=X&tabla=Y  (compatibilidad con el frontend legacy)
     * El frontend pasa rol_id pero la API nueva usa nombre de rol como identificador.
     */
    public void obtenerPermisosPorRolYTabla(Context ctx) {
        // El frontend manda rol_id (número), pero la API nueva usa nombre de rol
        // Devolvemos permisos vacíos por defecto si no encontramos el rol
        String tabla = ctx.queryParam("tabla");
        List<PermisoConfig> todos = rolDao.obtenerPermisos(null);
        if (tabla != null && !tabla.isEmpty()) {
            todos = todos.stream()
                    .filter(p -> tabla.equals(p.getTabla()))
                    .collect(java.util.stream.Collectors.toList());
        }
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok(todos));
    }
}
