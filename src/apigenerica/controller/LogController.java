package apigenerica.controller;

import apigenerica.model.ApiRespuesta;
import io.javalin.http.Context;
import logs.service.LogService;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST para el módulo de logs.
 *
 * Endpoints expuestos (todos requieren token JWT):
 *   GET  /api/logs              → todos los logs (más reciente primero)
 *   GET  /api/logs?q=texto      → filtro por cualquier campo
 *   POST /api/logs              → registrar log manual
 *
 * Solo el rol "admin" puede acceder (se valida en el before() de ApiGenerica).
 */
public class LogController {

    /**
     * GET /api/logs
     * Parámetro opcional: ?q=texto  (filtra por usuario, operación, tabla o descripción)
     */
    public void listar(Context ctx) {
        String filtro = ctx.queryParam("q");
        List<LogService.LogEntry> lista = (filtro != null && !filtro.trim().isEmpty())
                ? LogService.buscarComoEntradas(filtro)
                : LogService.obtenerTodosComoEntradas();
        ctx.json(ApiRespuesta.ok(lista));
    }

    /**
     * POST /api/logs
     * Body JSON: { "usuario":"...", "operacion":"...", "tabla":"...", "descripcion":"..." }
     * Permite registrar un log manualmente desde la propia API o desde un cliente externo.
     */
    @SuppressWarnings("unchecked")
    public void registrar(Context ctx) {
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String usuario     = body.getOrDefault("usuario",     "api");
        String operacion   = body.getOrDefault("operacion",   "INFO");
        String tabla       = body.getOrDefault("tabla",       "");
        String descripcion = body.getOrDefault("descripcion", "");

        if (descripcion.trim().isEmpty()) {
            ctx.status(400).json(ApiRespuesta.error("El campo 'descripcion' es obligatorio."));
            return;
        }

        LogService.registrar(usuario, operacion, tabla, descripcion);
        ctx.status(201).json(ApiRespuesta.ok("Log registrado."));
    }
}
