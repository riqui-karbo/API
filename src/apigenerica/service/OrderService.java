/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.service;

/**
 *
 * @author Grupo1
 * 
 * Ordena las tablas recibidas en el JSON
 * cuando el usuario selecciona varias tablas
 * para un JOIN
 */
import apigenerica.dao.MetaDao;
import apigenerica.model.RelacionConfig;
import apigenerica.model.TablaConfig;
import java.util.*;

public class OrderService {

    private final MetaDao metaDao;

    public OrderService(MetaDao metaDao) {
        this.metaDao = metaDao;
    }

    public List<String> ordenarTablas(List<String> tablasSolicitadas) {
        // Mapa de dependencias: Tabla y lista de tablas que dependen de ella (hijos)
        Map<String, List<String>> adyacencia = new HashMap<>();
        // Contador de padres: Tabla y número de padres que hay en la lista
        Map<String, Integer> gradosEntrada = new HashMap<>();
        
        // Inicializar mapas
        for (String tabla : tablasSolicitadas) {
            adyacencia.put(tabla, new ArrayList<>());
            gradosEntrada.put(tabla, 0);
        }

        // Construir el grafo basado en los metadatos de MySQL
        for (String tablaNombre : tablasSolicitadas) {
            TablaConfig config = metaDao.getConfiguracion(tablaNombre);
            if (config != null && config.getRelaciones() != null) {
                for (RelacionConfig rel : config.getRelaciones()) {
                    String padre = rel.getTablaDestino();
                    
                    // Solo si el padre está entre las tablas seleccionadas
                    if (tablasSolicitadas.contains(padre)) {
                        adyacencia.get(padre).add(tablaNombre); // El padre apunta a la hija
                        gradosEntrada.put(tablaNombre, gradosEntrada.get(tablaNombre) + 1);
                    }
                }
            }
        }

        // Añadir tablas a la cola para ser procesadas, primero las que no tienen padres
        Queue<String> cola = new LinkedList<>();
        for (String tabla : tablasSolicitadas) {
            if (gradosEntrada.get(tabla) == 0) {
                cola.add(tabla);
            }
        }

        // Tablas ordenadas
        List<String> resultado = new ArrayList<>();
        while (!cola.isEmpty()) {
            String actual = cola.poll();
            resultado.add(actual);

            // Reducir el grado de entrada de sus hijos
            for (String hijo : adyacencia.get(actual)) {
                gradosEntrada.put(hijo, gradosEntrada.get(hijo) - 1);
                // Si el hijo ya no tiene más padres pendientes, a la cola
                if (gradosEntrada.get(hijo) == 0) {
                    cola.add(hijo);
                }
            }
        }

        // Comprobar si hay un ciclo (ej: A depende de B y B de A)
        if (resultado.size() != tablasSolicitadas.size()) {
            throw new RuntimeException("Error: Se ha detectado una dependencia circular entre las tablas.");
        }

        return resultado;
    }
}
