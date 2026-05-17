/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.excepciones;

/**
 *
 * @author Grupo1
 * Define un error de base de datos (Error de servidor)
 */
public class BaseDatosException extends RuntimeException {
    public BaseDatosException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }  
}
