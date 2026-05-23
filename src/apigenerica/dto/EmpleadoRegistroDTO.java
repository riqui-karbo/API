package apigenerica.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO para la transferencia de datos en el registro de empleados.
 * Compatible tanto con snake_case como con camelCase.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmpleadoRegistroDTO {

    // Datos para erp_users
    private String email;
    private String contrasena;
    private String rol;

    // Datos empleados
    private String nombre;

    @JsonAlias({"primer_apellido", "primerApellido"})
    private String primerApellido;

    @JsonAlias({"segundo_apellido", "segundoApellido"})
    private String segundoApellido;

    @JsonAlias({"dni_nie", "dniNie"})
    private String dniNie;

    private String telefono;
    private String direccion;
    private String iban;
    private String nss;
    private String cargo;

    @JsonAlias({"foto_url", "fotoUrl"})
    private String fotoUrl;

    public EmpleadoRegistroDTO() {}

    // =========================
    // GETTERS Y SETTERS
    // =========================

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

    public String getSegundoApellido() {
        return segundoApellido;
    }

    public void setSegundoApellido(String segundoApellido) {
        this.segundoApellido = segundoApellido;
    }

    public String getDniNie() {
        return dniNie;
    }

    public void setDniNie(String dniNie) {
        this.dniNie = dniNie;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getIban() {
        return iban;
    }

    public void setIban(String iban) {
        this.iban = iban;
    }

    public String getNss() {
        return nss;
    }

    public void setNss(String nss) {
        this.nss = nss;
    }

    public String getCargo() {
        return cargo;
    }

    public void setCargo(String cargo) {
        this.cargo = cargo;
    }

    public String getFotoUrl() {
        return fotoUrl;
    }

    public void setFotoUrl(String fotoUrl) {
        this.fotoUrl = fotoUrl;
    }

    public String getFotoUrlNormalizada() {
        return fotoUrl;
    }
}