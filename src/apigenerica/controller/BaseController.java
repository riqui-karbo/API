package apigenerica.controller;

import apigenerica.TipoDatoMapper;
import apigenerica.config.AppConfig;
import apigenerica.config.ConexionMysql;
import logs.dao.ParadoxWorker;
import apigenerica.dao.BaseDao;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import apigenerica.dao.MetaDao;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.excepciones.RecursoNoEncontradoException;
import apigenerica.excepciones.ValidacionException;
import apigenerica.model.ApiRespuesta;
import apigenerica.model.ColumnaConfig;
import apigenerica.model.EntidadDinamica;
import apigenerica.model.RelacionConfig;
import apigenerica.model.TablaConfig;
import apigenerica.service.EmpleadoService;
import apigenerica.service.FicheroService;
import apigenerica.service.MetaService;
import apigenerica.service.OrderService;
import apigenerica.service.ServicioCifrado;
import apigenerica.service.ValidadorService;
import io.javalin.http.Context;
import io.javalin.http.HttpCode;
import io.javalin.http.UploadedFile;
import logs.service.LogService;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controlador genérico CRUD para cualquier tabla de la base de datos.
 * Usa los metadatos almacenados en MySQL para mapear los resultados dinámicamente.
 *
 * Características:
 * - CRUD completo con soporte para filtros, paginación y ordenación
 * - Soporte para relaciones (JOINs) mediante parámetro "include"
 * - Gestión de archivos (db4o o disco) para columnas tipo ARCHIVO
 * - Datos sensibles cifrados en Paradox (separados de MySQL)
 * - Operaciones transaccionales batch para múltiples tablas
 * - Registro de auditoría mediante LogService
 *
 * @author Grupo1
 */
public class BaseController {

    private final ValidadorService validador;
    private final MetaService metaService;
    private final BaseDao baseDao;
    private final MetaDao metaDao;
    private final OrderService orderService;
    private final FicheroService ficheroService;
    private final ServicioCifrado cifrado;
    private final EmpleadoService empleadoService;

    /**
     * Constructor con inyección de dependencias manual.
     * @param validador Servicio de validaciones de entrada
     * @param metaService Servicio de gestión de metadatos
     * @param baseDao DAO para operaciones CRUD básicas
     * @param metaDao DAO para acceso a metadatos
     * @param orderService Servicio para ordenar tablas por dependencias FK
     * @param ficheroService Servicio para gestión de archivos
     * @param cifrado Servicio para cifrado de datos sensibles
     */
    public BaseController(ValidadorService validador, MetaService metaService,
            BaseDao baseDao, MetaDao metaDao, OrderService orderService,
            FicheroService ficheroService, ServicioCifrado cifrado) {
        this.validador = validador;
        this.metaService = metaService;
        this.baseDao = baseDao;
        this.metaDao = metaDao;
        this.orderService = orderService;
        this.ficheroService = ficheroService;
        this.cifrado = cifrado;
        this.empleadoService = new EmpleadoService();
    }

    // ═══════════════════════════════════════════════════════════════════
    // READ: Obtener registros (con soporte para filtros, paginación, JOINs)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Endpoint: GET /api/{tabla}
     * Obtiene una lista paginada de registros de una tabla.
     *
     * Parámetros de consulta soportados:
     * - Filtros: ?campo=valor o ?campo__gt=valor (gt, lt, gte, lte, contains)
     * - Paginación: ?limit=20&offset=0
     * - Ordenación: ?sort=nombre&order=ASC
     * - Relaciones: ?include=tabla1,tabla2 (para JOINs)
     *
     * Headers de respuesta:
     * - X-Total-Count: Total de registros (sin paginar)
     * - X-Total-Pages: Total de páginas
     * - X-Current-Page: Página actual
     *
     * @param ctx Contexto de la petición HTTP
     */
    public void fetchTodo(Context ctx) {
        // ── Extraer parámetros de la URL ─────────────────────────────
        String tabla = ctx.pathParam("tabla");
        validador.validarNombre(tabla);

        // Filtros dinámicos: ignorar parámetros de control
        Map<String, String> filtros = new HashMap<>();
        List<String> controlParams = Arrays.asList("limit", "offset", "sort", "order", "include");
        ctx.queryParamMap().forEach((key, values) -> {
            if (!controlParams.contains(key.toLowerCase()) && !values.isEmpty()) {
                filtros.put(key, values.get(0));
            }
        });

        // Parámetros de paginación y ordenación
        String includes = ctx.queryParam("include");
        int limite = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20);
        int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);
        String sort = ctx.queryParam("sort");
        String order = ctx.queryParam("order");

        // ── Cargar metadatos de la tabla ─────────────────────────────
        TablaConfig config = metaService.getConfiguracion(tabla);
        List<ColumnaConfig> columnas = config.getColumnas() != null
                ? config.getColumnas() : new ArrayList<>();

        // Procesar relaciones (JOINs) si se solicitan
        List<RelacionConfig> relaciones = metaService.getRelaciones(tabla, includes);
        Map<String, List<ColumnaConfig>> colsHijas = obtenerColumnasHijas(relaciones);

        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_CLIENTE)) {
            // Calcular paginación para headers
            long totalRegistros = baseDao.contarRegistros(conn, tabla, filtros);
            int totalPaginas = (int) Math.ceil((double) totalRegistros / limite);

            ctx.header("X-Total-Count", String.valueOf(totalRegistros));
            ctx.header("X-Total-Pages", String.valueOf(totalPaginas));
            ctx.header("X-Current-Page", String.valueOf((offset / limite) + 1));
            ctx.header("Access-Control-Expose-Headers", "X-Total-Count, X-Total-Pages, X-Current-Page");

            List<EntidadDinamica> resultados;

            // ── Ejecutar consulta según si hay relaciones ─────────────
            if (!relaciones.isEmpty()) {
                // Consulta con JOINs para incluir datos relacionados
                resultados = baseDao.buscarConIncludes(
                        conn, tabla, columnas, relaciones, colsHijas,
                        filtros, sort, order, limite, offset
                );
            } else if (!columnas.isEmpty()) {
                // Consulta simple sin JOINs
                resultados = baseDao.buscarTodo(conn, tabla, columnas, filtros, sort, order, limite, offset);
            } else {
                throw new BaseDatosException("La tabla '" + tabla + "' no tiene columnas configuradas.", null);
            }

            // ── Post-procesamiento: datos sensibles y privacidad ─────
            for (EntidadDinamica entidad : resultados) {
                rellenarSensibles(entidad, columnas, config.getId());
            }
            aplicarFiltroPrivacidadLista(resultados, columnas);

            ctx.status(HttpCode.OK).json(ApiRespuesta.ok(resultados));

        } catch (SQLException e) {
            throw new BaseDatosException("Error al consultar tabla '" + tabla + "'.", e);
        }
    }

    /**
     * Endpoint: GET /api/{tabla}/{id}
     * Obtiene un registro específico por su ID, con soporte para relaciones.
     *
     * @param ctx Contexto de la petición HTTP
     */
    public void fetchPorId(Context ctx) {
        String tabla = ctx.pathParam("tabla");
        Long id = ctx.pathParamAsClass("id", Long.class).get();
        String includes = ctx.queryParam("include");

        validador.validarNombre(tabla);

        TablaConfig config = metaService.getConfiguracion(tabla);
        List<ColumnaConfig> columnas = config.getColumnas() != null
                ? config.getColumnas() : new ArrayList<>();

        List<RelacionConfig> relaciones = metaService.getRelaciones(tabla, includes);
        Map<String, List<ColumnaConfig>> colsHijas = obtenerColumnasHijas(relaciones);

        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_CLIENTE)) {
            Object resultado;

            if (!relaciones.isEmpty()) {
                resultado = baseDao.buscarPorIdConIncludes(conn, tabla, id, columnas, relaciones, colsHijas);
            } else if (!columnas.isEmpty()) {
                resultado = baseDao.buscarPorId(conn, tabla, columnas, id);
            } else {
                throw new BaseDatosException("La tabla '" + tabla + "' no tiene columnas configuradas.", null);
            }

            if (resultado == null) {
                throw new RecursoNoEncontradoException("No se encontró registro con ID: " + id);
            }

            EntidadDinamica entidad = (EntidadDinamica) resultado;
            rellenarSensibles(entidad, columnas, config.getId());
            aplicarFiltroPrivacidadEntidad(entidad, columnas);

            ctx.status(HttpCode.OK).json(ApiRespuesta.ok(entidad));

        } catch (SQLException e) {
            throw new BaseDatosException("Error al buscar el registro por ID.", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CREATE: Insertar registros (con soporte para archivos y sensibles)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Endpoint: POST /api/{tabla}
     * Inserta un nuevo registro en la tabla especificada.
     *
     * Soporta:
     * - JSON o multipart/form-data (para archivos)
     * - Columnas tipo ARCHIVO: se guardan en db4o o disco, UUID en MySQL
     * - Columnas tipo SENSIBLE: se cifran y guardan en Paradox
     * - Conversión automática de tipos de dato
     * - Registro de auditoría en LogService
     *
     * @param ctx Contexto de la petición HTTP
     */
    @SuppressWarnings("unchecked")
    public void insert(Context ctx) {
        String tabla = ctx.pathParam("tabla");
        validador.validarNombre(tabla);

        Map<String, Object> body = extraerDatos(ctx);
        if (body == null || body.isEmpty()) {
            throw new ValidacionException("Cuerpo de la petición vacío.");
        }

        // ── CASO ESPECIAL: tabla empleados ────────────────────────────────────
        // Cuando se inserta un empleado desde el endpoint genérico, se sincroniza
        // automáticamente con erp_users para que el empleado tenga cuenta de acceso.
        if ("empleados".equalsIgnoreCase(tabla)) {
            sincronizarEmpleadoConErpUsers(ctx, body);
            return;
        }
        // ─────────────────────────────────────────────────────────────────────

        EntidadDinamica entidad = new EntidadDinamica();
        body.forEach(entidad::set);

        TablaConfig config = metaService.getConfiguracion(tabla);
        if (config.getColumnas() != null) {
            entidad = convertirTipos(entidad, config.getColumnas());
        }

        List<String> uuids = new ArrayList<>();
        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_CLIENTE)) {
            // Procesar archivos primero (antes de insertar en MySQL)
            procesarFicheros(ctx, tabla, entidad, uuids, config.getColumnas());

            // Guardar datos sensibles en Paradox (separado de MySQL)
            guardarSensibles(entidad, config.getColumnas(), config.getId(), null);

            // Insertar en MySQL
            long id = baseDao.insertar(conn, tabla, entidad);

            // Actualizar PK en datos sensibles si usamos "PENDING"
            actualizarPkSensibles(config.getId(), id);

            // Registrar log de auditoría
            String usuario = obtenerUsuarioCtx(ctx);
            LogService.registrar(usuario, "INSERT", tabla, "Registro insertado con id=" + id);

            // Respuesta con el ID generado
            EntidadDinamica respuesta = new EntidadDinamica();
            respuesta.setId(id);
            ctx.status(HttpCode.CREATED).json(ApiRespuesta.ok(respuesta));

        } catch (Exception e) {
            // Rollback: eliminar archivos subidos si falló la operación
            limpiarFicheros(uuids);
            throw new BaseDatosException("Error al insertar registro en '" + tabla + "'.", e);
        }
    }

    /**
     * Endpoint: POST /api/batch/insert
     * Inserta registros en múltiples tablas en una sola transacción.
     *
     * Body esperado:
     * {
     *   "datos": {
     *     "clientes": { "nombre": "Juan", "email": "juan@email.com" },
     *     "pedidos": { "fecha": "2024-01-01", "cliente_id": null }
     *   }
     * }
     *
     * El servicio OrderService ordena las tablas para respetar dependencias FK.
     * Incluye registro de auditoría en LogService.
     *
     * @param ctx Contexto de la petición HTTP
     */
    @SuppressWarnings("unchecked")
    public void insertTransaccional(Context ctx) {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        Map<String, Object> datos = (Map<String, Object>) body.get("datos");

        if (datos == null) {
            throw new ValidacionException("El campo 'datos' es obligatorio.");
        }

        // Ordenar tablas según dependencias de foreign keys
        List<String> orden = orderService.ordenarTablas(new ArrayList<>(datos.keySet()));
        Map<String, EntidadDinamica> datosPorTabla = new LinkedHashMap<>();
        List<String> uuids = new ArrayList<>();

        for (String tabla : orden) {
            Map<String, Object> mapaDatos = (Map<String, Object>) datos.get(tabla);
            if (mapaDatos == null) continue;

            EntidadDinamica entidad = new EntidadDinamica();
            mapaDatos.forEach(entidad::set);

            TablaConfig config = metaService.getConfiguracion(tabla);
            if (config != null && config.getColumnas() != null) {
                entidad = convertirTipos(entidad, config.getColumnas());
                procesarFicheros(ctx, tabla, entidad, uuids, config.getColumnas());
            }
            datosPorTabla.put(tabla, entidad);
        }

        // Obtener relaciones entre las tablas para inyectar FKs automáticamente
        List<RelacionConfig> relaciones = metaService.getRelacionesEntreTablas(orden);

        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_CLIENTE)) {
            conn.setAutoCommit(false);
            try {
                // Insertar en orden: padres primero, hijos después
                Map<String, Long> idsGenerados = baseDao.insertarTransaccional(
                        conn, orden, datosPorTabla, relaciones);
                conn.commit();

                // Registrar log de auditoría
                String usuario = obtenerUsuarioCtx(ctx);
                LogService.registrar(usuario, "INSERT", String.join(",", orden),
                        "Insert transaccional en tablas: " + orden + " ids=" + idsGenerados);

                ctx.status(HttpCode.CREATED).json(ApiRespuesta.ok(idsGenerados));

            } catch (Exception e) {
                conn.rollback();
                limpiarFicheros(uuids);
                throw new BaseDatosException("Error en transacción batch. Se aplicó rollback.", e);
            }
        } catch (SQLException e) {
            limpiarFicheros(uuids);
            throw new BaseDatosException("Error de conexión en operación batch.", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // UPDATE: Actualizar registros (con soporte para archivos y sensibles)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Endpoint: PUT /api/{tabla}/{id}
     * Actualiza un registro existente.
     *
     * - Solo actualiza los campos enviados en el body (partial update)
     * - Gestiona archivos: nuevos se suben, antiguos se mantienen
     * - Actualiza datos sensibles en Paradox si corresponde
     * - Registro de auditoría en LogService
     *
     * @param ctx Contexto de la petición HTTP
     */
    @SuppressWarnings("unchecked")
    public void update(Context ctx) {
        String tabla = ctx.pathParam("tabla");
        Long id = ctx.pathParamAsClass("id", Long.class).get();
        validador.validarNombre(tabla);

        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        if (body == null || body.isEmpty()) {
            throw new ValidacionException("Cuerpo de la petición vacío.");
        }

        EntidadDinamica entidad = new EntidadDinamica();
        body.forEach(entidad::set);

        TablaConfig config = metaService.getConfiguracion(tabla);
        if (config.getColumnas() != null) {
            entidad = convertirTipos(entidad, config.getColumnas());
        }

        List<String> uuids = new ArrayList<>();
        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_CLIENTE)) {
            procesarFicheros(ctx, tabla, entidad, uuids, config.getColumnas());
            guardarSensibles(entidad, config.getColumnas(), config.getId(), id);

            int filas = baseDao.actualizar(conn, tabla, entidad, id);
            if (filas == 0) {
                throw new RecursoNoEncontradoException("No se encontró registro con ID: " + id);
            }

            // Registrar log de auditoría
            String usuario = obtenerUsuarioCtx(ctx);
            LogService.registrar(usuario, "UPDATE", tabla, "Registro actualizado id=" + id);

            ctx.status(HttpCode.OK).json(ApiRespuesta.ok("Registro actualizado correctamente."));

        } catch (SQLException e) {
            limpiarFicheros(uuids);
            throw new BaseDatosException("Error al actualizar registro.", e);
        }
    }

    /**
     * Endpoint: PUT /api/batch/update
     * Actualiza registros en múltiples tablas en una transacción.
     *
     * @param ctx Contexto de la petición HTTP
     */
    @SuppressWarnings("unchecked")
    public void updateTransaccional(Context ctx) {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        Number idRaw = (Number) body.get("id");

        if (idRaw == null) {
            throw new ValidacionException("El campo 'id' es obligatorio para update batch.");
        }
        Long id = idRaw.longValue();

        Map<String, Object> datosRaw = (Map<String, Object>) body.get("datos");
        if (datosRaw == null) {
            throw new ValidacionException("El campo 'datos' es obligatorio.");
        }

        List<String> orden = orderService.ordenarTablas(new ArrayList<>(datosRaw.keySet()));
        Map<String, EntidadDinamica> datosPorTabla = new LinkedHashMap<>();

        for (String tabla : orden) {
            Map<String, Object> mapaDatos = (Map<String, Object>) datosRaw.get(tabla);
            if (mapaDatos == null) continue;

            EntidadDinamica entidad = new EntidadDinamica();
            mapaDatos.forEach(entidad::set);

            TablaConfig config = metaService.getConfiguracion(tabla);
            if (config != null && config.getColumnas() != null) {
                entidad = convertirTipos(entidad, config.getColumnas());
            }
            datosPorTabla.put(tabla, entidad);
        }

        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_CLIENTE)) {
            conn.setAutoCommit(false);
            try {
                int filasAfectadas = baseDao.actualizarTransaccional(conn, orden, datosPorTabla, id);
                if (filasAfectadas == 0) {
                    throw new RecursoNoEncontradoException("No se encontraron registros para actualizar.");
                }
                conn.commit();
                ctx.json(ApiRespuesta.ok("Se actualizaron " + filasAfectadas + " registros."));
            } catch (Exception e) {
                conn.rollback();
                throw new BaseDatosException("Error en update batch. Se aplicó rollback.", e);
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error de conexión en update batch.", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DELETE: Eliminar registros (con limpieza de sensibles)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Endpoint: DELETE /api/{tabla}/{id}
     * Elimina un registro y limpia sus datos sensibles asociados.
     * Incluye registro de auditoría en LogService.
     *
     * @param ctx Contexto de la petición HTTP
     */
    public void delete(Context ctx) {
        String tabla = ctx.pathParam("tabla");
        Long id = ctx.pathParamAsClass("id", Long.class).get();
        validador.validarNombre(tabla);

        // Verificar que la tabla existe en metadatos
        TablaConfig config = metaService.getConfiguracion(tabla);

        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_CLIENTE)) {
            int filas = baseDao.eliminar(conn, tabla, id);
            if (filas == 0) {
                throw new RecursoNoEncontradoException("No se encontró registro con ID: " + id);
            }

            // Limpiar datos sensibles en Paradox
            borrarSensibles(config.getId(), id);

            // Registrar log de auditoría
            String usuario = obtenerUsuarioCtx(ctx);
            LogService.registrar(usuario, "DELETE", tabla, "Registro eliminado id=" + id);

            ctx.json(ApiRespuesta.ok("Registro eliminado correctamente."));

        } catch (SQLException e) {
            throw new BaseDatosException("Error al eliminar registro.", e);
        }
    }

    /**
     * Endpoint: DELETE /api/batch/delete
     * Elimina registros de múltiples tablas respetando dependencias FK.
     * Incluye registro de auditoría en LogService.
     *
     * @param ctx Contexto de la petición HTTP
     */
    public void deleteTransaccional(Context ctx) {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        Number idRaw = (Number) body.get("id");

        if (idRaw == null) {
            throw new ValidacionException("El campo 'id' es obligatorio.");
        }
        Long id = idRaw.longValue();

        List<String> tablas = (List<String>) body.get("tablas");
        if (tablas == null || tablas.isEmpty()) {
            throw new ValidacionException("El campo 'tablas' es obligatorio.");
        }

        // Ordenar e invertir: eliminar hijos antes que padres
        List<String> orden = orderService.ordenarTablas(tablas);

        // Validar que todas las tablas existen en metadatos
        for (String tabla : orden) {
            metaService.getConfiguracion(tabla);
        }

        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_CLIENTE)) {
            conn.setAutoCommit(false);
            try {
                int filas = baseDao.eliminarTransaccional(conn, orden, id);
                if (filas == 0) {
                    throw new RecursoNoEncontradoException("No se encontraron registros para eliminar.");
                }
                conn.commit();
                
                // Registrar log de auditoría
                String usuario = obtenerUsuarioCtx(ctx);
                LogService.registrar(usuario, "DELETE", String.join(",", orden),
                        "Delete transaccional en tablas: " + orden + " id=" + id);
                
                ctx.json(ApiRespuesta.ok("Se eliminaron registros en " + orden.size() + " tablas."));
            } catch (Exception e) {
                conn.rollback();
                throw new BaseDatosException("Error en delete batch. Se aplicó rollback.", e);
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error de conexión en delete batch.", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILIDADES PRIVADAS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Extrae datos del request, soportando tanto JSON como multipart/form-data.
     * @param ctx Contexto HTTP
     * @return Mapa con los datos extraídos
     */
    private Map<String, Object> extraerDatos(Context ctx) {
        if (ctx.isMultipart()) {
            // Formulario con archivo: extraer campos de texto
            Map<String, Object> datos = new HashMap<>();
            ctx.formParamMap().forEach((key, value) -> {
                if (!value.isEmpty()) {
                    datos.put(key, value.get(0));
                }
            });
            return datos;
        } else {
            // JSON puro
            return ctx.bodyAsClass(Map.class);
        }
    }

    /**
     * Obtiene los metadatos de columnas para cada tabla hija en las relaciones.
     * @param relaciones Lista de relaciones configuradas
     * @return Mapa: nombre de tabla → lista de ColumnaConfig
     */
    private Map<String, List<ColumnaConfig>> obtenerColumnasHijas(List<RelacionConfig> relaciones) {
        Map<String, List<ColumnaConfig>> colsHijas = new HashMap<>();
        for (RelacionConfig rel : relaciones) {
            TablaConfig configHija = metaService.getConfiguracion(rel.getTablaDestino());
            List<ColumnaConfig> colHija = configHija.getColumnas() != null
                    ? configHija.getColumnas() : new ArrayList<>();
            colsHijas.put(rel.getTablaDestino(), colHija);
        }
        return colsHijas;
    }

    /**
     * Convierte valores de entrada (String desde JSON) a tipos Java según metadatos.
     * @param datos Entidad con valores sin procesar
     * @param columnas Metadatos de columnas para saber el tipo esperado
     * @return Nueva entidad con valores convertidos
     */
    private EntidadDinamica convertirTipos(EntidadDinamica datos, List<ColumnaConfig> columnas) {
        EntidadDinamica convertidos = new EntidadDinamica();

        for (Map.Entry<String, Object> entry : datos.getTodo().entrySet()) {
            ColumnaConfig conf = null;
            for (ColumnaConfig c : columnas) {
                if (c.getNombre().equalsIgnoreCase(entry.getKey())) {
                    conf = c;
                    break;
                }
            }

            if (conf != null) {
                try {
                    convertidos.set(entry.getKey(), TipoDatoMapper.toJava(entry.getValue(), conf));
                } catch (Exception e) {
                    throw new ValidacionException("Error en campo '" + entry.getKey() + "': " + e.getMessage());
                }
            } else {
                // Columna no configurada: pasar valor tal cual
                convertidos.set(entry.getKey(), entry.getValue());
            }
        }
        return convertidos;
    }

    /**
     * Procesa archivos subidos: guarda en db4o o disco, devuelve UUID para MySQL.
     * @param ctx Contexto HTTP
     * @param tabla Nombre de la tabla destino
     * @param entidad Entidad que recibirá el UUID del archivo
     * @param uuidsNuevos Lista para tracking de archivos (rollback en caso de error)
     * @param columnas Metadatos para identificar columnas tipo ARCHIVO
     */
    private void procesarFicheros(Context ctx, String tabla, EntidadDinamica entidad,
            List<String> uuidsNuevos, List<ColumnaConfig> columnas) {
        for (ColumnaConfig col : columnas) {
            if (col.isArchivo()) {
                UploadedFile file = ctx.uploadedFile(col.getNombre());
                if (file != null) {
                    String uuid = UUID.randomUUID().toString();
                    uuidsNuevos.add(uuid); // Para rollback si falla
                    entidad.set(col.getNombre(), uuid); // UUID va a MySQL
                    ficheroService.guardar(uuid, tabla, file); // Contenido va a db4o/disco
                }
            }
        }
    }

    /**
     * Elimina archivos temporalmente subidos si la operación principal falla.
     * @param uuids Lista de UUIDs a eliminar
     */
    private void limpiarFicheros(List<String> uuids) {
        for (String uuid : uuids) {
            ficheroService.eliminar(uuid);
        }
    }

    /**
     * Recupera datos sensibles desde Paradox y los inyecta en la entidad.
     * @param entidad Entidad a enriquecer
     * @param columnas Metadatos para identificar columnas sensibles
     * @param tablaId ID de la tabla en erp_meta_tablas
     */
    private void rellenarSensibles(EntidadDinamica entidad, List<ColumnaConfig> columnas, long tablaId) {
        for (ColumnaConfig col : columnas) {
            if (!col.isSensible()) continue;
            try {
                String pk = String.valueOf(entidad.getId());
                String resultado = ejecutarParadoxWorker(
                    "SENS_SELECT",
                    String.valueOf(tablaId),
                    String.valueOf(col.getId()),
                    ParadoxWorker.encode(pk)
                );
                if (resultado != null && resultado.startsWith("OK:")) {
                    String valorEnc = resultado.substring(3);
                    if (!"NULL".equals(valorEnc)) {
                        // decode del encode que hizo el worker en encode()
                        String valorDecod = valorEnc
                            .replace("__SPACE__",     " ")
                            .replace("__NL__",        "\n")
                            .replace("__CR__",        "\r")
                            .replace("__BACKSLASH__", "\\");
                        entidad.set(col.getNombre(), cifrado.desencriptar(valorDecod));
                    }
                } else if (resultado != null && resultado.startsWith("ERROR:")) {
                    System.err.println("[API] AVISO: ParadoxWorker SELECT error para col '"
                        + col.getNombre() + "': " + resultado.substring(6));
                }
            } catch (Exception e) {
                System.err.println("[API] AVISO: No se pudo leer sensible '" + col.getNombre()
                    + "' desde Paradox: " + e.getMessage());
            }
        }
    }

    /**
     * Guarda datos sensibles en Paradox (separado de MySQL) con cifrado.
     * @param entidad Entidad con los datos a procesar
     * @param columnas Metadatos para identificar columnas sensibles
     * @param tablaId ID de la tabla en erp_meta_tablas
     * @param pkExistente ID del registro (null si es INSERT nuevo)
     */
    private void guardarSensibles(EntidadDinamica entidad, List<ColumnaConfig> columnas,
            long tablaId, Long pkExistente) {
        for (ColumnaConfig col : columnas) {
            if (!col.isSensible()) continue;

            Object val = entidad.get(col.getNombre());

            // SIEMPRE eliminar el campo de la entidad antes de que llegue a MySQL.
            // La columna sensible en MySQL es VARCHAR(1) NULL (placeholder).
            // El dato real va cifrado a Paradox; MySQL nunca lo ve.
            entidad.getTodo().remove(col.getNombre());

            if (val == null) continue;

            // Intentar guardar en Paradox vía subproceso. Si falla se loguea pero el INSERT
            // en MySQL sigue adelante (columna quedara NULL en MySQL).
            try {
                String enc = cifrado.encriptar(val.toString());
                String pk  = pkExistente == null ? "PENDING" : String.valueOf(pkExistente);
                String resultado = ejecutarParadoxWorker(
                    "SENS_INSERT",
                    String.valueOf(tablaId),
                    String.valueOf(col.getId()),
                    ParadoxWorker.encode(pk),
                    ParadoxWorker.encode(enc)
                );
                if (resultado == null || resultado.startsWith("ERROR:")) {
                    System.err.println("[API] AVISO: ParadoxWorker INSERT falló para col '"
                        + col.getNombre() + "': " + (resultado != null ? resultado.substring(6) : "null"));
                }
            } catch (Exception e) {
                System.err.println("[API] AVISO: No se pudo guardar campo sensible '"
                    + col.getNombre() + "' en Paradox: " + e.getMessage());
            }
        }
    }

    /**
     * Actualiza el placeholder "PENDING" por el ID real en datos sensibles.
     * Se usa después de INSERT cuando el ID se genera autoincremental.
     * @param tablaId ID de la tabla en metadatos
     * @param pkReal ID real generado por MySQL
     */
    private void actualizarPkSensibles(long tablaId, long pkReal) {
        try {
            String resultado = ejecutarParadoxWorker(
                "SENS_UPDATE_PK",
                String.valueOf(tablaId),
                ParadoxWorker.encode(String.valueOf(pkReal))
            );
            if (resultado == null || resultado.startsWith("ERROR:")) {
                System.err.println("[API] AVISO: ParadoxWorker UPDATE_PK falló: "
                    + (resultado != null ? resultado.substring(6) : "null"));
            }
        } catch (Exception e) {
            System.err.println("[API] AVISO: No se pudo actualizar PK en Paradox: " + e.getMessage());
        }
    }

    /**
     * Elimina datos sensibles asociados a un registro cuando se borra.
     * @param tablaId ID de la tabla en metadatos
     * @param pk ID del registro a eliminar
     */
    private void borrarSensibles(long tablaId, long pk) {
        try {
            String resultado = ejecutarParadoxWorker(
                "SENS_DELETE",
                String.valueOf(tablaId),
                ParadoxWorker.encode(String.valueOf(pk))
            );
            if (resultado == null || resultado.startsWith("ERROR:")) {
                throw new BaseDatosException("ParadoxWorker DELETE falló: "
                    + (resultado != null ? resultado.substring(6) : "null"), null);
            }
        } catch (BaseDatosException e) {
            throw e;
        } catch (Exception e) {
            throw new BaseDatosException("Error al eliminar datos sensibles.", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SUBPROCESS: Lanza ParadoxWorker en JVM nueva (evita límite 50 queries)
    // ═══════════════════════════════════════════════════════════════════

    private static final String CARPETA_SENSIBLES = "base_de_datos";

    /**
     * Ejecuta ParadoxWorker en un subproceso independiente para operaciones
     * sobre paradox_sensibles. Cada llamada crea una JVM nueva → contador
     * de queries a 0 → nunca se alcanza el límite de 50 del driver HXTT.
     *
     * @param cmd   Comando SENS_*: SENS_INSERT, SENS_UPDATE_PK, SENS_SELECT,
     *              SENS_SELECT_ALL_TABLE, SENS_DELETE, SENS_INIT
     * @param args  Argumentos adicionales dependientes del comando
     * @return Línea de stdout del worker ("OK:..." o "ERROR:..."), o null si falló el proceso
     */
    private static String ejecutarParadoxWorker(String cmd, String... args) {
        try {
            String carpetaAbs = new java.io.File(CARPETA_SENSIBLES).getAbsolutePath();
            String classpath  = System.getProperty("java.class.path");
            String javaExe    = System.getProperty("java.home") + "/bin/java";

            java.util.List<String> cmdList = new java.util.ArrayList<>();
            cmdList.add(javaExe);
            cmdList.add("-cp");
            cmdList.add(classpath);
            cmdList.add("logs.dao.ParadoxWorker");
            cmdList.add(carpetaAbs);
            cmdList.add(cmd);
            for (String a : args) cmdList.add(a);

            ProcessBuilder pb = new ProcessBuilder(cmdList);
            pb.redirectErrorStream(false);
            Process proc = pb.start();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            proc.waitFor();
            return sb.toString().trim();
        } catch (Exception e) {
            System.err.println("[API] ejecutarParadoxWorker error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Filtra campos no visibles o contraseñas de la respuesta al cliente.
     * @param entidad Entidad a filtrar
     * @param configs Metadatos de columnas
     */
    private void aplicarFiltroPrivacidadEntidad(EntidadDinamica entidad, List<ColumnaConfig> configs) {
        if (entidad == null || configs == null) return;

        configs.stream()
                .filter(c -> c.isContrasena() || !c.isVisible())
                .forEach(c -> entidad.getTodo().remove(c.getNombre()));
    }

    /**
     * Aplica filtro de privacidad a una lista de entidades.
     * @param entidades Lista a procesar
     * @param configs Metadatos de columnas
     */
    private void aplicarFiltroPrivacidadLista(List<EntidadDinamica> entidades, List<ColumnaConfig> configs) {
        if (entidades == null) return;
        entidades.forEach(e -> aplicarFiltroPrivacidadEntidad(e, configs));
    }

    /**
     * Extrae el nombre o ID del usuario autenticado del contexto Javalin.
     * El before() de ApiGenerica inyecta "usuarioId" como atributo.
     * @param ctx Contexto HTTP de Javalin
     * @return Identificador del usuario en formato "usuario#ID" o "api" por defecto
     */
    private String obtenerUsuarioCtx(Context ctx) {
        try {
            Object id = ctx.attribute("usuarioId");
            return id != null ? "usuario#" + id : "api";
        } catch (Exception e) {
            return "api";
        }
    }

    // ── Sincronización empleados → erp_users ──────────────────────────────────

    /**
     * Gestiona el insert en la tabla "empleados" desde el endpoint genérico.
     *
     * Si el body incluye "contrasena" (o "password"), delega en EmpleadoService
     * para que se cree el registro en erp_users y en empleados de forma atómica.
     *
     * Si NO viene contraseña (el frontend no la envía en este flujo), se inserta
     * el empleado normalmente con BaseDao y luego se crea el usuario en erp_users
     * con una contraseña temporal aleatoria que el administrador deberá cambiar.
     *
     * Campos esperados en el body:
     *   - email / correo_electronico  (obligatorio)
     *   - nombre                      (obligatorio)
     *   - primer_apellido             (obligatorio)
     *   - dni_nie                     (obligatorio)
     *   - contrasena / password       (opcional; si falta se genera temporal)
     *   - rol                         (opcional; por defecto "empleado")
     *   - cargo                       (opcional)
     */
    @SuppressWarnings("unchecked")
    private void sincronizarEmpleadoConErpUsers(Context ctx, Map<String, Object> body) {
        // Normalizar claves del body
        String email    = safeStr(body, "email", body.get("correo_electronico"));
        String nombre   = safeStr(body, "nombre", null);
        String apellido = safeStr(body, "primer_apellido", null);
        String dni      = safeStr(body, "dni_nie", null);
        String cargo    = safeStr(body, "cargo", null);
        String rol      = safeStr(body, "rol", null);

        // Contraseña: usar la del body si viene, si no generar una temporal
        String contrasena = safeStr(body, "contrasena", body.get("password"));
        boolean contrasenaGenerada = false;
        if (contrasena == null || contrasena.trim().isEmpty()) {
            contrasena = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            contrasenaGenerada = true;
        }

        // Validar campos mínimos
        if (email == null || email.trim().isEmpty()
                || dni == null || dni.trim().isEmpty()) {
            throw new ValidacionException("Los campos 'email' y 'dni_nie' son obligatorios para crear un empleado.");
        }

        if (rol == null || rol.trim().isEmpty()) rol = "empleado";

        boolean ok = empleadoService.registrarEmpleadoConAcceso(
                email.trim(), contrasena, rol.trim(),
                nombre   != null ? nombre.trim()   : "",
                apellido != null ? apellido.trim()  : "",
                dni.trim(),
                cargo    != null ? cargo.trim()     : ""
        );

        if (!ok) {
            throw new BaseDatosException(
                "Error al crear el empleado. El email o DNI pueden estar duplicados.", null);
        }

        String usuario = obtenerUsuarioCtx(ctx);
        LogService.registrar(usuario, "INSERT", "empleados",
                "Empleado '" + email.trim() + "' creado con cuenta en erp_users"
                + (contrasenaGenerada ? " (contraseña temporal generada)" : ""));

        // Construir respuesta
        Map<String, Object> datos = new java.util.LinkedHashMap<>();
        datos.put("email", email.trim());
        datos.put("rol", rol.trim());
        if (contrasenaGenerada) {
            datos.put("contrasena_temporal", contrasena);
            datos.put("aviso", "Se ha generado una contraseña temporal. Cámbiala en el primer acceso.");
        }

        EntidadDinamica respuesta = new EntidadDinamica();
        datos.forEach(respuesta::set);
        ctx.status(HttpCode.CREATED).json(ApiRespuesta.ok(respuesta));
    }

    /** Extrae un String de un Map con clave primaria; si es null usa el valorAlternativo. */
    private String safeStr(Map<String, Object> map, String clave, Object alternativo) {
        Object v = map.get(clave);
        if (v != null && !v.toString().trim().isEmpty()) return v.toString().trim();
        if (alternativo != null && !alternativo.toString().trim().isEmpty()) return alternativo.toString().trim();
        return null;
    }
}