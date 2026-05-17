/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package core.util;

import apigenerica.config.AppConfig;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;


// Utilidad de cifrado AES-256 para proteger archivos antes de guardar en disco.
// Reemplaza SHA-256 (solo hash) por cifrado reversible.
// =============================================================================
// INTEGRACION PRUEBA: NUEVO SISTEMA DE ENCRIPTACION AES-256
// Reemplaza el SHA-256 anterior. Permite encriptar y desencriptar archivos.
// Puede ser eliminado o modificado despues de validar la funcionalidad.
// =============================================================================

public class Encriptador{
    
    private static final String CLAVE = AppConfig.AES_KEY;
    
    // =============================================================================
    // INICIO INTEGRACION: Metodos AES-256
    // =============================================================================
    // Encripta bytes con AES-256. Devuelve bytes encriptados.
    public static byte[] encriptar(byte[]datos){
        try{
           SecretKeySpec key = new SecretKeySpec(ajustarClave(CLAVE), "AES");
                    Cipher cipher = Cipher.getInstance("AES");
                    cipher.init(Cipher.ENCRYPT_MODE, key);
                    return cipher.doFinal(datos);
        } catch(Exception e){
            System.err.println("[AES] Error al encriptar: " + e.getMessage());
            return null;
        }
    }
    
    // Desencripta bytes con AES-256. Devuelve bytes originales
    public static byte[] desencriptar(byte[]datosEncriptados){
        try{
            SecretKeySpec key = new SecretKeySpec(ajustarClave(CLAVE), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            return cipher.doFinal(datosEncriptados);
        }catch (Exception e){
            System.err.println("[AES] Error al desencriptar: " + e.getMessage());
            return null;
        }
    }
    
    // Ajusta cualquier String a exactamente 32 bytes para AES-256 
    // para no tener problemas con el CLAVE que acepta 32 caractares exacto(32 bytes)
    private static byte[] ajustarClave(String clave){
        byte[] bytes = clave.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] ajustada = new byte[32];
        
        // Si es muy larga, cortara hasta llegar a 32 para asi no pete el sistema
        if (bytes.length >= 32){
            System.arraycopy(bytes, 0, ajustada, 0, 32);
        } else {
            
        // Y si es muy corta, los espacios que hay que llenar se llenara con espacios 
        System.arraycopy(bytes, 0, ajustada, 0, bytes.length);
        
            // Utilizamos un for para realizar los rellenos con espacios
            for (int i = bytes.length; i < 32; i++){
                ajustada[i] = ' ';
            }
        }
        return ajustada;
    }
}

