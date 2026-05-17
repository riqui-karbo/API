/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica;

import apigenerica.excepciones.ValidacionException;
import apigenerica.model.ColumnaConfig;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.mindrot.jbcrypt.BCrypt;

/**
 * @author Grupo1 Convertir tipo de dato del formulario a SQL y viceversa;
 * convertir a tipo de dato Java
 */
public class TipoDatoMapper {

    public static String toTexto(int tipo) {
        switch (tipo) {
            case Types.INTEGER:
            case Types.BIGINT:
                return "ENTERO";
            case Types.DECIMAL:
            case Types.DOUBLE:
            case Types.FLOAT:
                return "DECIMAL";
            case Types.VARCHAR:
            case Types.CHAR:
                return "TEXTO_CORTO";
            case Types.LONGNVARCHAR:
            case Types.CLOB:
                return "TEXTO_LARGO";
            default:
                return "TEXTO_CORTO";
        }
    }

    public static String toSql(String tipo) {
        switch (tipo.toUpperCase()) {
            case "TEXTO_CORTO":
                return "VARCHAR(255)";
            case "CONTRASENA":
                return "CHAR(60)"; // Hash BCrypt
            case "TEXTO_LARGO":
                return "TEXT";
            case "ENTERO":
                return "INT";
            case "DECIMAL":
                return "DECIMAL(10,2)";
            case "FECHA_HORA":
                return "DATETIME";
            case "FECHA":
                return "DATE";
            case "BINARIO":
                return "TINYINT(1)";
            case "ARCHIVO":
                return "VARCHAR(255)";
            default:
                return "VARCHAR(255)";
        }
    }

    /**
     * Validar y convertir tipo de dato en texto plano, utilizado en el
     * formulario de creación y edición de tablas, a tipo de dato de Java
     *
     * @param valorSinProc valor antes de validación y conversión
     * @param conf configuración de las columnas de la tabla
     * @return tipo de dato Java
     * @throws Exception
     */
    public static Object toJava(Object valorSinProc, ColumnaConfig conf) throws Exception {
        // Validación datos obligatorios (Not Nullable)
        if (valorSinProc == null) {
            if (!conf.isNullable()) {
                throw new ValidacionException("El campo " + conf.getNombre() + " no puede ser nulo.");
            }
            return null; // No hay dato, salir del método
        }
        String tipo = conf.getTipo().toUpperCase();
        String valorStr = valorSinProc.toString();

        switch (tipo) {
            case "ENTERO":
                try {
                    return Integer.valueOf(valorStr);
                } catch (NumberFormatException e) {
                    throw new Exception("El valor no es un entero válido");
                }
            case "CONTRASENA":
                return BCrypt.hashpw(valorStr, BCrypt.gensalt()); // Hashear
            case "DECIMAL":
                return Double.valueOf(valorStr);
            case "BINARIO":
                if (valorStr.equalsIgnoreCase("true") || valorStr.equals("1")) {
                    return true;
                }
                if (valorStr.equalsIgnoreCase("false") || valorStr.equals("0")) {
                    return false;
                }
                throw new Exception("Formato booleano inválido");
            case "FECHA":
                return LocalDate.parse(valorStr); // Formato YYYY-MM-DD
            case "FECHA_HORA":
                return LocalDateTime.parse(valorStr);
            case "TEXTO_CORTO":
            case "TEXTO_LARGO":
            case "ARCHIVO":
            default:
                return valorStr;
        }
    }
}
