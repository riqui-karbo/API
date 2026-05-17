/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.model;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * @author Grupo1
 * Representa una operación pendiente de ser realizada en 
 * MySQL, que se almacenará en Paradox de forma temporal
 */
public class OperacionPendiente {
    private Long id;
    private String operacion; // INSERT, UPDATE, DELETE
    private String nombreDb;
    private String nombreTabla;
    private Map<String, Object> payload; // JSON de la operación
    private LocalDateTime timestamp;

    // Getters y setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOperacion() {
        return operacion;
    }

    public void setOperacion(String operacion) {
        this.operacion = operacion;
    }

    public String getNombreDb() {
        return nombreDb;
    }

    public void setNombreDb(String nombreDb) {
        this.nombreDb = nombreDb;
    }

    public String getNombreTabla() {
        return nombreTabla;
    }

    public void setNombreTabla(String nombreTabla) {
        this.nombreTabla = nombreTabla;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    
}
