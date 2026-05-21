package apigenerica.controller;

import apigenerica.model.ApiRespuesta;
import apigenerica.dao.UsuarioDao;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.excepciones.RecursoNoEncontradoException;
import apigenerica.excepciones.ValidacionException;
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
     * @param ctx Contexto de la petición HTTP
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
     * @param ctx Contexto de la petición HTTP 
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
     * Obtener un registro de la tabla de cuentas de usuario
     * 
     * @param ctx Contexto de la petición HTTP 
     */
    public void obtenerUsuario(Context ctx) {
        Long id = ctx.pathParamAsClass("id", Long.class).get();

        EntidadDinamica usuario = usuarioDao.obtenerPorId(id);
        if (usuario == null) {
            ctx.status(HttpCode.NOT_FOUND).json(ApiRespuesta.error("Usuario no encontrado."));
            return;
        }

        ctx.status(HttpCode.OK).json(ApiRespuesta.ok(usuario));
    }
    
    /**
     * Registrar cuenta de usuario en la base de datos
     *
     * @param ctx Contexto de la petición HTTP
     */
    public void registrar(Context ctx) {
        Map<String, Object> datosUsuario = ctx.bodyAsClass(Map.class);
        String email = (String) datosUsuario.get("email");
        String password = (String) datosUsuario.get("password");
        Object rol = datosUsuario.get("rol");

        if (email == null || password == null || rol == null) {
            throw new ValidacionException("Todos los campos (email, password, rol) son obligatorios.");
        }

        int rolId = Integer.parseInt(rol.toString());
        // Encriptar la contraseña
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());

        // Guardar en DB
        long id = usuarioDao.crearUsuario(email, passwordHash, rolId);

        ctx.status(HttpCode.CREATED).json(ApiRespuesta.ok("Usuario registrado con ID: " + id));
    }

    /**
     * Modificar los campos de un usuario
     * 
     * @param ctx Contexto de la petición HTTP 
     */
    public void modificarUsuario(Context ctx) {
        Long id = ctx.pathParamAsClass("id", Long.class).get();
        Map<String, Object> datos = ctx.bodyAsClass(Map.class);

        // Buscar el usuario actual
        EntidadDinamica usuarioActual = usuarioDao.obtenerPorId(id);
        if (usuarioActual == null) {
            throw new RecursoNoEncontradoException("Usuario no encontrado.");
        }

        // Si el dato viene en el body, se utiliza; si no, se utiliza el de la db
        String email = datos.containsKey("email")
                ? (String) datos.get("email") : (String) usuarioActual.get("email");
        int rolId;
        if (datos.containsKey("rol")) {
            rolId = Integer.parseInt(datos.get("rol").toString());
        } else {
            rolId = (int) usuarioActual.get("rol_id"); 
        }
        int activo;
        if (datos.containsKey("activo")) {
            Object val = datos.get("activo");
            activo = (val instanceof Boolean) ? ((boolean) val ? 1 : 0) : Integer.parseInt(val.toString());
        } else {
            activo = (int) usuarioActual.get("activo");
        }

        // Actualizar datos
        usuarioDao.actualizarUsuario(id, email, rolId, activo);
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok("Usuario actualizado."));
    }

    /**
     * Borrar cuenta de la base de datos
     *
     * @param ctx Contexto de la petición HTTP 
     */
    public void eliminar(Context ctx) {
        Long id = ctx.pathParamAsClass("id", Long.class).get();

        usuarioDao.eliminarUsuario(id);
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok("Usuario eliminado."));
    }
}