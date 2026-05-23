package apigenerica.controller;

import apigenerica.dto.EmpleadoRegistroDTO;
import apigenerica.model.ApiRespuesta;
import apigenerica.service.EmpleadoService;
import io.javalin.http.Context;
import io.javalin.http.HttpCode;
import java.util.Map;

public class EmpleadoController {

    private final EmpleadoService empleadoService;

    public EmpleadoController(EmpleadoService empleadoService) {
        this.empleadoService = empleadoService;
    }

    /**
     * POST /api/empleados/registrar
     */
    public void registrar(Context ctx) {
        EmpleadoRegistroDTO dto = ctx.bodyAsClass(EmpleadoRegistroDTO.class);

        if (dto.getEmail() == null || dto.getEmail().trim().isEmpty()
                || dto.getContrasena() == null || dto.getContrasena().trim().isEmpty()
                || dto.getDniNie() == null || dto.getDniNie().trim().isEmpty()) {

            ctx.status(HttpCode.BAD_REQUEST)
               .json(ApiRespuesta.error("Email, contrasena y dniNie son campos obligatorios."));
            return;
        }

        String rol = (dto.getRol() != null && !dto.getRol().trim().isEmpty())
                ? dto.getRol().trim()
                : "empleado";

        boolean ok = empleadoService.registrarEmpleadoConAcceso(
                dto.getEmail().trim(),
                dto.getContrasena(),
                rol,
                dto.getNombre()         != null ? dto.getNombre().trim()         : "",
                dto.getPrimerApellido() != null ? dto.getPrimerApellido().trim() : "",
                dto.getDniNie().trim(),
                dto.getCargo()          != null ? dto.getCargo().trim()          : ""
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
     * PUT /api/empleados/{id}
     * Actualiza los datos del empleado (nombre, apellidos, dni, foto, etc.)
     */
    public void actualizar(Context ctx) {
        int idEmpleado;
        try {
            idEmpleado = Integer.parseInt(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            ctx.status(HttpCode.BAD_REQUEST).json(ApiRespuesta.error("ID de empleado no válido."));
            return;
        }

        Map<String, Object> datos = ctx.bodyAsClass(Map.class);
        if (datos == null || datos.isEmpty()) {
            ctx.status(HttpCode.BAD_REQUEST).json(ApiRespuesta.error("No se han enviado datos para actualizar."));
            return;
        }

        boolean ok = empleadoService.actualizarEmpleado(idEmpleado, datos);
        if (ok) {
            ctx.status(HttpCode.OK).json(ApiRespuesta.ok("Empleado actualizado correctamente."));
        } else {
            ctx.status(HttpCode.INTERNAL_SERVER_ERROR)
               .json(ApiRespuesta.error("No se pudo actualizar el empleado. Comprueba que el ID existe."));
        }
    }

    /**
     * PUT /api/empleados/{id}/rol
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