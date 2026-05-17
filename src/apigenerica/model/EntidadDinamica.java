/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.model;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Grupo1
 * Representa una fila de una tabla como un mapa de clave-valor
 */
public class EntidadDinamica {

    // Nombre de la colummna y valor
    private Map<String, Object> valores = new HashMap<>();
    // Nombre de la columna de la PrimaryKey
    private static final String CAMPO_ID = "id";

    // Devuelve el ID de la colección (columna con nombre "id")
    public Long getId() {
        Object id = valores.get(CAMPO_ID);
        return (id != null) ? ((Number) id).longValue() : null;
    }

    // Guarda el ID dentro de la colección
    public void setId(Long id) {
        valores.put(CAMPO_ID, id);
    }

    // Guarda un dato en la colección
    public void set(String columna, Object valor) {
        valores.put(columna, valor);
    }

    // Devuelve un dato de la colección
    public Object get(String columna) {
        return valores.get(columna);
    }

    // Devuelve toda la colección
    public Map<String, Object> getTodo() {
        return valores;
    }
}
