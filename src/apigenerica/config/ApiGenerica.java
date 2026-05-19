package apigenerica.config;

import apigenerica.controller.AdminBdController;
import apigenerica.controller.AuthController;
import apigenerica.controller.BaseController;
import apigenerica.controller.ConfigController;
import apigenerica.controller.MetaController;
import apigenerica.controller.ModuloController;
import apigenerica.controller.RolController;
import apigenerica.dao.BaseDao;
import apigenerica.dao.MetaDao;
import apigenerica.dao.RolDao;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.excepciones.NoAutorizadoException;
import apigenerica.excepciones.RecursoNoEncontradoException;
import apigenerica.excepciones.ValidacionException;
import apigenerica.model.ApiRespuesta;
import apigenerica.dao.UsuarioDao;
import apigenerica.service.FicheroService;
import apigenerica.service.JwtService;
import apigenerica.service.MetaService;
import apigenerica.service.OrderService;
import apigenerica.service.ServicioCifrado;
import apigenerica.service.SqlService;
import apigenerica.service.ValidadorService;
import apigenerica.controller.LogController;
import logs.service.LogService;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.javalin.Javalin;

// NUEVO: imports necesarios para los endpoints de ficheros y la pagina de prueba
import apigenerica.controller.FicheroController;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.javalin.http.UploadedFile;

/**
 * Punto de entrada de la API generica del ERP.
 * Inicializa conexiones, servicios, controladores y endpoints.
 *
 * @author Grupo1
 */
public class ApiGenerica {

    public static void main(String[] args) {
        // ── Auto-limpieza: matar procesos anteriores colgados ─────────
        limpiarPuertoYProcesos(7000);

        // ── Inicializar conexion con MySQL ────────────────────────────
        // MODIFICADO: la URL de ConexionMysql se cambio de erp_sistema a mysql
        // para que HikariCP pueda arrancar aunque erp_sistema no exista todavia.
        // crearEstructuraERP() se encarga de crearla automaticamente.
        ConexionMysql.inicializar();

        // ── Inicializar conexion con Paradox ──────────────────────────
        ConexionParadox.inicializar();

        // ── Inicializar motor de logs ─────────────────────────────────
        LogService.inicializar();

        // ── Instanciar servicios (inyeccion de dependencias manual) ───
        MetaDao metaDao = new MetaDao();
        BaseDao baseDao = new BaseDao();
        ValidadorService validador = new ValidadorService(metaDao);
        SqlService sqlService = new SqlService(validador);

        // FicheroService usa db4o. Si el fichero esta bloqueado (proceso anterior colgado)
        // la API arranca sin el servicio de ficheros para no crashear todo el sistema.
        FicheroService ficheroService = null;
        try {
            ficheroService = new FicheroService();
        } catch (Exception e) {
            System.err.println("[API] AVISO: FicheroService no disponible (" + e.getMessage() + ")");
            System.err.println("[API] La API arranca sin soporte de ficheros. Cierra cualquier proceso Java anterior.");
        }

        MetaService metaService = new MetaService(metaDao, validador, sqlService, ficheroService);
        OrderService orderService = new OrderService(metaDao);
        JwtService jwtService = new JwtService();
        UsuarioDao authService = new UsuarioDao();
        ServicioCifrado cifrado = new ServicioCifrado();

        // ── Instanciar controladores ─────────────────────────────────
        BaseController baseCtrl = new BaseController(validador, metaService, baseDao, metaDao, orderService, ficheroService, cifrado);
        AuthController authCtrl = new AuthController(jwtService, authService);
        ConfigController configCtrl = new ConfigController();
        ModuloController moduloCtrl = new ModuloController();
        MetaController metaCtrl = new MetaController(metaService, validador, orderService, sqlService);
        RolController rolCtrl = new RolController(new RolDao());
        AdminBdController adminBdCtrl = new AdminBdController();
        LogController logCtrl = new LogController();

        // NUEVO: variables effectively-final para usar ficheroService dentro de lambdas.
        // Java exige que las variables capturadas en lambdas no se reasignen,
        // y ficheroService puede ser null si db4o falla, por eso se copia aqui.
        final FicheroService fs = ficheroService;
        final FicheroController ficheroCtrl = (fs != null) ? new FicheroController(fs) : null;

        // ── Crear servidor Javalin ───────────────────────────────────
        Javalin app = Javalin.create(config -> {
            // Habilitar CORS para que el frontend pueda consumir la API
            config.enableCorsForAllOrigins();
            // Logging de peticiones
            config.enableDevLogging();
            // NUEVO: limite de subida a 500 MB para soportar ficheros grandes.
            // Javalin por defecto solo acepta 1 MB; sin esto los ficheros >1 MB
            // devuelven 413 Payload Too Large antes de llegar al controller.
            config.maxRequestSize = 500_000_000L;
        }).start(7000);

        app.before(ctx -> {
            String path = ctx.path();
            // NUEVO: obtener metodo HTTP para filtrar OPTIONS
            String method = ctx.method();

            // NUEVO: las peticiones OPTIONS (preflight CORS) no llevan token
            // y deben pasar sin validacion o el navegador bloquea las peticiones cross-origin.
            if ("OPTIONS".equalsIgnoreCase(method)) {
                return;
            }

            // Rutas publicas que no requieren token:
            // /api/auth  → login y refresh de JWT
            // /api/store → uso interno
            // /test      → pagina de prueba de backups (sin autenticacion)
            // NUEVO: /backup → endpoints llamados desde la pagina de prueba;
            //        la pagina no gestiona JWT por lo que se excluyen aqui.
            if (path.startsWith("/api/auth")
                    || path.startsWith("/api/store")
                    || path.equals("/test")
                    || path.startsWith("/backup")) {
                return;
            }

            // Extraer token de las rutas protegidas
            String authHeader = ctx.header("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new NoAutorizadoException("Token no proporcionado.", null);
            }

            String token = authHeader.replace("Bearer ", "");

            try {
                // Validar el token y extraer claims
                DecodedJWT jwt = jwtService.verificarToken(token);

                // Inyectar datos del usuario en el contexto
                ctx.attribute("usuarioId", jwt.getClaim("id").asLong());
                ctx.attribute("usuarioRol", jwt.getClaim("rol").asString());

                // Rutas de roles: solo admin puede GESTIONAR roles,
                // pero cualquier usuario autenticado puede LEER sus permisos
                if (path.startsWith("/api/roles")) {
                    String rol = jwt.getClaim("rol").asString();
                    boolean esLecturaPermisos = "GET".equalsIgnoreCase(ctx.method())
                            && path.matches("/api/roles/[^/]+/permisos");
                    if (!esLecturaPermisos && !"admin".equalsIgnoreCase(rol)) {
                        throw new NoAutorizadoException("Solo el administrador puede gestionar roles.", null);
                    }
                }

                // Rutas de administracion de BD: solo admin
                if (path.startsWith("/api/admin")) {
                    String rol = jwt.getClaim("rol").asString();
                    if (!"admin".equalsIgnoreCase(rol)) {
                        throw new NoAutorizadoException("Solo el administrador puede administrar bases de datos.", null);
                    }
                }

                // Rutas de logs: solo admin
                if (path.startsWith("/api/logs")) {
                    String rol = jwt.getClaim("rol").asString();
                    if (!"admin".equalsIgnoreCase(rol)) {
                        throw new NoAutorizadoException("Solo el administrador puede consultar los logs.", null);
                    }
                }

            } catch (NoAutorizadoException e) {
                throw e;
            } catch (Exception e) {
                throw new NoAutorizadoException("Token invalido o expirado.", e);
            }
        });

        // ── Endpoints de roles y permisos ────────────────────────────
        app.get("/api/roles",                   ctx -> rolCtrl.listarRoles(ctx));
        app.post("/api/roles",                  ctx -> rolCtrl.crearRol(ctx));
        app.delete("/api/roles/{nombre}",       ctx -> rolCtrl.eliminarRol(ctx));
        app.get("/api/roles/{nombre}/permisos", ctx -> rolCtrl.obtenerPermisos(ctx));
        app.put("/api/roles/{nombre}/permisos", ctx -> rolCtrl.guardarPermiso(ctx));

        // ── Endpoints de logs (solo admin) ───────────────────────────
        app.get("/api/logs",  ctx -> logCtrl.listar(ctx));
        app.post("/api/logs", ctx -> logCtrl.registrar(ctx));

        // ── Endpoints de metadatos ────────────────────────────────────
        app.post("/api/metadata/tablas",                                  ctx -> metaCtrl.crearTabla(ctx));
        app.get("/api/metadata/tablas",                                   ctx -> metaCtrl.listarTablas(ctx));
        app.get("/api/metadata/tablas/{tabla}",                           ctx -> metaCtrl.obtenerEstructuraTabla(ctx));
        app.delete("/api/metadata/tablas/{tabla}",                        ctx -> metaCtrl.eliminarTabla(ctx));
        app.post("/api/metadata/tablas/{tabla}/columnas",                 ctx -> metaCtrl.agregarColumna(ctx));
        app.put("/api/metadata/tablas/{tabla}/columnas/{columna}",        ctx -> metaCtrl.modificarColumna(ctx));
        app.put("/api/metadata/tablas/{tabla}/columnas/{columna}/nombre", ctx -> metaCtrl.renombrarColumna(ctx));
        app.delete("/api/metadata/tablas/{tabla}/columnas/{columna}",     ctx -> metaCtrl.eliminarColumna(ctx));

        // ── Endpoints de autenticacion ───────────────────────────────
        app.post("/api/auth/login",   ctx -> authCtrl.login(ctx));
        app.post("/api/auth/refresh", ctx -> authCtrl.refresh(ctx));

        // ── Endpoints de configuracion ERP ───────────────────────────
        app.get("/api/erp/config", ctx -> configCtrl.getConfig(ctx));
        app.put("/api/erp/config", ctx -> configCtrl.updateConfig(ctx));

        // ── Endpoints de modulos ─────────────────────────────────────
        app.get("/api/erp/modulos",         ctx -> moduloCtrl.getAll(ctx));
        app.post("/api/erp/modulos",        ctx -> moduloCtrl.create(ctx));
        app.delete("/api/erp/modulos/{id}", ctx -> moduloCtrl.delete(ctx));

        // ── Endpoints de administracion de BD ────────────────────────
        app.get("/api/admin/bd",                                    ctx -> adminBdCtrl.listarBDs(ctx));
        app.post("/api/admin/bd",                                   ctx -> adminBdCtrl.crearBD(ctx));
        app.delete("/api/admin/bd/{nombre}",                        ctx -> adminBdCtrl.borrarBD(ctx));
        app.get("/api/admin/bd/{nombre}/tablas",                    ctx -> adminBdCtrl.listarTablas(ctx));
        app.post("/api/admin/bd/{nombre}/tablas",                   ctx -> adminBdCtrl.crearTabla(ctx));
        app.delete("/api/admin/bd/{nombre}/tablas/{tabla}",         ctx -> adminBdCtrl.borrarTabla(ctx));
        app.get("/api/admin/bd/{nombre}/tablas/{tabla}/estructura", ctx -> adminBdCtrl.describirTabla(ctx));
        app.get("/api/admin/bd/{nombre}/tablas/{tabla}/datos",      ctx -> adminBdCtrl.leerDatos(ctx));
        app.post("/api/admin/bd/{nombre}/tablas/{tabla}/datos",     ctx -> adminBdCtrl.insertarRegistro(ctx));
        app.post("/api/admin/bd/{nombre}/backup",                   ctx -> adminBdCtrl.crearBackup(ctx));
        app.get("/api/admin/bd/conexion",                           ctx -> adminBdCtrl.probarConexion(ctx));

        // ── Endpoints CRUD transaccionales ────────────────────────────
        app.post("/api/batch/insert",   ctx -> baseCtrl.insertTransaccional(ctx));
        app.put("/api/batch/update",    ctx -> baseCtrl.updateTransaccional(ctx));
        app.delete("/api/batch/delete", ctx -> baseCtrl.deleteTransaccional(ctx));

        // ── NUEVO: Endpoints de ficheros ──────────────────────────────
        // Deben ir ANTES de los CRUD genericos para que /api/ficheros no sea
        // interceptado por app.get("/api/{tabla}") como si fuera una tabla normal.

        // NUEVO: Subir un archivo. FicheroService decide automaticamente si va
        // a db4o (<=20 MB) o a disco cifrado (.enc) (>20 MB).
        // La condicion usa bytesOriginales.length en vez de file.getSize() porque
        // Javalin puede devolver -1 en getSize() para peticiones multipart.
        app.post("/api/ficheros/{tabla}", ctx -> {
            if (fs == null) {
                ctx.status(503).json(ApiRespuesta.error("Servicio de ficheros no disponible."));
                return;
            }
            UploadedFile file = ctx.uploadedFile("archivo");
            if (file == null) {
                ctx.status(400).json(ApiRespuesta.error("No se recibio ningun archivo (campo: 'archivo')."));
                return;
            }
            String uuid = UUID.randomUUID().toString();
            fs.guardar(uuid, ctx.pathParam("tabla"), file);
            // Releer metadatos de db4o para incluir tipoDetectado en la respuesta
            apigenerica.model.Fichero meta = fs.obtenerMetadatos(uuid);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("uuid", uuid);
            resp.put("tipoDetectado", meta != null && meta.getTipoDetectado() != null
                    ? meta.getTipoDetectado() : "");
            ctx.status(201).json(ApiRespuesta.ok(resp));
        });

        // NUEVO: Listar todos los ficheros del indice MySQL (erp_ficheros).
        // Muestra uuid, nombre, tipo detectado, peso, si esta en disco o db4o y fecha.
        app.get("/api/ficheros", ctx -> {
            List<Map<String, Object>> lista = new ArrayList<>();
            try (Connection conn = ConexionMysql.getConexion();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT * FROM erp_sistema.erp_ficheros ORDER BY fecha_subida DESC")) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("uuid",            rs.getString("uuid"));
                    row.put("nombre_original", rs.getString("nombre_original"));
                    row.put("mime_type",       rs.getString("mime_type"));
                    row.put("tipo_detectado",  rs.getString("tipo_detectado"));
                    row.put("tamano_bytes",    rs.getLong("tamano_bytes"));
                    row.put("esta_en_disco",   rs.getInt("esta_en_disco") == 1);
                    row.put("tabla_origen",    rs.getString("tabla_origen"));
                    row.put("fecha_subida",    rs.getString("fecha_subida"));
                    lista.add(row);
                }
            }
            ctx.json(ApiRespuesta.ok(lista));
        });

        // NUEVO: Metadatos de un fichero concreto leidos desde db4o.
        // Devuelve nombre, mime, tamano, tipoDetectado, ruta y enDisco.
        app.get("/api/ficheros/{uuid}/info", ctx -> {
            if (ficheroCtrl == null) {
                ctx.status(503).json(ApiRespuesta.error("Servicio de ficheros no disponible."));
                return;
            }
            ficheroCtrl.obtenerInfo(ctx);
        });

        // NUEVO: Descarga del contenido original.
        // FicheroService aplica decrypt+decompress de forma transparente —
        // el controller no sabe si el fichero estaba en disco o en db4o.
        app.get("/api/ficheros/{uuid}/descargar", ctx -> {
            if (ficheroCtrl == null) {
                ctx.status(503).json(ApiRespuesta.error("Servicio de ficheros no disponible."));
                return;
            }
            ficheroCtrl.descargar(ctx);
        });

        // NUEVO: Elimina el fichero de db4o, el .enc del disco (si aplica)
        // y la fila del indice MySQL (erp_ficheros).
        app.delete("/api/ficheros/{uuid}", ctx -> {
            if (fs == null) {
                ctx.status(503).json(ApiRespuesta.error("Servicio de ficheros no disponible."));
                return;
            }
            fs.eliminar(ctx.pathParam("uuid"));
            ctx.json(ApiRespuesta.ok("Fichero eliminado correctamente."));
        });

        // ── NUEVO: Pagina de prueba de backups ────────────────────────
        // Sirve test-backup.html desde la raiz del proyecto (junto a build.xml).
        // Acceso: http://localhost:7000/test
        // IMPORTANTE: el nombre del endpoint es /test pero el archivo en disco
        // es test-backup.html — son dos cosas distintas.
        // No requiere JWT — los endpoints /backup/* tampoco lo requieren.
        app.get("/test", ctx -> {
            java.nio.file.Path ruta = java.nio.file.Paths.get("test-backup.html");
            if (java.nio.file.Files.exists(ruta)) {
                ctx.contentType("text/html; charset=UTF-8");
                ctx.result(new String(java.nio.file.Files.readAllBytes(ruta),
                        java.nio.charset.StandardCharsets.UTF_8));
            } else {
                ctx.status(404).result("test-backup.html no encontrado. Colocalo en la raiz del proyecto (junto a build.xml).");
            }
        });

        // ── NUEVO: Endpoints de backup usados por la pagina de prueba ─
        // Sin JWT: la pagina test-backup.html no gestiona autenticacion.
        // Delegan en AdminBdController donde estan los 4 metodos nuevos:
        // verificarBackup, crearBackupGeneral, listarBackups, restaurarBackup.
        // IMPORTANTE: deben ir antes de los CRUD genericos igual que los de ficheros.
        app.get("/backup/verificar",  ctx -> adminBdCtrl.verificarBackup(ctx));
        app.post("/backup/crear",     ctx -> adminBdCtrl.crearBackupGeneral(ctx));
        app.get("/backup/listar",     ctx -> adminBdCtrl.listarBackups(ctx));
        app.post("/backup/restaurar", ctx -> adminBdCtrl.restaurarBackup(ctx));

        // ── Endpoints CRUD genericos (cualquier tabla) ───────────────
        // IMPORTANTE: Van al final para no interceptar las rutas especificas
        app.get("/api/{tabla}",         ctx -> baseCtrl.fetchTodo(ctx));
        app.get("/api/{tabla}/{id}",    ctx -> baseCtrl.fetchPorId(ctx));
        app.post("/api/{tabla}",        ctx -> baseCtrl.insert(ctx));
        app.put("/api/{tabla}/{id}",    ctx -> baseCtrl.update(ctx));
        app.delete("/api/{tabla}/{id}", ctx -> baseCtrl.delete(ctx));

        // ── Manejo global de excepciones ─────────────────────────────
        app.exception(ValidacionException.class, (e, ctx) ->
                ctx.status(400).json(ApiRespuesta.error(e.getMessage())));

        app.exception(RecursoNoEncontradoException.class, (e, ctx) ->
                ctx.status(404).json(ApiRespuesta.error(e.getMessage())));

        app.exception(NoAutorizadoException.class, (e, ctx) ->
                ctx.status(401).json(ApiRespuesta.error(e.getMessage())));

        app.exception(BaseDatosException.class, (e, ctx) ->
                ctx.status(500).json(ApiRespuesta.error(e.getMessage())));

        app.exception(Exception.class, (e, ctx) -> {
            System.err.println("Error no controlado: " + e.getMessage());
            e.printStackTrace();
            ctx.status(500).json(ApiRespuesta.error("Error interno del servidor."));
        });

        // ── Shutdown hook ────────────────────────────────────────────
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LogService.detener();
            ConexionMysql.cerrar();
            ConexionParadox.cerrar();
            System.out.println("[API] Conexiones cerradas correctamente.");
        }));

        System.out.println("===========================================");
        System.out.println("  API ERP Generica arrancada en :7000");
        System.out.println("===========================================");
    }

    /**
     * Mata automaticamente cualquier proceso que este usando el puerto indicado.
     * Evita el error "Port already in use" cuando NetBeans no cierra bien la JVM anterior.
     * Solo funciona en Windows (usa netstat + taskkill).
     */
    private static void limpiarPuertoYProcesos(int puerto) {
        System.out.println("[API] Comprobando si el puerto " + puerto + " esta libre...");
        try {
            Process netstat = new ProcessBuilder("netstat", "-ano")
                    .redirectErrorStream(true)
                    .start();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(netstat.getInputStream()));
            String linea;
            String patron = ":" + puerto;
            while ((linea = reader.readLine()) != null) {
                if (linea.contains(patron) && linea.contains("LISTENING")) {
                    String[] partes = linea.trim().split("\\s+");
                    String pid = partes[partes.length - 1];
                    System.out.println("[API] Puerto " + puerto + " ocupado por PID=" + pid + ". Terminando proceso...");
                    new ProcessBuilder("taskkill", "/F", "/PID", pid)
                            .redirectErrorStream(true).start().waitFor();
                    System.out.println("[API] PID=" + pid + " eliminado. Puerto " + puerto + " libre.");
                    Thread.sleep(1500);
                }
            }
            netstat.waitFor();
        } catch (Exception e) {
            System.err.println("[API] No se pudo liberar el puerto: " + e.getMessage());
        }
    }
}