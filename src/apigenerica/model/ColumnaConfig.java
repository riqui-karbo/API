/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.model;

/**
 * @author Grupo1
 * Metadatos de las columnas de la base de datos
 */
public class ColumnaConfig {
    Long id;
    String nombre;
    String tipo;
    boolean nullable = true;
    boolean contrasena = false; // Hashear antes de insertar en MySQL
    boolean visible = true; // Para el DTO
    boolean sensible = false; // Enviar dato a Paradox
    boolean archivo = false; // Enviar dato a db4o
    boolean autoincremental;
    boolean unico;
    Object valorDefecto;

    // Getters y setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public boolean isNullable() {
        return nullable;
    }

    public void setNullable(boolean nullable) {
        this.nullable = nullable;
    }

    public boolean isContrasena() {
        return contrasena;
    }

    public void setContrasena(boolean contrasena) {
        this.contrasena = contrasena;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isSensible() {
        return sensible;
    }

    public void setSensible(boolean sensible) {
        this.sensible = sensible;
    }

    public boolean isArchivo() {
        return archivo;
    }

    public void setArchivo(boolean archivo) {
        this.archivo = archivo;
    }

    public boolean isAutoincremental() {
        return autoincremental;
    }

    public void setAutoincremental(boolean autoincremental) {
        this.autoincremental = autoincremental;
    }

    public boolean isUnico() {
        return unico;
    }

    public void setUnico(boolean unico) {
        this.unico = unico;
    }

    public Object getValorDefecto() {
        return valorDefecto;
    }

    public void setValorDefecto(String valorDefecto) {
        this.valorDefecto = valorDefecto;
    }
}
