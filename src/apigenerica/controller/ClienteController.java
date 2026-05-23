/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.controller;

import apigenerica.model.ApiRespuesta;
import apigenerica.service.ClienteService;
import io.javalin.http.Context;
import io.javalin.http.HttpCode;
import java.util.Map;

/**
 * POST /api/store/clientes/registrar
 *
 * Ruta pública (está dentro de /api/store/, exenta de JWT en ApiGenerica).
 * Crea el usuario en erp_users (tipo=cliente) y la fila en clientes.
 *
 * Body JSON esperado:
 * {
 *   "email":     "juan@ejemplo.com",   (obligatorio)
 *   "password":  "mipassword",         (obligatorio, mín. 8 chars)
 *   "nombre":    "Juan",               (obligatorio)
 *   "apellido":  "García",             (opcional)
 *   "cif_nif":   "12345678Z",          (obligatorio, UNIQUE en BD)
 *   "telefono":  "600000000",          (opcional)
 *   "direccion": "Calle Falsa 123"     (opcional)
 * }
 */
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @SuppressWarnings("unchecked")
    public void registrar(Context ctx) {
        Map<String, String> body = ctx.bodyAsClass(Map.class);

        String email     = trim(body.get("email"));
        String password  = trim(body.get("password"));
        String nombre    = trim(body.get("nombre"));
        String apellido  = trim(body.getOrDefault("apellido", ""));
        String cifNif    = trim(body.get("cif_nif"));
        String telefono  = trim(body.getOrDefault("telefono", ""));
        String direccion = trim(body.getOrDefault("direccion", ""));

        // Validaciones básicas
        if (email.isEmpty() || password.isEmpty() || nombre.isEmpty() || cifNif.isEmpty()) {
            ctx.status(HttpCode.BAD_REQUEST)
               .json(ApiRespuesta.error("Los campos email, password, nombre y cif_nif son obligatorios."));
            return;
        }
        if (password.length() < 8) {
            ctx.status(HttpCode.BAD_REQUEST)
               .json(ApiRespuesta.error("La contraseña debe tener al menos 8 caracteres."));
            return;
        }

        boolean ok = clienteService.registrarClienteConAcceso(
                email, password, nombre, apellido, cifNif, telefono, direccion);

        if (ok) {
            ctx.status(HttpCode.CREATED)
               .json(ApiRespuesta.ok("Cuenta de cliente creada correctamente."));
        } else {
            ctx.status(HttpCode.CONFLICT)
               .json(ApiRespuesta.error("No se pudo crear la cuenta. El email o CIF/NIF ya están registrados."));
        }
    }

    private static String trim(String s) {
        return s != null ? s.trim() : "";
    }
}