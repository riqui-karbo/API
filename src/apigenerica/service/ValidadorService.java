/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.service;

import apigenerica.dao.MetaDao;
import apigenerica.excepciones.ValidacionException;
import apigenerica.model.ColumnaConfig;
import apigenerica.model.ApiRequest;
import apigenerica.model.RelacionConfig;
import apigenerica.model.TablaConfig;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Grupo1 
 * Lógica de validaciones
 */
public class ValidadorService {

    private final MetaDao metaDao;

    public ValidadorService(MetaDao metaDao) {
        this.metaDao = metaDao;
    }

    /**
     * Comprueba que el nombre no esté vacío
     *
     * @param nombre Nombre lógico
     */
    public void validarNombre(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new ValidacionException("El nombre es obligatorio.");
        }

        if (!nombre.matches("^[a-zA-Z0-9_]+$")) {
            throw new ValidacionException("El nombre contiene caracteres no válidos.");
        }
    }

    /**
     * Comprueba que una tabla no tenga columnas con nombres duplicados
     *
     * @param cols Metadatos de las columnas
     */
    public void validarColumnasUnicas(List<ColumnaConfig> cols) {
        Set<String> nombres = new HashSet<>();
        for (ColumnaConfig c : cols) {
            if (!nombres.add(c.getNombre().toLowerCase())) {
                throw new ValidacionException("La columna '" + c.getNombre() + "' está duplicada.");
            }
        }
    }

    /**
     * Valida que una columna no exista ya en la tabla
     *
     * @param tabla Tabla, para obtener la lista de columnas
     * @param nombreColumna Nombre de la columna a validar
     */
    public void validarColumnaNoExiste(TablaConfig tabla, String nombreColumna) {
        boolean existe = tabla.getColumnas().stream()
                .anyMatch(c -> c.getNombre().equalsIgnoreCase(nombreColumna));
        if (existe) {
            throw new ValidacionException("La columna '" + nombreColumna + "' ya existe en la tabla '" + tabla.getNombreLogico() + "'.");
        }
    }
    
    /**
     * Valida que una columna exista en la tabla
     * 
     * @param tabla Tabla, para obtener la lista de columnas
     * @param nombreColumna Nombre de la columna a validar
     */
    public void validarColumnaExiste(TablaConfig tabla, String nombreColumna) {
        boolean existe = tabla.getColumnas().stream()
                .anyMatch(c -> c.getNombre().equalsIgnoreCase(nombreColumna));
        if (!existe) {
            throw new ValidacionException("La columna '" + nombreColumna + "' no existe en la tabla '" + tabla.getNombreLogico() + "'.");
        }
    }

    /**
     * Aplica reglas de validación sobre los metadatos recibidos en el JSON
     *
     * @param request Request enviada por el cliente
     */
    public void validarMetadata(ApiRequest request) {
        if (request.getModuloId() == null || request.getModuloId() <= 0) {
            throw new ValidacionException("El ID del módulo es obligatorio y debe ser válido.");
        }
        if (request.getTabla() == null || request.getTabla().isEmpty()) {
            throw new ValidacionException("Debe proporcionar al menos una tabla.");
        }
        for (TablaConfig t : request.getTabla()) {
            validarNombre(t.getNombreLogico());
            if (t.getColumnas() == null || t.getColumnas().isEmpty()) {
                throw new ValidacionException("La tabla " + t.getNombreLogico() + " no tiene columnas.");
            }
            validarColumnasUnicas(t.getColumnas());
        // Validar relaciones de esta tabla
        if (t.getRelaciones() != null && !t.getRelaciones().isEmpty()) {
                validarRelaciones(t, t.getRelaciones());
            }
        }
    }

    /**
     * Comprueba si la relación entre una lista de tablas existe en los
     * metadatos
     *
     * @param tablas Lista de tablas
     */
    public void validarRelacionesExisten(List<String> tablas) {
        for (String tabla : tablas) {
            TablaConfig config = metaDao.getConfiguracion(tabla);
            if (config == null) {
                throw new ValidacionException("No hay metadatos configurados para la tabla: " + tabla);
            }
        }
    }

    /**
     * Valida que las relaciones apunten a tablas y columnas existentes en la
     * petición
     *
     * @param relaciones Metadatos de las relaciones
     */
    private void validarRelaciones(TablaConfig origen, List<RelacionConfig> relaciones) {
        for (RelacionConfig rel : relaciones) {
            // Verificar que la columna FK exista en la tabla origen
            boolean existeCol = origen.getColumnas().stream()
                    .anyMatch(c -> c.getNombre().equalsIgnoreCase(rel.getFkColumna()));

            if (!existeCol) {
                throw new ValidacionException("La columna FK '" + rel.getFkColumna()
                        + "' no existe en la tabla '" + origen.getNombreAmigable() + "'");
            }
        }
    } 
    
    /**
     * Bloquea acciones sobre la base de datos del sistema o la columna ID primaria
     * 
     * @param columna Nombre de la columna
     */
    public void validarProteccionInterna(String columna) {
        if (columna != null && "id".equalsIgnoreCase(columna)) {
            throw new ValidacionException("La columna 'id' no puede ser modificada o eliminada.");
        }
    }
}
