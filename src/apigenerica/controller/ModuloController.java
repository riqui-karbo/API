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
}
