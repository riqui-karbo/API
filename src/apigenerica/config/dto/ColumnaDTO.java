package apigenerica.config.dto;

public class ColumnaDTO {
    public String nombre;
    public String tipo;
    public boolean nullable = true;
    public boolean es_contrasena = false;
    public boolean es_visible = true;
    public boolean es_sensible = false;
    public boolean es_archivo = false;
    public boolean autoincremental = false;
    public boolean unico = false;
    public String valor_defecto;
    
    // Getters para Jackson
    public String getNombre() { return nombre; }
    public String getTipo() { return tipo; }
    public boolean isNullable() { return nullable; }
    public boolean isEs_contrasena() { return es_contrasena; }
    public boolean isEs_visible() { return es_visible; }
    public boolean isEs_sensible() { return es_sensible; }
    public boolean isEs_archivo() { return es_archivo; }
    public boolean isAutoincremental() { return autoincremental; }
    public boolean isUnico() { return unico; }
    public String getValor_defecto() { return valor_defecto; }
}