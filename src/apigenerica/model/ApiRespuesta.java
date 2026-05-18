/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.model;

/**
 * @author Grupo1
 * Representa una respuesta de la API.
 * {@code exito} - indica si la operación se completó correctamente
 * {@code mensaje} - descripción del resultado o del error
 * {@code datos} - objeto devuelto en caso de éxito, null en caso de error
 * 
 * @param <T> Tipo del objeto devuelto en {@code datos}
 */
public class ApiRespuesta<T> {
    private boolean exito;
    private String mensaje;
    private T datos;

    private ApiRespuesta(boolean exito, String mensaje, T datos) {
        this.exito = exito;
        this.mensaje = mensaje;
        this.datos = datos;
    }

    public static <T> ApiRespuesta<T> ok(T datos) {
        return new ApiRespuesta<>(true, null, datos);
    }

    public static ApiRespuesta<Void> ok(String mensaje) {
        return new ApiRespuesta<>(true, mensaje, null);
    }

    public static ApiRespuesta<Void> error(String mensaje) {
        return new ApiRespuesta<>(false, mensaje, null);
    }

    // Getters y setters
    public boolean isExito() {
        return exito;
    }

    public void setExito(boolean exito) {
        this.exito = exito;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public T getDatos() {
        return datos;
    }

    public void setDatos(T datos) {
        this.datos = datos;
    }

}
