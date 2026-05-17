package apigenerica.model;

/**
 * Representa los permisos CRUD de un rol sobre una tabla/sección concreta.
 *
 * @author Grupo1
 */
public class PermisoConfig {

    private String rol;
    private String tabla;
    private boolean puedeVer;
    private boolean puedeCrear;
    private boolean puedeEditar;
    private boolean puedeEliminar;

    public PermisoConfig() {}

    public PermisoConfig(String rol, String tabla,
                         boolean puedeVer, boolean puedeCrear,
                         boolean puedeEditar, boolean puedeEliminar) {
        this.rol = rol;
        this.tabla = tabla;
        this.puedeVer = puedeVer;
        this.puedeCrear = puedeCrear;
        this.puedeEditar = puedeEditar;
        this.puedeEliminar = puedeEliminar;
    }

    public String getRol()                 { return rol; }
    public void setRol(String rol)         { this.rol = rol; }

    public String getTabla()               { return tabla; }
    public void setTabla(String tabla)     { this.tabla = tabla; }

    public boolean isPuedeVer()                  { return puedeVer; }
    public void setPuedeVer(boolean puedeVer)     { this.puedeVer = puedeVer; }

    public boolean isPuedeCrear()                 { return puedeCrear; }
    public void setPuedeCrear(boolean puedeCrear) { this.puedeCrear = puedeCrear; }

    public boolean isPuedeEditar()                  { return puedeEditar; }
    public void setPuedeEditar(boolean puedeEditar) { this.puedeEditar = puedeEditar; }

    public boolean isPuedeEliminar()                    { return puedeEliminar; }
    public void setPuedeEliminar(boolean puedeEliminar) { this.puedeEliminar = puedeEliminar; }
}
