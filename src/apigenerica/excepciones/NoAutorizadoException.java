/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.excepciones;

/**
 *
 * @author Grupo1
 * Define un error de base de datos (Falta de permisos)
 */
public class NoAutorizadoException extends RuntimeException {
    public NoAutorizadoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }  
}
