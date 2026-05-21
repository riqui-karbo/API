package apigenerica.controller;

import apigenerica.config.ConexionMysql;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.model.ApiRespuesta;
import io.javalin.http.Context;
import io.javalin.http.HttpCode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador para la gestión de módulos instalados en el ERP.
 *
 * @author Grupo1
 */
public class ModuloController {

    /**
     * Devuelve los módulos existentes
     * 
     * @param ctx Contexto de la petición HTTP
     */
    public void getAll(Context ctx) {
        String sql = "SELECT * FROM `erp_modulos` ORDER BY orden ASC";
        List<Map<String, Object>> modulos = new ArrayList<>();

        try (Connection conn = ConexionMysql.getConexion("erp_sistema");
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> modulo = new LinkedHashMap<>();
                modulo.put("id", rs.getInt("id"));
                modulo.put("nombre", rs.getString("nombre"));
                modulo.put("icono", rs.getString("icono"));
                modulo.put("iconType", rs.getString("icon_type"));
                modulo.put("habilitado", rs.getBoolean("habilitado"));
                modulo.put("orden", rs.getInt("orden"));
                modulos.add(modulo);
            }
            ctx.json(ApiRespuesta.ok(modulos));

        } catch (SQLException e) {
            throw new BaseDatosException("Error al obtener módulos.", e);
        }
    }

    /**
     * Crear un nuevo módulo
     * 
     * @param ctx Contexto de la petición HTTP
     */
    @SuppressWarnings("unchecked")
    public void create(Context ctx) {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String sql = "INSERT INTO `erp_modulos` (nombre, icono, icon_type, habilitado, orden) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = ConexionMysql.getConexion("erp_sistema");
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, (String) body.getOrDefault("nombre", "Módulo Nuevo"));
            stmt.setString(2, (String) body.getOrDefault("icono", "📦"));
            stmt.setString(3, (String) body.getOrDefault("iconType", "emote"));
            stmt.setBoolean(4, (Boolean) body.getOrDefault("habilitado", true));
            stmt.setInt(5, ((Number) body.getOrDefault("orden", 0)).intValue());

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    ctx.status(HttpCode.CREATED).json(ApiRespuesta.ok(rs.getInt(1)));
                } else {
                    ctx.status(500).json(ApiRespuesta.error("No se pudo obtener el ID del módulo."));
                }
            }

        } catch (SQLException e) {
            throw new BaseDatosException("Error al crear módulo.", e);
        }
    }

    /**
     * Borrar un módulo a partir de su ID
     * 
     * @param ctx Contexto de la petición HTTP
     */
    public void delete(Context ctx) {
        String idStr = ctx.pathParam("id");
        String sql = "DELETE FROM `erp_modulos` WHERE id = ?";

        try (Connection conn = ConexionMysql.getConexion("erp_sistema");
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, Integer.parseInt(idStr));
            int rows = stmt.executeUpdate();

            if (rows > 0) {
                ctx.json(ApiRespuesta.ok("Módulo eliminado."));
            } else {
                ctx.status(404).json(ApiRespuesta.error("Módulo no encontrado."));
            }

        } catch (SQLException | NumberFormatException e) {
            throw new BaseDatosException("Error al eliminar módulo.", e);
        }
    }
    
    /**
     * Obtiene la lista de tablas asociadas a un módulo específico
     * Se filtra la tabla 'erp_meta_tablas' mediante el parámetro 'modulo_id'
     * @param ctx Contexto de la petición HTTP
     */
    public void getTablasByModulo(Context ctx) {
        //Recorre el parámetro que viene de PHP
        String moduloId = ctx.queryParam("modulo_id");
        
        if (moduloId == null || !moduloId.matches("\\d+")){
            ctx.status(400).json(ApiRespuesta.error("El parámetro modulo_id es obligatorio."));
            return;
        }
        
        String sql = "SELECT * FROM `erp_meta_tablas` WHERE modulo_id = ?";
        List<Map<String, Object>> tablas = new ArrayList<>();
        
        try (Connection conn = ConexionMysql.getConexion("erp_sistema");
            PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, Integer.parseInt(moduloId)); //Convierte el string a numero para el SQL
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> tabla = new LinkedHashMap<>();
                        tabla.put("id", rs.getInt("id"));
                        tabla.put("nombre_logico", rs.getString("nombre_logico"));
                        tabla.put("nombre_amigable", rs.getString("nombre_amigable"));
                        tablas.add(tabla);
                    }
                }
                ctx.json(ApiRespuesta.ok(tablas));
        }catch (SQLException | NumberFormatException e) {
            //Si hay un error lo lanzamos para que Javalin lo gestione
            System.err.println("CRITICAL DB ERROR: " + e.getMessage());
            throw new BaseDatosException("Error al obtener tablas del módulo.", e);
        }
    }
}