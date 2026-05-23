package apigenerica.controller;

import apigenerica.dto.EmpleadoRegistroDTO;
import apigenerica.model.ApiRespuesta;
import apigenerica.service.EmpleadoService;
import io.javalin.http.Context;
import io.javalin.http.HttpCode;
import java.util.Map;

/**
 * Controlador Javalin para el registro de empleados.
 * Reemplaza el antiguo @WebServlet que nunca llegó a funcionar con Javalin.
 *
 * La ruta se registra en ApiGenerica.java:
 *   app.post("/api/empleados/registrar", ctx -> empleadoCtrl.registrar(ctx));
 */
public class EmpleadoController {

    private final EmpleadoService empleadoService;

    public EmpleadoController(EmpleadoService empleadoService) {
        this.empleadoService = empleadoService;
    }

    /**
     * POST /api/empleados/registrar
     *
     * Body JSON esperado:
     * {
     *   "email":          "juan@empresa.com",
     *   "contrasena":     "contraseña_en_plano",
     *   "rol":            "empleado",        <- opcional, por defecto "empleado"
     *   "nombre":         "Juan",
     *   "primerApellido": "García",
     *   "dniNie":         "12345678A",
     *   "cargo":          "Comercial"        <- opcional
     * }
     */
    public void registrar(Context ctx) {
        EmpleadoRegistroDTO dto = ctx.bodyAsClass(EmpleadoRegistroDTO.class);

        // Validaciones obligatorias
        if (dto.getEmail() == null || dto.getEmail().trim().isEmpty()
                || dto.getContrasena() == null || dto.getContrasena().trim().isEmpty()
                || dto.getDniNie() == null || dto.getDniNie().trim().isEmpty()) {

            ctx.status(HttpCode.BAD_REQUEST)
               .json(ApiRespuesta.error("Email, contrasena y dniNie son campos obligatorios."));
            return;
        }

        // Rol por defecto si no se envía
        String rol = (dto.getRol() != null && !dto.getRol().trim().isEmpty())
                ? dto.getRol().trim()
                : "empleado";

        boolean ok = empleadoService.registrarEmpleadoConAcceso(
                dto.getEmail().trim(),
                dto.getContrasena(),
                rol,
                dto.getNombre()          != null ? dto.getNombre().trim()          : "",
                dto.getPrimerApellido()  != null ? dto.getPrimerApellido().trim()  : "",
                dto.getSegundoApellido() != null ? dto.getSegundoApellido().trim() : null,
                dto.getDniNie().trim(),
                dto.getTelefono()        != null ? dto.getTelefono().trim()        : null,
                dto.getDireccion()       != null ? dto.getDireccion().trim()       : null,
                dto.getIban()            != null ? dto.getIban().trim()            : null,
                dto.getNss()             != null ? dto.getNss().trim()             : null,
                dto.getCargo()           != null ? dto.getCargo().trim()           : "",
                dto.getFotoUrlNormalizada() != null ? dto.getFotoUrlNormalizada().trim() : null
        );

        if (ok) {
            ctx.status(HttpCode.CREATED)
               .json(ApiRespuesta.ok("Empleado y cuenta creados correctamente."));
        } else {
            ctx.status(HttpCode.INTERNAL_SERVER_ERROR)
               .json(ApiRespuesta.error("Error al crear el empleado. Email o DNI posiblemente duplicados."));
        }
    }
   
    /**
    * PUT /api/empleados/{id}/rol
    *
    * Solo accesible para admin (el guard en ApiGenerica ya lo verifica).
    * Actualiza la columna 'rol' de erp_users para el usuario vinculado al empleado.
    *
    * Body JSON: { "rol": "comercial" }
    */
   public void cambiarRol(Context ctx) {
       int idEmpleado;
       try {
           idEmpleado = Integer.parseInt(ctx.pathParam("id"));
       } catch (NumberFormatException e) {
           ctx.status(HttpCode.BAD_REQUEST).json(ApiRespuesta.error("ID de empleado no válido."));
           return;
       }

       Map<String, String> body = ctx.bodyAsClass(Map.class);
       String nuevoRol = body.get("rol");
       if (nuevoRol == null || nuevoRol.trim().isEmpty()) {
           ctx.status(HttpCode.BAD_REQUEST).json(ApiRespuesta.error("El campo 'rol' es obligatorio."));
           return;
       }
       nuevoRol = nuevoRol.trim().toLowerCase();

       boolean ok = empleadoService.cambiarRolEmpleado(idEmpleado, nuevoRol);
       if (ok) {
           ctx.status(HttpCode.OK).json(ApiRespuesta.ok("Rol actualizado correctamente."));
       } else {
           ctx.status(HttpCode.INTERNAL_SERVER_ERROR)
              .json(ApiRespuesta.error("No se pudo actualizar el rol. Comprueba que el empleado y el rol existen."));
       }
   }
}