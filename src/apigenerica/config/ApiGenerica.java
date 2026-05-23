package apigenerica.config;

import apigenerica.controller.AdminBdController;
import apigenerica.backup.BackupController;
import apigenerica.backup.BackupOrchestrator;
import apigenerica.controller.AuthController;
import apigenerica.controller.BaseController;
import apigenerica.controller.ConfigController;
import apigenerica.controller.EmpleadoController;
import apigenerica.controller.ClienteController;
import apigenerica.service.ClienteService;
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
import apigenerica.service.EmpleadoService;
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
        // ── Auto-limpieza: matar procesos anteriores colgados 
        limpiarPuertoYProcesos(7000);

        // ── Inicializar conexion con MySQL 
        ConexionMysql.inicializar();

        // ── Inicializar conexion con Paradox 
        ConexionParadox.inicializar();

        // ── Inicializar motor de logs 
        LogService.inicializar();

        // ── Instanciar servicios (inyeccion de dependencias manual) 
        MetaDao metaDao = new MetaDao();
        BaseDao baseDao = new BaseDao();
        ValidadorService validador = new ValidadorService(metaDao);
        SqlService sqlService = new SqlService(validador);

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
        BackupOrchestrator backupOrchestrator = new BackupOrchestrator();
        LogController logCtrl = new LogController();
        EmpleadoController empleadoCtrl = new EmpleadoController(new EmpleadoService()); 
        ClienteController clienteCtrl = new ClienteController(new ClienteService());

        final FicheroService fs = ficheroService;
        final FicheroController ficheroCtrl = (fs != null) ? new FicheroController(fs) : null;

        // ── Crear servidor Javalin ───────────────────────────────────
        Javalin app = Javalin.create(config -> {
            config.enableCorsForAllOrigins();
            config.enableDevLogging();
            config.maxRequestSize = 500_000_000L;
        }).start(7000);

        app.before(ctx -> {
            String path = ctx.path();
            String method = ctx.method();

            if ("OPTIONS".equalsIgnoreCase(method)) {
                return;
            }

            if (path.startsWith("/api/auth")
                    || path.startsWith("/api/store")
                    || path.equals("/test")
                    || path.equals("/test-ficheros") 
                    || path.startsWith("/backup")) {
                return;
            }
            
            String authHeader = ctx.header("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new NoAutorizadoException("Token no proporcionado.", null);
            }

            String token = authHeader.replace("Bearer ", "");

            try {
                DecodedJWT jwt = jwtService.verificarToken(token);

                ctx.attribute("usuarioId", jwt.getClaim("id").asLong());
                ctx.attribute("usuarioRol", jwt.getClaim("rol").asString());

                if (path.startsWith("/api/roles")) {
                    String rol = jwt.getClaim("rol").asString();
                    boolean esLecturaPermisos = "GET".equalsIgnoreCase(ctx.method())
                            && path.matches("/api/roles/[^/]+/permisos");
                    if (!esLecturaPermisos && !"admin".equalsIgnoreCase(rol)) {
                        throw new NoAutorizadoException("Solo el administrador puede gestionar roles.", null);
                    }
                }

                if (path.startsWith("/api/admin")) {
                    String rol = jwt.getClaim("rol").asString();
                    if (!"admin".equalsIgnoreCase(rol)) {
                        throw new NoAutorizadoException("Solo el administrador puede administrar bases de datos.", null);
                    }
                }

                // NUEVO: solo admin puede registrar empleados
                if (path.startsWith("/api/empleados")) {
                    String rol = jwt.getClaim("rol").asString();
                    if (!"admin".equalsIgnoreCase(rol)) {
                        throw new NoAutorizadoException("Solo el administrador puede registrar empleados.", null);
                    }
                }

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

        // ── Alias /api/erp/roles y /api/erp/permisos (compatibilidad con el frontend) ─
        app.get("/api/erp/roles",               ctx -> rolCtrl.listarRoles(ctx));
        app.post("/api/erp/roles",              ctx -> rolCtrl.crearRol(ctx));
        app.get("/api/erp/permisos",            ctx -> rolCtrl.obtenerPermisosPorRolYTabla(ctx));
        app.put("/api/erp/permisos",            ctx -> rolCtrl.guardarPermiso(ctx));

        // ── Endpoints de Empleados ──────────────────────────────────────────────────
        app.post("/api/empleados/registrar", ctx -> empleadoCtrl.registrar(ctx));
        app.put("/api/empleados/{id}/rol",   ctx -> empleadoCtrl.cambiarRol(ctx));
        
        //Registro público de clientes (sin JWT)
        app.post("api/store/clientes/registrar", ctx -> clienteCtrl.registrar(ctx));

        // ── Incidencias públicas (sin JWT, desde la tienda) ──────────
        // La ruta /api/store/* ya está exenta de JWT (ver filtro antes).
        // Usamos insertEnTabla() para evitar depender del pathParam "tabla".
        app.post("/api/store/incidencias", ctx -> baseCtrl.insertEnTabla(ctx, "incidencias"));

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

        // ── Endpoints de relaciones ──────────────────────────────────
        app.get("/api/metadata/relaciones/{tablaId}", ctx -> metaCtrl.listarRelaciones(ctx));
        app.post("/api/metadata/relaciones",           ctx -> metaCtrl.crearRelacion(ctx));
        app.delete("/api/metadata/relaciones/{id}",    ctx -> metaCtrl.eliminarRelacion(ctx));

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
        app.get("/api/erp/tablas",          ctx -> moduloCtrl.getTablasByModulo(ctx));

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

        // ── Endpoints de ficheros ──────────────────────────────────
        if (ficheroCtrl != null) {
            app.post("/api/ficheros/{tabla}", ctx -> ficheroCtrl.subir(ctx));
            app.get("/api/ficheros", ctx -> ficheroCtrl.listar(ctx));
            app.get("/api/ficheros/{uuid}/info", ctx -> ficheroCtrl.obtenerInfo(ctx));
            app.get("/api/ficheros/{uuid}/descargar", ctx -> ficheroCtrl.descargar(ctx));
            app.delete("/api/ficheros/{uuid}", ctx -> ficheroCtrl.eliminar(ctx));
        } else {
            // Si db4o falló al inicio y el servicio es nulo, devolvemos 503 Service Unavailable en todas sus rutas
            io.javalin.http.Handler fallback = ctx -> ctx.status(503).json(ApiRespuesta.error("Servicio de ficheros no disponible."));
            app.post("/api/ficheros/{tabla}", fallback);
            app.get("/api/ficheros", fallback);
            app.get("/api/ficheros/{uuid}/info", fallback);
            app.get("/api/ficheros/{uuid}/descargar", fallback);
            app.delete("/api/ficheros/{uuid}", fallback);
        }

        // ── Pagina de prueba de backups ────────────────────────────
        app.get("/test", ctx -> {
            java.nio.file.Path ruta = java.nio.file.Paths.get("paradox-logs-diagnostico.html");
            if (java.nio.file.Files.exists(ruta)) {
                ctx.contentType("text/html; charset=UTF-8");
                ctx.result(new String(java.nio.file.Files.readAllBytes(ruta),
                        java.nio.charset.StandardCharsets.UTF_8));
            } else {
                ctx.status(404).result("paradox-logs-diagnostico.html no encontrado.");
            }
        });

        // ── Endpoints de backup (nuevo sistema: MySQL + db4o + Paradox + Logs → ZIP único) ──
        app.get("/backup/verificar",  ctx -> adminBdCtrl.verificarBackup(ctx));
        app.get("/backup/listar",     ctx -> adminBdCtrl.listarBackups(ctx));

        // POST /backup/crear → BackupOrchestrator: genera backup_erp_<ts>.zip con todo incluido
        app.post("/backup/crear", ctx -> {
            try {
                java.io.File resultado = backupOrchestrator.ejecutarBackupCompleto();
                java.util.Map<String, Object> datos = new java.util.LinkedHashMap<>();
                datos.put("nombre",  resultado.getName());
                datos.put("archivo", resultado.getAbsolutePath());
                datos.put("tamano",  resultado.length());
                datos.put("descripcion", "ZIP con MySQL (.sql) + db4o (.zip) + Paradox (dir) + Logs (.txt)");
                ctx.status(200).json(apigenerica.model.ApiRespuesta.ok(datos));
            } catch (Exception e) {
                ctx.status(500).json(apigenerica.model.ApiRespuesta.error("Error al crear backup: " + e.getMessage()));
            }
        });

        // POST /backup/restaurar?backup=NOMBRE&confirmacion=CONFIRMAR
        app.post("/backup/restaurar", ctx -> {
            String nombreBackup = ctx.queryParam("backup");
            String confirmacion = ctx.queryParam("confirmacion");
            if (nombreBackup == null || nombreBackup.trim().isEmpty()) {
                ctx.status(400).json(apigenerica.model.ApiRespuesta.error("Parámetro 'backup' requerido."));
                return;
            }
            if (!"CONFIRMAR".equals(confirmacion)) {
                ctx.status(400).json(apigenerica.model.ApiRespuesta.error("Requiere confirmacion=CONFIRMAR para restaurar."));
                return;
            }
            java.io.File backupFile = new java.io.File(apigenerica.backup.BackupConfig.getDirectorioBackups(), nombreBackup);
            if (!backupFile.exists()) {
                ctx.status(404).json(apigenerica.model.ApiRespuesta.error("Backup no encontrado: " + nombreBackup));
                return;
            }
            try {
                backupOrchestrator.restaurarBackupCompleto(backupFile);
                ctx.status(200).json(apigenerica.model.ApiRespuesta.ok("Restauración completada: " + nombreBackup));
            } catch (Exception e) {
                ctx.status(500).json(apigenerica.model.ApiRespuesta.error("Error al restaurar: " + e.getMessage()));
            }
        });

        // ── Alias /api/erp/backups (compatibilidad con el frontend) ─
        app.get("/api/erp/backups",            ctx -> adminBdCtrl.listarBackups(ctx));
        app.post("/api/erp/backups", ctx -> {
            try {
                java.io.File resultado = backupOrchestrator.ejecutarBackupCompleto();
                java.util.Map<String, Object> datos = new java.util.LinkedHashMap<>();
                datos.put("nombre",      resultado.getName());
                datos.put("archivo",     resultado.getAbsolutePath());
                datos.put("tamano",      resultado.length());
                datos.put("descripcion", "ZIP con MySQL (.sql) + db4o (.zip) + Paradox (dir) + Logs (.txt)");
                ctx.status(200).json(apigenerica.model.ApiRespuesta.ok(datos));
            } catch (Exception e) {
                ctx.status(500).json(apigenerica.model.ApiRespuesta.error("Error al crear backup: " + e.getMessage()));
            }
        });
        app.post("/api/erp/backups/restaurar", ctx -> {
            String nombreBackup = ctx.queryParam("backup");
            String confirmacion = ctx.queryParam("confirmacion");
            if (nombreBackup == null || nombreBackup.trim().isEmpty()) {
                ctx.status(400).json(apigenerica.model.ApiRespuesta.error("Parámetro 'backup' requerido."));
                return;
            }
            if (!"CONFIRMAR".equals(confirmacion)) {
                ctx.status(400).json(apigenerica.model.ApiRespuesta.error("Requiere confirmacion=CONFIRMAR para restaurar."));
                return;
            }
            java.io.File backupFile = new java.io.File(apigenerica.backup.BackupConfig.getDirectorioBackups(), nombreBackup);
            if (!backupFile.exists()) {
                ctx.status(404).json(apigenerica.model.ApiRespuesta.error("Backup no encontrado: " + nombreBackup));
                return;
            }
            try {
                backupOrchestrator.restaurarBackupCompleto(backupFile);
                ctx.status(200).json(apigenerica.model.ApiRespuesta.ok("Restauración completada: " + nombreBackup));
            } catch (Exception e) {
                ctx.status(500).json(apigenerica.model.ApiRespuesta.error("Error al restaurar: " + e.getMessage()));
            }
        });
        app.delete("/api/erp/backups/{nombre}",  ctx -> {
            ctx.status(200).json(apigenerica.model.ApiRespuesta.ok("Eliminación de backup delegada."));
        });

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