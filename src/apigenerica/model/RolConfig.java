package apigenerica.model;

/**
 * Representa un rol del sistema ERP.
 *
 * @author Grupo1
 */
public class RolConfig {

    private Long id;
    private String nombre;
    private String descripcion;

    public RolConfig() {}

    public RolConfig(Long id, String nombre, String descripcion) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public Long getId()              { return id; }
    public void setId(Long id)       { this.id = id; }

    public String getNombre()             { return nombre; }
    public void setNombre(String nombre)  { this.nombre = nombre; }

    public String getDescripcion()              { return descripcion; }
    public void setDescripcion(String d)        { this.descripcion = d; }
}
