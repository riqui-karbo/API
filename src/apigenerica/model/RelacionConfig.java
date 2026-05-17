/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.model;

/**
 * @author Grupo1
 * Entidad que representa una relación entre dos tablas
 */
public class RelacionConfig {
    private String nombreRelacion;
    private String tablaOrigen;
    private String fkColumna;
    private String tablaDestino;
    private String cardinalidad;
    
    // Getters y setters
    public String getNombreRelacion() {
        return nombreRelacion;
    }

    public void setNombreRelacion(String nombreRelacion) {
        this.nombreRelacion = nombreRelacion;
    }

    public String getTablaOrigen() {
        return tablaOrigen;
    }

    public void setTablaOrigen(String tablaOrigen) {
        this.tablaOrigen = tablaOrigen;
    }

    public String getFkColumna() {
        return fkColumna;
    }

    public void setFkColumna(String fkColumna) {
        this.fkColumna = fkColumna;
    }

    public String getTablaDestino() {
        return tablaDestino;
    }

    public void setTablaDestino(String tablaDestino) {
        this.tablaDestino = tablaDestino;
    }

    public String getCardinalidad() {
        return cardinalidad;
    }

    public void setCardinalidad(String cardinalidad) {
        this.cardinalidad = cardinalidad;
    }
    
}
