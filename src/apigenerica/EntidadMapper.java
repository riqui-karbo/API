/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica;

import apigenerica.model.ColumnaConfig;
import apigenerica.model.EntidadDinamica;
import apigenerica.model.RelacionConfig;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Grupo1 
 * Convertir resultados de una consulta SQL en un objeto EntidadDinamica
 */
public class EntidadMapper {

    public EntidadDinamica mapear(ResultSet resultSet, List<ColumnaConfig> columnas) throws SQLException {
        EntidadDinamica entidad = new EntidadDinamica();

        // Mapear el ID (Columna "id") — tolerante si la tabla no tiene columna "id"
        try {
            entidad.setId(resultSet.getLong("id"));
        } catch (SQLException e) {
            // La tabla no tiene columna "id"; intentar usar la primera columna como ID
            if (!columnas.isEmpty()) {
                try {
                    Object primerValor = resultSet.getObject(columnas.get(0).getNombre());
                    if (primerValor instanceof Number) {
                        entidad.setId(((Number) primerValor).longValue());
                    }
                } catch (Exception ignored) { }
            }
        }
    
        // Extraer nombres de las columnas de la tabla de ColumnaConfig
        for (ColumnaConfig col : columnas) {
            // Mapear columnas
            entidad.set(col.getNombre(), resultSet.getObject(col.getNombre()));
        }
        return entidad;
    }
    
    public void agregarHijos(ResultSet resultSet, String tablaPrincipal, EntidadDinamica padre,
        List<RelacionConfig> relaciones, Map<String, List<ColumnaConfig>> colsHijas) throws SQLException {

        for (RelacionConfig rel : relaciones) {
            // Buscar tabla origen y unirla a la de destino
            boolean isOrigen = rel.getTablaOrigen().equalsIgnoreCase(tablaPrincipal);
            String tablaAUnir = isOrigen ? rel.getTablaDestino() : rel.getTablaOrigen();

            // Obtener prefijo
            String prefijo = tablaAUnir + "_";

            // Obtener columnas de la tabla hijo
            List<ColumnaConfig> columnasHijo = colsHijas.get(tablaAUnir);
            if (columnasHijo == null) continue;

            Map<String, Object> hijo = new HashMap<>();
            boolean tieneDatos = false; // Para controlar si el LEFT JOIN es null

            // Extraer datos del hijo
            for (ColumnaConfig col : columnasHijo) {
                // El nombre de la columna en ResultSet (prefijo_nombreCol)
                String nombreColumna = prefijo + col.getNombre();
                Object valor = resultSet.getObject(nombreColumna);
                hijo.put(col.getNombre(), valor);

                // Si algún campo no es null, la fila tiene datos
                if (valor != null) tieneDatos = true;
            }

            if (isOrigen) {
                // Relación N:1 o 1:1, el padre solo tiene un hijo
                if (tieneDatos) {
                    // Guardar hijo dentro del padre
                    padre.set(tablaAUnir, hijo);
                } else {
                    // Si el LEFT JOIN no encontró nada
                    padre.set(tablaAUnir, null);
                }
            } else {
                // Relación 1:N, el padre tiene una lista de hijos
                Object valorActual = padre.getTodo().get(tablaAUnir);
                List<Map<String, Object>> listaHijos;

                // Inicializar la lista la primera vez
                if (valorActual == null) {
                    listaHijos = new ArrayList<>();
                    padre.set(tablaAUnir, listaHijos);
                } else {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> mapAList = (List<Map<String, Object>>) valorActual;
                    listaHijos = mapAList;
                }

                // Solo agregar si hay datos y evitar duplicados (por si hay múltiples JOINs)
                if (tieneDatos) {
                    Long idHijo = (hijo.get("id") != null) ? ((Number) hijo.get("id")).longValue() : null;
                    boolean yaExiste = false;

                    // Revisar si ya hay un hijo con ese ID
                    for (Map<String, Object> h : listaHijos) {
                        Long idExistente = (h.get("id") != null) ? ((Number) h.get("id")).longValue() : null;
                        if (idExistente != null && idExistente.equals(idHijo)) {
                            yaExiste = true;
                            break;
                        }
                    }

                    if (!yaExiste) {
                        listaHijos.add(hijo);
                    }
                }
            }
        }
    }
}
