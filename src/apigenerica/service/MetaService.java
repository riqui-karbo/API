package apigenerica.service;

import apigenerica.config.AppConfig;
import apigenerica.dao.MetaDao;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.excepciones.RecursoNoEncontradoException;
import apigenerica.excepciones.ValidacionException;
import apigenerica.model.ColumnaConfig;
import apigenerica.model.RelacionConfig;
import apigenerica.model.TablaConfig;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MetaService {

    private final MetaDao metaDao;
    private final ValidadorService validador;
    private final SqlService sqlService;
    private final FicheroService ficheroService;

    public MetaService(MetaDao metaDao, ValidadorService validador, SqlService sqlService,
                       FicheroService ficheroService) {
        this.metaDao = metaDao;
        this.validador = validador;
        this.sqlService = sqlService;
        this.ficheroService = ficheroService;
    }

    /**
     * Guardar metadatos
     *
     * @param tabla Metadatos a guardar
     */
    public void guardarConfiguracion(TablaConfig tabla) {
        if (tabla.getNombreAmigable() == null || tabla.getNombreAmigable().trim().isEmpty()) {
            tabla.setNombreAmigable(crearNombreAmigable(tabla.getNombreLogico()));
        }
        // Marcar columnas de contraseña y archivo automáticamente
        if (tabla.getColumnas() != null) {
            for (ColumnaConfig col : tabla.getColumnas()) {
                if ("CONTRASENA".equalsIgnoreCase(col.getTipo())) {
                    col.setContrasena(true);
                    col.setVisible(false);
                    col.setSensible(true);
                }

                if ("ARCHIVO".equalsIgnoreCase(col.getTipo())) {
                    col.setArchivo(true);
                }
            }
        }
        try {
            metaDao.guardarConfiguracion(tabla);
        } catch (Exception e) {
            throw new BaseDatosException("Error al guardar configuración de '" + tabla.getNombreLogico() + "'.", e);
        }
    }

    /**
     * Borra los metadatos de la tabla
     *
     * @param nombreLogico Nombre de la tabla
     */
    public void eliminarConfiguracion(String nombreLogico) {
        metaDao.eliminarConfiguracion(nombreLogico);
    }

    /**
     * Construye el nombre amigable a partir del nombre lógico. Ejemplo:
     * datos_clientes genera el nombre Datos clientes
     *
     * @param nombreLogico Nombre lógico de la tabla
     * @return Nombre amigable
     */
    public String crearNombreAmigable(String nombreLogico) {
        validador.validarNombre(nombreLogico);
        String nombreAmigable = nombreLogico.replace("_", " ");
        return nombreAmigable.substring(0, 1).toUpperCase() + nombreAmigable.substring(1);
    }

    /**
     * Obtiene las relaciones entre una tabla principal y una o más tablas
     * secundarias. Busca la configuración de la tabla principal (metadatos) y
     * con ella su lista de relaciones. Procesa una lista de includes, separando
     * cada elemento en un array,
     *
     * @param tablaPrincipal Tabla padre
     * @param includes Cadena de texto con las tablas secundarias
     * @return Lista de relaciones
     */
    public List<RelacionConfig> getRelaciones(String tablaPrincipal, String includes) {
        if (includes == null || includes.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String[] tablasSolicitadas = includes.replace(" ", "").split(",");
        List<String> listaSolicitadas = Arrays.asList(tablasSolicitadas);
        List<RelacionConfig> relacionesFinales = new ArrayList<>();

        TablaConfig config = metaDao.getConfiguracion(tablaPrincipal);
        if (config != null && config.getRelaciones() != null) {
            for (RelacionConfig rel : config.getRelaciones()) {
                if (listaSolicitadas.contains(rel.getTablaDestino())) {
                    relacionesFinales.add(rel);
                }
            }
        }

        List<RelacionConfig> relacionesHijas = metaDao.getRelacionesHijas(tablaPrincipal);
        if (relacionesHijas != null) {
            for (RelacionConfig rel : relacionesHijas) {
                if (listaSolicitadas.contains(rel.getTablaOrigen())) {
                    relacionesFinales.add(rel);
                }
            }
        }
        return relacionesFinales;
    }

    /**
     * Obtiene todas las relaciones internas que existen entre una lista de
     * tablas. Para operaciones transaccionales donde necesitamos inyectar
     * FKs.
     *
     * @param tablas Lista de tablas de la transacción
     * @return Lista de relaciones entre estas tablas
     */
    public List<RelacionConfig> getRelacionesEntreTablas(List<String> tablas) {
        List<RelacionConfig> relacionesFinales = new ArrayList<>();
        if (tablas == null || tablas.isEmpty()) {
            return relacionesFinales;
        }
        for (String tablaOrigen : tablas) {
            TablaConfig config = metaDao.getConfiguracion(tablaOrigen);
            if (config != null && config.getRelaciones() != null) {
                for (RelacionConfig rel : config.getRelaciones()) {
                    if (tablas.contains(rel.getTablaDestino())) {
                        relacionesFinales.add(rel);
                    }
                }
            }
        }
        return relacionesFinales;
    }

    /**
     * Devuelve los metadatos de una tabla
     *
     * @param nombreLogico Nombre de la tabla en MySQL
     * @return Metadatos de la tabla
     */
    public TablaConfig getConfiguracion(String nombreLogico) {
        TablaConfig config = metaDao.getConfiguracion(nombreLogico);
        if (config == null) {
            throw new RecursoNoEncontradoException("Tabla no registrada: " + nombreLogico);
        }
        return config;
    }

    /**
     * Devuelve la lista de todas las tablas registradas para una base de datos.
     *
     * @param moduloId Id del módulo cuyas tablas se quieren mostrar
     * @param sinModulo true si la tabla no pertenece a un módulo
     * @return Lista de metadatos de tablas
     */
    public List<TablaConfig> listarTablas(Long moduloId, Boolean sinModulo) {
        if (Boolean.TRUE.equals(sinModulo)) {
            return metaDao.listarTablasSinModulo();
        }
        if (moduloId != null) {
            return metaDao.listarTablasPorModulo(moduloId);
        }
        return metaDao.getTodas();
    }

    /**
     * Elimina una tabla
     *
     * @param nombreLogico Nombre de la base de datos en MySQL
     * @throws SQLException
     */
    public void eliminarTabla(String nombreLogico) throws SQLException {
        List<RelacionConfig> relaciones = metaDao.getRelacionesHijas(nombreLogico);
        if (!relaciones.isEmpty()) {
            throw new ValidacionException("No se puede eliminar la tabla porque otras tablas dependen de ella.");
        }
        List<String> listaUuids = metaDao.buscarFicherosAsociados(nombreLogico);
        String sql = sqlService.generarDropSql(nombreLogico);
        sqlService.ejecutarSql(AppConfig.DB_CLIENTE, sql);
        metaDao.eliminarConfiguracion(nombreLogico);
        for (String uuid : listaUuids) {
            ficheroService.eliminar(uuid);
        }
    }

    /**
     * Devuelve la configuración completa de una tabla (columnas y relaciones).
     *
     * @param nombreLogico Nombre de la tabla en MySQL
     * @return Metadatos de la tabla
     */
    public TablaConfig obtenerDetalleTabla(String nombreLogico) {
        return metaDao.getConfiguracion(nombreLogico);
    }

    /**
     * Agrega una columna a una tabla
     *
     * @param nombreTabla Nombre de la tabla en MySQL
     * @param nuevaCol Metadatos de la nueva columna
     * @throws SQLException
     */
    public void agregarColumna(String nombreTabla, ColumnaConfig nuevaCol) throws SQLException {
        TablaConfig config = metaDao.getConfiguracion(nombreTabla);
        validador.validarColumnaNoExiste(config, nuevaCol.getNombre());
        validador.validarProteccionInterna(nuevaCol.getNombre());
        String sql = sqlService.generarAddColumnSql(nombreTabla, nuevaCol);
        sqlService.ejecutarSql(AppConfig.DB_CLIENTE, sql);
        // Gestionar metadatos
        if ("CONTRASENA".equalsIgnoreCase(nuevaCol.getTipo())) {
            nuevaCol.setContrasena(true);
            nuevaCol.setVisible(false);
            nuevaCol.setSensible(true);
        }
        if ("ARCHIVO".equalsIgnoreCase(nuevaCol.getTipo())) {
            nuevaCol.setArchivo(true);
        }
        config.getColumnas().add(nuevaCol);
        metaDao.guardarConfiguracion(config);
    }

    /**
     * Elimina la columna de una tabla
     *
     * @param nombreTabla Nombre de la tabla en MySQL
     * @param nombreColumna Nombre de la columna a eliminar
     * @throws SQLException
     */
    public void eliminarColumna(String nombreTabla, String nombreColumna) throws SQLException {
        TablaConfig config = metaDao.getConfiguracion(nombreTabla);
        validador.validarProteccionInterna(nombreColumna);
        validador.validarColumnaExiste(config, nombreColumna);
        String sql = sqlService.generarDropColumnSql(nombreTabla, nombreColumna);
        sqlService.ejecutarSql(AppConfig.DB_CLIENTE, sql);
        config.getColumnas().removeIf(c -> c.getNombre().equalsIgnoreCase(nombreColumna));
        metaDao.guardarConfiguracion(config);
    }

    /**
     * Renombra una columna
     *
     * @param nombreTabla Nombre de la tabla en MySQL
     * @param nombreViejo Nombre actual de la columna
     * @param nombreNuevo Nombre por el que se reemplazará el nombre de la
     * columna
     * @throws SQLException
     */
    public void renombrarColumna(String nombreTabla, String nombreViejo, String nombreNuevo) throws SQLException {
        TablaConfig config = metaDao.getConfiguracion(nombreTabla);
        validador.validarProteccionInterna(nombreViejo);
        validador.validarNombre(nombreNuevo);
        String sql = sqlService.generarRenameColumnSql(nombreTabla, nombreViejo, nombreNuevo);
        sqlService.ejecutarSql(AppConfig.DB_CLIENTE, sql);
        config.getColumnas().stream()
                .filter(c -> c.getNombre().equalsIgnoreCase(nombreViejo))
                .findFirst()
                .ifPresent(c -> c.setNombre(nombreNuevo));
        metaDao.guardarConfiguracion(config);
    }

    /**
     * Modifica una columna de una tabla
     *
     * @param nombreTabla Nombre de la tabla
     * @param colModificada Nombre de la columna
     */
    public void modificarColumna(String nombreTabla, ColumnaConfig colModificada) {
        TablaConfig config = metaDao.getConfiguracion(nombreTabla);
        validador.validarColumnaExiste(config, colModificada.getNombre());
        validador.validarProteccionInterna(colModificada.getNombre());
        String sql = sqlService.generarModifyColumnSql(nombreTabla, colModificada);
        try {
            sqlService.ejecutarSql(AppConfig.DB_CLIENTE, sql);
            for (int i = 0; i < config.getColumnas().size(); i++) {
                if (config.getColumnas().get(i).getNombre().equalsIgnoreCase(colModificada.getNombre())) {
                    config.getColumnas().set(i, colModificada);
                    break;
                }
            }
            metaDao.guardarConfiguracion(config);
        } catch (SQLException e) {
            procesarErrorMysql(e);
        }
    }

    // Traduce códigos de error MySQL a mensajes de validación amigables
    private void procesarErrorMysql(SQLException e) {
        int errorCode = e.getErrorCode();
        switch (errorCode) {
            case 1138:
                throw new ValidacionException("No se puede hacer la columna obligatoria (NOT NULL) porque ya existen registros con valores vacíos.");
            case 1265:
            case 1292:
            case 1366:
                throw new ValidacionException("No se puede cambiar el tipo de dato. Existen registros incompatibles con el nuevo formato.");
            case 1060:
                throw new ValidacionException("El nombre de la columna ya está en uso.");
            case 1025:
                throw new ValidacionException("No se puede modificar la columna porque es parte de una relación (Foreign Key). Elimine la relación primero.");
            default:
                throw new BaseDatosException("Error en la base de datos al modificar la tabla: " + e.getMessage(), e);
        }
    }

    public void crearRelacion(apigenerica.model.RelacionConfig rel) throws SQLException {
        metaDao.guardarRelacion(rel);
    }

    public List<apigenerica.model.RelacionConfig> listarRelaciones(long tablaId) throws SQLException {
        return metaDao.listarRelacionesPorTabla(tablaId);
    }

    public void eliminarRelacion(int idRelacion) throws SQLException {
        apigenerica.model.RelacionConfig rel = metaDao.getRelacionPorId(idRelacion);
        if (rel == null) {
            throw new apigenerica.excepciones.RecursoNoEncontradoException("Relación con id " + idRelacion + " no encontrada.");
        }
        metaDao.eliminarRelacion(idRelacion);
    }
}