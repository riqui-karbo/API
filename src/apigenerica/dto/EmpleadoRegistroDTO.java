/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.dto;

/**
 * DTO para la transferencia de datos en el registro de empleados.
 * @author Grupo1
 */
public class EmpleadoRegistroDTO {
    
    // Datos para erp_users (Base de datos Sistema)
    private String email;
    private String contrasena;
    private String rol;

    // Datos para empleados (Base de datos Cliente)
    private String nombre;
    private String primerApellido;
    private String dniNie;
    private String cargo;

    // --- Constructor vacío requerido por Spring para mapear el JSON ---
    public EmpleadoRegistroDTO() {
    }

    // --- Getters y Setters exactos ---
    public String getEmail() { 
        return email; 
    }
    public void setEmail(String email) { 
        this.email = email; 
    }

    public String getContrasena() { 
        return contrasena; 
    }
    public void setContrasena(String contrasena) { 
        this.contrasena = contrasena; 
    }

    public String getRol() { 
        return rol; 
    }
    public void setRol(String rol) { 
        this.rol = rol; 
    }

    public String getNombre() { 
        return nombre; 
    }
    public void setNombre(String nombre) { 
        this.nombre = nombre; 
    }

    public String getPrimerApellido() { 
        return primerApellido; 
    }
    public void setPrimerApellido(String primerApellido) { 
        this.primerApellido = primerApellido; 
    }

    public String getDniNie() { 
        return dniNie; 
    }
    public void setDniNie(String dniNie) { 
        this.dniNie = dniNie; 
    }

    public String getCargo() { 
        return cargo; 
    }
    public void setCargo(String cargo) { 
        this.cargo = cargo; 
    }
}