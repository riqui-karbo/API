package apigenerica.config.dto;
import java.util.List;

public class SchemaConfig {
    public List<ModuloDTO> modulos;
    public List<TablaDTO> tablas;
    
    public List<ModuloDTO> getModulos() { return modulos; }
    public List<TablaDTO> getTablas() { return tablas; }
}