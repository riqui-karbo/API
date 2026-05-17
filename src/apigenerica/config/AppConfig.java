/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.config;

/**
 *
 * @author Grupo1
 */
public class AppConfig {

    public static final String DB_SISTEMA = "erp_sistema";

    // Estas variables se llenan al iniciar la API (desde un .env, properties o el instalador)
    // public static String DB_CLIENTE = System.getenv("DB_CLIENTE"); 
    public static final String DB_CLIENTE = "erp_empresa";
    // private static final String SECRET_KEY = System.getenv("SECRET_KEY");
    private static final String SECRET_KEY = "clave_secreta_de_prueba"; // Firma y verifica los tokens

    // Getter
    public static String getSecretKey() {
        return SECRET_KEY;
    }
    
    //  NUEVO: clave AES para cifrado de ficheros 
    // Se lee de la variable de entorno ERP_AES_KEY si está definida (producción).
    // Si no, cae al valor de desarrollo. Cambiarlo en producción sin recompilar.
    // IMPORTANTE: debe tener exactamente 16, 24 o 32 caracteres para AES-128/192/256.
    public static final String AES_KEY = System.getenv("ERP_AES_KEY") != null
            ? System.getenv("ERP_AES_KEY")
            : "ErpClaveSegura32CaracteresExact!"; // 32 chars → AES-256
}
