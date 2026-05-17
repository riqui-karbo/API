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
// NUEVO: imports para la página de prueba y los endpoints de ficheros
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
 * Punto de entrada de la API genérica del ERP. Inicializa conexiones,
 * servicios, controladores y endpoints.
 *
 * @author Grupo1
 */
public class ApiGenerica {

    public static void main(String[] args) {
// ── Auto-limpieza: matar procesos anteriores colgados ─────────
        limpiarPuertoYProcesos(7000);
        // ── Inicializar conexión con MySQL ────────────────────────────
        ConexionMysql.inicializar();

        // ── Inicializar conexión con Paradox ──────────────────────────
        ConexionParadox.inicializar();

        // ── Inicializar motor de logs ─────────────────────────────────
        LogService.inicializar();

        // ── Instanciar servicios (inyección de dependencias manual) ───
        MetaDao metaDao = new MetaDao();
        BaseDao baseDao = new BaseDao();
        ValidadorService validador = new ValidadorService(metaDao);
        SqlService sqlService = new SqlService(validador);

        // FicheroService usa db4o. Si el fichero está bloqueado (proceso anterior colgado)
        // la API arranca sin el servicio de ficheros para no crashear todo el sistema.
        FicheroService ficheroService = null;
        try {
            ficheroService = new FicheroService();
        } catch (Exception e) {
            System.err.println("[API] AVISO: FicheroService no disponible ( " + e.getMessage() + ") ");
            System.err.println("[API] La API arranca sin soporte de ficheros. Cierra cualquier proceso Java anterior. ");
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

        // NUEVO: variable effectively-final necesaria para usar ficheroService dentro
        // de lambdas. Java exige que las variables capturadas en lambdas no se reasignen,
        // y ficheroService puede ser null si db4o falla, por eso se copia aquí.
        final FicheroService fs = ficheroService;
        final FicheroController ficheroCtrl = (fs != null) ? new FicheroController(fs) : null;

        // ── Crear servidor Javalin ───────────────────────────────────
        Javalin app = Javalin.create(config -> {
            // Habilitar CORS para que el frontend pueda consumir la API
            config.enableCorsForAllOrigins();
            // Logging de peticiones
            config.enableDevLogging();
            // NUEVO: aumentar el límite de subida a 500 MB para soportar ficheros grandes
            config.maxRequestSize = 500_000_000L;
        }).start(7000);

        app.before(ctx -> {
            String path = ctx.path();

            if (path.startsWith("/api/auth ") || path.startsWith("/api/store ") || path.equals("/test")) {
                return; // Rutas públicas: no requieren token
            }

            // Extraer token de las rutas protegidas
            String authHeader = ctx.header("Authorization ");
            if (authHeader == null || !authHeader.startsWith("Bearer  ")) {
                throw new NoAutorizadoException("Token no proporcionado. ", null);
            }

            String token = authHeader.replace("Bearer  ", " ");

            try {
                // Validar el token y extraer claims
                DecodedJWT jwt = jwtService.verificarToken(token);

                // Inyectar datos del usuario en el contexto
                ctx.attribute("usuarioId ", jwt.getClaim("id ").asLong());
                ctx.attribute("usuarioRol ", jwt.getClaim("rol ").asString());

                // Rutas de roles: solo admin puede GESTIONAR roles,
                // pero cualquier usuario autenticado puede LEER sus permisos
                if (path.startsWith("/api/roles ")) {
                    String rol = jwt.getClaim("rol ").asString();
                    boolean esLecturaPermisos = "GET ".equalsIgnoreCase(ctx.method())
                            && path.matches("/api/roles/[^/]+/permisos ");
                    if (!esLecturaPermisos && !"admin ".equalsIgnoreCase(rol)) {
                        throw new NoAutorizadoException("Solo el administrador puede gestionar roles. ", null);
                    }
                }

                // Rutas de administracion de BD: solo admin
                if (path.startsWith("/api/admin ")) {
                    String rol = jwt.getClaim("rol ").asString();
                    if (!"admin ".equalsIgnoreCase(rol)) {
                        throw new NoAutorizadoException("Solo el administrador puede administrar bases de datos. ", null);
                    }
                }

                // Rutas de logs: solo admin
                if (path.startsWith("/api/logs ")) {
                    String rol = jwt.getClaim("rol ").asString();
                    if (!"admin ".equalsIgnoreCase(rol)) {
                        throw new NoAutorizadoException("Solo el administrador puede consultar los logs. ", null);
                    }
                }

            } catch (NoAutorizadoException e) {
                throw e;
            } catch (Exception e) {
                throw new NoAutorizadoException("Token inválido o expirado. ", e);
            }
        });

        // ── Endpoints de roles y permisos ────────────────────────────
        app.get("/api/roles ", ctx -> rolCtrl.listarRoles(ctx));
        app.post("/api/roles ", ctx -> rolCtrl.crearRol(ctx));
        app.delete("/api/roles/{nombre} ", ctx -> rolCtrl.eliminarRol(ctx));
        app.get("/api/roles/{nombre}/permisos ", ctx -> rolCtrl.obtenerPermisos(ctx));
        app.put("/api/roles/{nombre}/permisos ", ctx -> rolCtrl.guardarPermiso(ctx));

        // ── Endpoints de logs (solo admin) ───────────────────────────
        app.get("/api/logs ", ctx -> logCtrl.listar(ctx));
        app.post("/api/logs ", ctx -> logCtrl.registrar(ctx));

        // ── Endpoints de metadatos ──────────────
        // Crear tablas
        app.post("/api/metadata/tablas ", ctx -> metaCtrl.crearTabla(ctx));
        // Obtener metadatos (lista de nombres) de todas las tablas
        app.get("/api/metadata/tablas ", ctx -> metaCtrl.listarTablas(ctx));
        // Obtener los metadatos de una tabla
        app.get("/api/metadata/tablas/{tabla} ", ctx -> metaCtrl.obtenerEstructuraTabla(ctx));
        // Eliminar una tabla
        app.delete("/api/metadata/tablas/{tabla} ", ctx -> metaCtrl.eliminarTabla(ctx));
        // Añadir columnas a una tabla
        app.post("/api/metadata/tablas/{tabla}/columnas ", ctx -> metaCtrl.agregarColumna(ctx));
        // Modificar las columnas de una tabla
        app.put("/api/metadata/tablas/{tabla}/columnas/{columna} ", ctx -> metaCtrl.modificarColumna(ctx));
        // Renombrar una columna
        app.put("/api/metadata/tablas/{tabla}/columnas/{columna}/nombre ", ctx -> metaCtrl.renombrarColumna(ctx));
        // Eliminar columnas de una tabla
        app.delete("/api/metadata/tablas/{tabla}/columnas/{columna} ", ctx -> metaCtrl.eliminarColumna(ctx));

        // ── Endpoints de autenticación ───────────────────────────────
        app.post("/api/auth/login ", ctx -> authCtrl.login(ctx));
        app.post("/api/auth/refresh ", ctx -> authCtrl.refresh(ctx));

        // ── Endpoints de configuración ERP ───────────────────────────
        app.get("/api/erp/config ", ctx -> configCtrl.getConfig(ctx));
        app.put("/api/erp/config ", ctx -> configCtrl.updateConfig(ctx));

        // ── Endpoints de módulos ─────────────────────────────────────
        app.get("/api/erp/modulos ", ctx -> moduloCtrl.getAll(ctx));
        app.post("/api/erp/modulos ", ctx -> moduloCtrl.create(ctx));
        app.delete("/api/erp/modulos/{id} ", ctx -> moduloCtrl.delete(ctx));

        // ── Endpoints de administración de BD ────────────────────────
        app.get("/api/admin/bd ", ctx -> adminBdCtrl.listarBDs(ctx));
        app.post("/api/admin/bd ", ctx -> adminBdCtrl.crearBD(ctx));
        app.delete("/api/admin/bd/{nombre} ", ctx -> adminBdCtrl.borrarBD(ctx));
        app.get("/api/admin/bd/{nombre}/tablas ", ctx -> adminBdCtrl.listarTablas(ctx));
        app.post("/api/admin/bd/{nombre}/tablas ", ctx -> adminBdCtrl.crearTabla(ctx));
        app.delete("/api/admin/bd/{nombre}/tablas/{tabla} ", ctx -> adminBdCtrl.borrarTabla(ctx));
        app.get("/api/admin/bd/{nombre}/tablas/{tabla}/estructura ", ctx -> adminBdCtrl.describirTabla(ctx));
        app.get("/api/admin/bd/{nombre}/tablas/{tabla}/datos ", ctx -> adminBdCtrl.leerDatos(ctx));
        app.post("/api/admin/bd/{nombre}/tablas/{tabla}/datos ", ctx -> adminBdCtrl.insertarRegistro(ctx));
        app.post("/api/admin/bd/{nombre}/backup ", ctx -> adminBdCtrl.crearBackup(ctx));
        app.get("/api/admin/bd/conexion ", ctx -> adminBdCtrl.probarConexion(ctx));

        // ── Endpoints CRUD transaccionales ─────────────────────────────────────
        app.post("/api/batch/insert ", ctx -> baseCtrl.insertTransaccional(ctx));
        app.put("/api/batch/update ", ctx -> baseCtrl.updateTransaccional(ctx));
        app.delete("/api/batch/delete ", ctx -> baseCtrl.deleteTransaccional(ctx));

        // NUEVO: Endpoints de ficheros ─────────────────────────────────
        // Deben ir ANTES de los CRUD genéricos para que /api/ficheros no sea
        // interceptado por app.get("/api/{tabla}") como si fuera una tabla normal.
        // NUEVO: Sirve el test.html desde la raíz del proyecto (donde está build.xml).
        // Acceso: http://localhost:7000/test
        app.get("/test", ctx -> {
            java.nio.file.Path ruta = java.nio.file.Paths.get("test.html");
            if (java.nio.file.Files.exists(ruta)) {
                ctx.contentType("text/html; charset=UTF-8");
                ctx.result(new String(java.nio.file.Files.readAllBytes(ruta), java.nio.charset.StandardCharsets.UTF_8));
            } else {
                ctx.status(404).result("test.html no encontrado. Colocalo en la raiz del proyecto (junto a build.xml).");
            }
        });

        // NUEVO: Subir un archivo → FicheroService decide si va a db4o o disco cifrado
        app.post("/api/ficheros/{tabla}", ctx -> {
            if (fs == null) {
                ctx.status(503).json(ApiRespuesta.error("Servicio de ficheros no disponible."));
                return;
            }
            UploadedFile file = ctx.uploadedFile("archivo");
            if (file == null) {
                ctx.status(400).json(ApiRespuesta.error("No se recibió ningún archivo (campo: 'archivo')."));
                return;
            }
            String uuid = UUID.randomUUID().toString();
            fs.guardar(uuid, ctx.pathParam("tabla"), file);
            // Releer metadatos de db4o para incluir el tipoDetectado en la respuesta
            apigenerica.model.Fichero meta = fs.obtenerMetadatos(uuid);
            Map resp = new LinkedHashMap<>();
            resp.put("uuid", uuid);
            resp.put("tipoDetectado", meta != null && meta.getTipoDetectado() != null
                    ? meta.getTipoDetectado() : "");
            ctx.status(201).json(ApiRespuesta.ok(resp));
        });

        // NUEVO: Listar todos los ficheros registrados en el índice MySQL (erp_ficheros).
        // Muestra uuid, nombre, tipo detectado, peso, si está en disco o db4o, y fecha.
        app.get("/api/ficheros", ctx -> {
            List<Map<String, Object>> lista = new ArrayList<>();
            try (Connection conn = ConexionMysql.getConexion(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(
                    "SELECT * FROM erp_sistema.erp_ficheros ORDER BY fecha_subida DESC")) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("uuid", rs.getString("uuid"));
                    row.put("nombre_original", rs.getString("nombre_original"));
                    row.put("mime_type", rs.getString("mime_type"));
                    row.put("tipo_detectado", rs.getString("tipo_detectado"));
                    row.put("tamano_bytes", rs.getLong("tamano_bytes"));
                    row.put("esta_en_disco", rs.getInt("esta_en_disco") == 1);
                    row.put("tabla_origen", rs.getString("tabla_origen"));
                    row.put("fecha_subida", rs.getString("fecha_subida"));
                    lista.add(row);
                }
            }
            ctx.json(ApiRespuesta.ok(lista));
        });

        // NUEVO: Metadatos de un fichero concreto leídos desde db4o (nombre, mime, tamaño, fecha)
        app.get("/api/ficheros/{uuid}/info", ctx -> {
            if (ficheroCtrl == null) {
                ctx.status(503).json(ApiRespuesta.error("Servicio de ficheros no disponible."));
                return;
            }
            ficheroCtrl.obtenerInfo(ctx);
        });

        // NUEVO: Descarga del contenido original (descifra + descomprime de forma transparente)
        app.get("/api/ficheros/{uuid}/descargar", ctx -> {
            if (ficheroCtrl == null) {
                ctx.status(503).json(ApiRespuesta.error("Servicio de ficheros no disponible."));
                return;
            }
            ficheroCtrl.descargar(ctx);
        });

        // NUEVO: Elimina el objeto de db4o y, si estaba en disco, borra también el .enc
        app.delete("/api/ficheros/{uuid}", ctx -> {
            if (fs == null) {
                ctx.status(503).json(ApiRespuesta.error("Servicio de ficheros no disponible."));
                return;
            }
            fs.eliminar(ctx.pathParam("uuid"));
            ctx.json(ApiRespuesta.ok("Fichero eliminado correctamente."));
        });

        // ── Endpoints CRUD genéricos (cualquier tabla) ───────────────
        // IMPORTANTE: Van al final para no interceptar las rutas específicas
        app.get("/api/{tabla} ", ctx -> baseCtrl.fetchTodo(ctx));
        app.get("/api/{tabla}/{id} ", ctx -> baseCtrl.fetchPorId(ctx));
        app.post("/api/{tabla} ", ctx -> baseCtrl.insert(ctx));
        app.put("/api/{tabla}/{id} ", ctx -> baseCtrl.update(ctx));
        app.delete("/api/{tabla}/{id} ", ctx -> baseCtrl.delete(ctx));

        // ── Manejo global de excepciones ─────────────────────────────
        app.exception(ValidacionException.class, (e, ctx)
                -> ctx.status(400).json(ApiRespuesta.error(e.getMessage())));

        app.exception(RecursoNoEncontradoException.class, (e, ctx)
                -> ctx.status(404).json(ApiRespuesta.error(e.getMessage())));

        app.exception(NoAutorizadoException.class, (e, ctx)
                -> ctx.status(401).json(ApiRespuesta.error(e.getMessage())));

        app.exception(BaseDatosException.class, (e, ctx)
                -> ctx.status(500).json(ApiRespuesta.error(e.getMessage())));

        app.exception(Exception.class, (e, ctx) -> {
            System.err.println("Error no controlado:  " + e.getMessage());
            e.printStackTrace();
            ctx.status(500).json(ApiRespuesta.error("Error interno del servidor. "));
        });

        // ── Shutdown hook ────────────────────────────────────────────
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LogService.detener();
            ConexionMysql.cerrar();
            ConexionParadox.cerrar();
            System.out.println("[API] Conexiones cerradas correctamente. ");
        }));

        System.out.println("=========================================== ");
        System.out.println("  API ERP Genérica arrancada en :7000 ");
        System.out.println("=========================================== ");
    }

    /**
     * Mata automaticamente cualquier proceso que este usando el puerto
     * indicado. Evita el error "Port already in use" cuando NetBeans no cierra
     * bien la JVM anterior. Solo funciona en Windows (usa netstat + taskkill).
     */
    private static void limpiarPuertoYProcesos(int puerto) {
        System.out.println("[API] Comprobando si el puerto  " + puerto + " esta libre... ");
        try {
            Process netstat = new ProcessBuilder("netstat ", "-ano ")
                    .redirectErrorStream(true)
                    .start();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(netstat.getInputStream()));
            String linea;
            String patron = ": " + puerto + "  ";
            while ((linea = reader.readLine()) != null) {
                if (linea.contains(patron) && linea.contains("LISTENING ")) {
                    String[] partes = linea.trim().split("\\s+ ");
                    String pid = partes[partes.length - 1];
                    System.out.println("[API] Puerto  " + puerto + " ocupado por PID= " + pid + ". Terminando proceso... ");
                    new ProcessBuilder("taskkill ", "/F ", "/PID ", pid)
                            .redirectErrorStream(true).start().waitFor();
                    System.out.println("[API] PID= " + pid + " eliminado. Puerto  " + puerto + " libre. ");
                    Thread.sleep(1500); // Esperar a que el SO libere el socket
                }
            }
            netstat.waitFor();
        } catch (Exception e) {
            System.err.println("[API] No se pudo liberar el puerto:  " + e.getMessage());
        }
    }
}
