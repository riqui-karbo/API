package apigenerica.config.dto;
import java.util.List;

public class TablaDTO {
    public String modulo;
    public String nombre_logico;
    public String nombre_amigable;
    public List<ColumnaDTO> columnas;
    
    public String getModulo() { return modulo; }
    public String getNombre_logico() { return nombre_logico; }
    public String getNombre_amigable() { return nombre_amigable; }
    public List<ColumnaDTO> getColumnas() { return columnas; }
}