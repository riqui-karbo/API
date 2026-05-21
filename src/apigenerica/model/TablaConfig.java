/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.model;

import java.util.List;

/**
 * @author Grupo1
 * Metadatos de las tablas de la base de datos
 */
public class TablaConfig {
    Long id;
    private Long moduloId;
    private String imagenUuid;
    String nombreLogico;
    String nombreAmigable;
    List<ColumnaConfig> columnas;
    List<RelacionConfig> relaciones;

    // Setters y getters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getModuloId() {
        return moduloId;
    }

    public void setModuloId(Long moduloId) {
        this.moduloId = moduloId;
    }

    public String getNombreLogico() {
        return nombreLogico;
    }

    public void setNombreLogico(String nombreLogico) {
        this.nombreLogico = nombreLogico;
    }

    public String getNombreAmigable() {
        return nombreAmigable;
    }

    public void setNombreAmigable(String nombreAmigable) {
        this.nombreAmigable = nombreAmigable;
    }

    public List<ColumnaConfig> getColumnas() {
        return columnas;
    }

    public void setColumnas(List<ColumnaConfig> columnas) {
        this.columnas = columnas;
    }

    public List<RelacionConfig> getRelaciones() {
        return relaciones;
    }

    public void setRelaciones(List<RelacionConfig> relaciones) {
        this.relaciones = relaciones;
    }
    
    public String getImagenUuid() { return imagenUuid; }
    public void setImagenUuid(String imagenUuid) { this.imagenUuid = imagenUuid; }
}