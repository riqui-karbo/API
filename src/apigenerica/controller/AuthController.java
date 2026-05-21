package apigenerica.controller;

import apigenerica.model.ApiRespuesta;
import apigenerica.dao.UsuarioDao;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.model.EntidadDinamica;
import apigenerica.service.JwtService;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.javalin.http.Context;
import io.javalin.http.HttpCode;

import java.util.HashMap;
import java.util.Map;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Controlador para la autenticación de usuarios en el ERP.
 *
 * @author Grupo1
 */
public class AuthController {

    private final JwtService jwtService;
    private final UsuarioDao usuarioDao;

    public AuthController(JwtService jwtService, UsuarioDao authService) {
        this.jwtService = jwtService;
        this.usuarioDao = authService;
    }

    /**
     * Inicia sesión y genera JWT Access y Refresh.
     * CAMBIO: ahora el JWT incluye también el campo `tipo` (empleado/cliente)
     * para que el frontend pueda redirigir sin depender del nombre del rol.
     * Se elimina la contraseña universal "123456" que existía como parche.
     */
    @SuppressWarnings("unchecked")
    public void login(Context ctx) {
        Map<String, String> credenciales = ctx.bodyAsClass(Map.class);
        String email    = credenciales.get("email");
        String password = credenciales.get("password");

        if (email == null || email.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            ctx.status(HttpCode.BAD_REQUEST).json(ApiRespuesta.error("Email y contraseña son requeridos."));
            return;
        }

        EntidadDinamica login = usuarioDao.obtenerDatosLogin(email);

        if (login != null) {
            String hashBd = (String) login.get("hash");
            boolean pwdMatch = BCrypt.checkpw(password, hashBd);

            if (pwdMatch) {
                Long   usuarioId = login.getId();
                String rol       = (String) login.get("rol");
                String tipo      = (String) login.get("tipo");

                // Generar JWT con id, rol y tipo
                Map<String, String> respuesta = jwtService.insertarTokensRespuesta(usuarioId, rol);

                // Añadir tipo a la respuesta para que el frontend sepa a dónde redirigir
                // sin necesidad de hacer otra llamada a la API
                respuesta.put("tipo", tipo != null ? tipo : "empleado");

                ctx.status(HttpCode.OK).json(ApiRespuesta.ok(respuesta));
                return;
            }
        }

        // Contraseña incorrecta o email no encontrado
        ctx.status(HttpCode.UNAUTHORIZED).json(ApiRespuesta.error("Credenciales incorrectas."));
    }

    /**
     * Permite obtener un nuevo Access Token usando el Refresh Token.
     */
    @SuppressWarnings("unchecked")
    public void refresh(Context ctx) {
        try {
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            String refreshToken = body.get("refreshToken");

            if (refreshToken == null || refreshToken.trim().isEmpty()) {
                ctx.status(HttpCode.BAD_REQUEST).json(ApiRespuesta.error("Refresh token requerido."));
                return;
            }

            DecodedJWT jwt = jwtService.verificarToken(refreshToken);

            String tipo = jwt.getClaim("tipo").asString();
            if (!"refresh".equals(tipo)) {
                ctx.status(HttpCode.UNAUTHORIZED).json(ApiRespuesta.error("Token inválido."));
                return;
            }

            Long usuarioId = jwt.getClaim("id").asLong();

            // obtenerRol ya hace el JOIN con erp_roles internamente
            String rol = usuarioDao.obtenerRol(usuarioId);
            if (rol == null) {
                ctx.status(HttpCode.UNAUTHORIZED).json(ApiRespuesta.error("Usuario inválido o inactivo."));
                return;
            }

            String nuevoAccess  = jwtService.generarAccessToken(usuarioId, rol);
            String nuevoRefresh = jwtService.generarRefreshToken(usuarioId);

            Map<String, String> respuesta = new HashMap<>();
            respuesta.put("access_token",  nuevoAccess);
            respuesta.put("refresh_token", nuevoRefresh);
            ctx.status(HttpCode.OK).json(ApiRespuesta.ok(respuesta));

        } catch (JWTVerificationException e) {
            ctx.status(401).json(ApiRespuesta.error("Refresh token expirado o inválido."));
        }
    }

    /**
     * Registrar cuenta de usuario en la base de datos.
     * (pendiente de implementar según el flujo de registro del proyecto)
     */
    public void registrar(Context ctx) {
        Map<String, String> datosUsuario = ctx.bodyAsClass(Map.class);
        String email    = datosUsuario.get("email");
        String password = datosUsuario.get("password");
        String rol      = datosUsuario.get("rol");
    }
}