package apigenerica.controller;

import apigenerica.TipoDatoMapper;
import apigenerica.config.AppConfig;
import apigenerica.config.ConexionMysql;
import apigenerica.dao.BaseDao;
import apigenerica.dao.MetaDao;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.excepciones.RecursoNoEncontradoException;
import apigenerica.excepciones.ValidacionException;
import apigenerica.model.ApiRespuesta;
import apigenerica.model.ColumnaConfig;
import apigenerica.model.EntidadDinamica;
import apigenerica.model.RelacionConfig;
import apigenerica.model.TablaConfig;
import apigenerica.service.FicheroService;
import apigenerica.service.MetaService;
import apigenerica.service.OrderService;
import apigenerica.service.ValidadorService;
import io.javalin.http.Context;
import io.javalin.http.HttpCode;
import io.javalin.http.UploadedFile;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controlador genérico CRUD para cualquier tabla de la base de datos. Usa los
 * metadatos almacenados en MySQL para mapear los resultados.
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

    public BaseController(ValidadorService validador, MetaService metaService,
            BaseDao baseDao, MetaDao metaDao, OrderService orderService, 
            FicheroService ficheroService) {
        this.validador = validador;
        this.metaService = metaService;
        this.baseDao = baseDao;
        this.metaDao = metaDao;
        this.orderService = orderService;
        this.ficheroService = ficheroService;
    }

    /**
     * Obtener entidad de una tabla
     *
     * @param ctx Contexto de la petición HTTP
     */
    public void fetchTodo(Context ctx) {
        // Obtener nombre de la tabla de la URL
        String tabla = ctx.pathParam("tabla");
        validador.validarNombre(tabla);

        // Filtros
        Map<String, String> filtros = new HashMap<>();
        // Ignorar los siguientes parámetros
        List<String> controlParams = Arrays.asList("limit", "offset", "sort", "order", "include");
        ctx.queryParamMap().forEach((key, values) -> {
            if (!controlParams.contains(key.toLowerCase())) {
                filtros.put(key, values.get(0));
            }
        });

        // Parámetros de paginación y orden
        String includes = ctx.queryParam("include"); // Ejemplo: ?include=cliente,empresa
        int limite = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20);
        int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);
        String sort = ctx.queryParam("sort");
        String order = ctx.queryParam("order");

        // Buscar metadatos de la tabla de MySQL
        TablaConfig config = metaService.getConfiguracion(tabla);
        List<ColumnaConfig> columnas = config.getColumnas() != null
                ? config.getColumnas() : new ArrayList<>();

        // Includes (Relaciones)
        List<RelacionConfig> relaciones = metaService.getRelaciones(tabla, includes);
        Map<String, List<ColumnaConfig>> colsHijas = obtenerColumnasHijas(relaciones);

        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_CLIENTE)) {
            long totalRegistros = baseDao.contarRegistros(conn, tabla, filtros);
            int totalPaginas = (int) Math.ceil((double) totalRegistros / limite);

            // Añadir número de registros y páginas totales + página actual al header
            ctx.header("X-Total-Count", String.valueOf(totalRegistros));
            ctx.header("X-Total-Pages", String.valueOf(totalPaginas));
            ctx.header("X-Current-Page", String.valueOf((offset / limite) + 1));
            // Exponer los headers para que CORS pueda leerlos
            ctx.header("Access-Control-Expose-Headers", "X-Total-Count, X-Total-Pages, X-Current-Page");

            List<EntidadDinamica> resultados;

            // Si hay relaciones, SELECT con includes
            if (!relaciones.isEmpty()) {
                resultados = baseDao.buscarConIncludes(
                        conn, tabla, columnas, relaciones, colsHijas,
                        filtros, sort, order, limite, offset
                );
            } // Si no hay relaciones pero sí metadatos de columnas, SELECT normal
            else if (!columnas.isEmpty()) {
                resultados = baseDao.buscarTodo(conn, tabla, columnas, filtros, sort, order, limite, offset);
            } else {
                throw new BaseDatosException("La tabla '" + tabla + "' no tiene columnas configuradas.", null);
            }
            aplicarFiltroPrivacidadLista(resultados, columnas);
            ctx.status(HttpCode.OK).json(ApiRespuesta.ok(resultados));
        } catch (SQLException e) {
            throw new BaseDatosException("Error al consultar tabla '" + tabla + "'.", e);
        }
    }

    private Map<String, List<ColumnaConfig>> obtenerColumnasHijas(List<RelacionConfig> relaciones) {
        Map<String, List<ColumnaConfig>> colsHijas = new HashMap<>();
        for (RelacionConfig rel : relaciones) {
            // Buscar configuración de la tabla destino (hija)
            TablaConfig configHija = metaService.getConfiguracion(rel.getTablaDestino());

            // Si tiene columnas, se guardan. Si no, guardar una lista vacía
            List<ColumnaConfig> colHija = configHija.getColumnas() != null
                    ? configHija.getColumnas() : new ArrayList<>();

            colsHijas.put(rel.getTablaDestino(), colHija);
        }
        return colsHijas;
    }

    /**
     * Obtener entidad de un registro de una tabla
     *
     * @param ctx Contexto de la petición HTTP
     */
    public void fetchPorId(Context ctx) {
        // Obtener parámetros de la URL
        String tabla = ctx.pathParam("tabla");
        Long id = ctx.pathParamAsClass("id", Long.class).get();
        String includes = ctx.queryParam("include");
        validador.validarNombre(tabla);

        // Buscar metadatos de la tabla de MySQL
        TablaConfig config = metaService.getConfiguracion(tabla);
        List<ColumnaConfig> columnas = config.getColumnas() != null
                ? config.getColumnas() : new ArrayList<>();

        // Includes (Relaciones)
        List<RelacionConfig> relaciones = metaService.getRelaciones(tabla, includes);
        Map<String, List<ColumnaConfig>> colsHijas = obtenerColumnasHijas(relaciones);

        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_CLIENTE)) {
            Object resultado;

            // Si hay relaciones, SELECT con includes
            if (!relaciones.isEmpty()) {
                resultado = baseDao.buscarPorIdConIncludes(conn, tabla, id, columnas, relaciones, colsHijas);
            } // Si no hay relaciones pero sí columnas, SELECT normal
            else if (!columnas.isEmpty()) {
                resultado = baseDao.buscarPorId(conn, tabla, columnas, id);
            } // Si no hay metadatos
            else {
                throw new BaseDatosException("La tabla '" + tabla + "' no tiene columnas configuradas.", null);
            }

            // Si no existe el registro en la DB
            if (resultado == null) {
                throw new RecursoNoEncontradoException("No se encontró registro.");
            }

            aplicarFiltroPrivacidadEntidad((EntidadDinamica) resultado, columnas);
            ctx.status(HttpCode.OK).json(ApiRespuesta.ok(resultado));
        } catch (SQLException e) {
            throw new BaseDatosException("Error al buscar el registro por ID.", e);
        }
    }

    /**
     * Inserta un registro en la base de entidad
     *
     * @param ctx Contexto de la petición HTTP
     */
    @SuppressWarnings("unchecked")
    public void insert(Context ctx) {
        // Obtener parámetros de la URL
        String tabla = ctx.pathParam("tabla");
        validador.validarNombre(tabla);

        // Obtener entidad JSON
        Map<String, Object> body = extraerDatos(ctx);
        if (body == null || body.isEmpty()) {
            throw new ValidacionException("Cuerpo vacío.");
        }
        EntidadDinamica entidad = new EntidadDinamica();
        body.forEach(entidad::set); // Mapear cada línea a EntidadDinamica

        // Buscar metadatos de la tabla de MySQL
        TablaConfig config = metaService.getConfiguracion(tabla);
        if (config.getColumnas() != null) {
            entidad = convertirTipos(entidad, config.getColumnas());
        }
        
        List<String> uuids = new ArrayList<>(); 
        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_CLIENTE)) {
            procesarFicheros(ctx, tabla, entidad, uuids, config.getColumnas());
            // Insertar en MySQL
            long id = baseDao.insertar(conn, tabla, entidad);
            // Devolver objeto con el ID que le ha sido asignado
            EntidadDinamica respuesta = new EntidadDinamica();
            respuesta.setId(id);
            ctx.status(HttpCode.CREATED).json(ApiRespuesta.ok(respuesta));
        } catch (Exception e) {
            limpiarFicheros(uuids); // Borrar ficheros guardados
            throw new BaseDatosException("Error al insertar.", e);
        }
    }

    /**
     * Inserta registros en varias tablas a modo de transacción
     *
     * @param ctx Contexto de la petición HTTP
     */
    @SuppressWarnings("unchecked")
    public void insertTransaccional(Context ctx) {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        // Obtener parámetros de la URL
        Map<String, Object> datos = (Map<String, Object>) body.get("datos");
        if (datos == null) {
            throw new ValidacionException("El campo 'datos' es obligatorio.");
        }

        // Ordenar tablas según fk
        List<String> orden = orderService.ordenarTablas(new ArrayList<>(datos.keySet()));
        Map<String, EntidadDinamica> datosPorTabla = new LinkedHashMap<>();

        List<String> uuids = new ArrayList<>();
        
        for (String tabla : orden) {
            Map<String, Object> mapaDatos = (Map<String, Object>) datos.get(tabla);
            if (mapaDatos == null) {
                continue;
            }

            // Convertir Map en EntidadDinamica
            EntidadDinamica entidad = new EntidadDinamica();
            mapaDatos.forEach(entidad::set);

            TablaConfig config = metaService.getConfiguracion(tabla);
            if (config != null && config.getColumnas() != null) {
                entidad = convertirTipos(entidad, config.getColumnas());
                procesarFicheros(ctx, tabla, entidad, uuids, config.getColumnas());
            }
            datosPorTabla.put(tabla, entidad);
        }

        List<RelacionConfig> relaciones = metaService.getRelacionesEntreTablas(orden);

        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_CLIENTE)) {
            conn.setAutoCommit(false);
            try {
                Map<String, Long> idsGenerados = baseDao.insertarTransaccional(conn, orden, datosPorTabla, relaciones);
                conn.commit();
                // Devolver el nombre de la tabla y los IDs generados en cada insert
                ctx.status(HttpCode.CREATED).json(ApiRespuesta.ok(idsGenerados));
            } catch (Exception e) {
                conn.rollback(); // Deshacer cambios
                limpiarFicheros(uuids); // Borrar ficheros guardados
                throw new BaseDatosException("Error al insertar. Se aplicó rollback.", e);
            }
        } catch (SQLException e) {
            limpiarFicheros(uuids); // Borrar ficheros guardados
            throw new BaseDatosException("Error de conexión.", e);
        }
    }
    
    /**
     * Eliminar ficheros a partir de su UUID. Se utiliza si la
     * operación falló
     * 
     * @param uuids Lista de UUIDs
     */
    private void limpiarFicheros(List<String> uuids) {
        for (String uuid : uuids) {
            ficheroService.eliminar(uuid);
        }
    }

    /**
     * Actualiza los entidad de una tabla
     *
     * @param ctx Contexto de la petición HTTP
     */
    @SuppressWarnings("unchecked")
    public void update(Context ctx) {
        // Obtener parámetros de la URL
        String tabla = ctx.pathParam("tabla");
        Long id = ctx.pathParamAsClass("id", Long.class).get();
        validador.validarNombre(tabla);

        // Convertir body del JSON en EntidadDinamica
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        if (body == null || body.isEmpty()) {
            throw new ValidacionException("Cuerpo vacío.");
        }
        
        EntidadDinamica entidad = new EntidadDinamica();
        body.forEach(entidad::set); // Mapear cada línea a EntidadDinamica

        // Buscar metadatos de la tabla de MySQL
        TablaConfig config = metaService.getConfiguracion(tabla);
        if (config.getColumnas() != null) {
            entidad = convertirTipos(entidad, config.getColumnas());
        }

        List<String> uuids = new ArrayList<>(); 
        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_CLIENTE)) {
            procesarFicheros(ctx, tabla, entidad, uuids, config.getColumnas());
            // Actualizar en MySQL
            int filas = baseDao.actualizar(conn, tabla, entidad, id);
            if (filas == 0) {
                throw new RecursoNoEncontradoException("No se encontró el registro.");
            }
            ctx.status(HttpCode.OK).json(ApiRespuesta.ok("Actualizado."));
        } catch (SQLException e) {
            limpiarFicheros(uuids); // Borrar ficheros guardados
            throw new BaseDatosException("Error al actualizar.", e);
        }
    }

    @SuppressWarnings("unchecked")
    public void updateTransaccional(Context ctx) {
        // Convertir body del JSON en Map
        Map<String, Object> body = ctx.bodyAsClass(Map.class);

        Number idRaw = (Number) body.get("id");
        if (idRaw == null) {
            throw new ValidacionException("El campo 'id' es obligatorio.");
        }
        Long id = idRaw.longValue();

        Map<String, Object> datosRaw = (Map<String, Object>) body.get("datos");
        if (datosRaw == null) {
            throw new ValidacionException("El campo 'datos' es obligatorio.");
        }

        // Ordenar tablas según fk
        List<String> orden = orderService.ordenarTablas(new ArrayList<>(datosRaw.keySet()));

        Map<String, EntidadDinamica> datosPorTabla = new LinkedHashMap<>();

        for (String tabla : orden) {
            Map<String, Object> mapaDatos = (Map<String, Object>) datosRaw.get(tabla);
            if (mapaDatos == null) {
                continue;
            }

            // Convertir map en EntidadDinamica
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
                throw new BaseDatosException("Error al actualizar registros. Se aplicó rollback.", e);
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error de conexión.", e);
        }
    }
    
    /**
     * Extrae los parámetros de la petición, tanto
     * JSON como Multipart
     */
    private Map<String, Object> extraerDatos(Context ctx) {
        if (ctx.isMultipart()) { // Si tiene un archivo
            Map<String, Object> datos = new HashMap<>();
            // Convertir formParamMap a Map
            ctx.formParamMap().forEach((key, value) -> {
                if (!value.isEmpty()) {
                    datos.put(key, value.get(0));
                }
            });
            return datos;
        } else { // Si es JSON
            return ctx.bodyAsClass(Map.class);
        }
    }

    /**
     * Elimina los entidad de una tabla
     *
     * @param ctx Contexto de la petición HTTP
     */
    public void delete(Context ctx) {
        // Obtener parámetros de la URL
        String tabla = ctx.pathParam("tabla");
        Long id = ctx.pathParamAsClass("id", Long.class).get();
        validador.validarNombre(tabla);

        // Verificar que la tabla existe
        metaService.getConfiguracion(tabla);

        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_CLIENTE)) {
            int filas = baseDao.eliminar(conn, tabla, id); // Intentar eliminar registro
            if (filas == 0) {
                throw new RecursoNoEncontradoException("No se encontró registro.");
            }
            ctx.json(ApiRespuesta.ok("Eliminado."));
        } catch (SQLException e) {
            throw new BaseDatosException("Error al eliminar.", e);
        }
    }

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

        // Ordenar y luego invertir para respetar FK en el DELETE
        List<String> orden = orderService.ordenarTablas(tablas);

        // Validar que las tablas existen
        for (String tabla : orden) {
            metaService.getConfiguracion(tabla);
        }

        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_CLIENTE)) {
            conn.setAutoCommit(false);
            try {
                int filas = baseDao.eliminarTransaccional(conn, orden, id);
                if (filas == 0) {
                    throw new RecursoNoEncontradoException("No se encontraron registros.");
                }
                conn.commit();
                ctx.json(ApiRespuesta.ok("Se eliminaron registros en " + orden.size() + " tablas."));
            } catch (Exception e) {
                conn.rollback();
                throw new BaseDatosException("Error al eliminar. Se aplicó rollback.", e);
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error de conexión.", e);
        }
    }

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
                    throw new ValidacionException(e.getMessage());
                }
            } else {
                convertidos.set(entry.getKey(), entry.getValue());
            }
        }
        return convertidos;
    }

    /**
     * Aplica el filtro de privacidad a una entidad. Estos son los únicos datos
     * que se devuelven al cliente
     *
     * @param entidad Entidad/Registro de una tabla
     * @param configs Metadatos de sus columnas para saber qué entidad se deben
ocultar
     */
    private void aplicarFiltroPrivacidadEntidad(EntidadDinamica entidad, List<ColumnaConfig> configs) {
        if (entidad == null || configs == null) {
            return;
        }
        // Ocultar entidad no visibles y contraseñas
        configs.stream()
                .filter(c -> c.isContrasena() || !c.isVisible())
                .forEach(c -> entidad.getTodo().remove(c.getNombre()));
    }

    /**
     * Aplica el filro de privacidad a una lista de entidades
     *
     * @param entidades Lista de entidades
     * @param configs Metadatos de sus columnas para saber qué entidad se deben
ocultar
     */
    private void aplicarFiltroPrivacidadLista(List<EntidadDinamica> entidades, List<ColumnaConfig> configs) {
        if (entidades == null) {
            return;
        }
        entidades.forEach(e -> aplicarFiltroPrivacidadEntidad(e, configs));
    }
    
    private void procesarFicheros(Context ctx, String tabla, EntidadDinamica entidad, List<String> uuidsNuevos, List<ColumnaConfig> columnas) {
        for (ColumnaConfig col : columnas) {
            // Obtener columnas fichero
            if (col.isArchivo()) {
                UploadedFile file = ctx.uploadedFile(col.getNombre());

                if (file != null) {
                    // Generar UUID
                    String uuid = UUID.randomUUID().toString();
                    // Guardar el UUID en una lista para borrar los ficheros en caso de error
                    uuidsNuevos.add(uuid);
                    // Añadir UUID a la entidad que se insertará en MySQL
                    entidad.set(col.getNombre(), uuid);
                    // Guardar fichero
                    ficheroService.guardar(uuid, tabla, file);
                }
            }
        }
    }
}
