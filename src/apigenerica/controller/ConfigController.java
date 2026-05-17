package apigenerica.controller;

import apigenerica.config.ConexionMysql;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.model.ApiRespuesta;
import io.javalin.http.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controlador para la configuración global del ERP (Logo, colores, etc).
 *
 * @author Grupo1
 */
public class ConfigController {

    /**
     * Endpoint: GET /api/erp/config
     */
    public void getConfig(Context ctx) {
        String sql = "SELECT * FROM `erp_config`";
        Map<String, String> config = new LinkedHashMap<>();

        try (Connection conn = ConexionMysql.getConexion("erp_sistema");
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
             
            while (rs.next()) {
                config.put(rs.getString("clave"), rs.getString("valor"));
            }
            ctx.json(ApiRespuesta.ok(config));

        } catch (SQLException e) {
            throw new BaseDatosException("Error al obtener la configuración.", e);
        }
    }

    /**
     * Endpoint: PUT /api/erp/config
     */
    @SuppressWarnings("unchecked")
    public void updateConfig(Context ctx) {
        Map<String, String> nuevosValores = ctx.bodyAsClass(Map.class);
        String sql = "INSERT INTO `erp_config` (clave, valor) VALUES (?, ?) ON DUPLICATE KEY UPDATE valor = ?";

        try (Connection conn = ConexionMysql.getConexion("erp_sistema")) {
            conn.setAutoCommit(false);
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (Map.Entry<String, String> entry : nuevosValores.entrySet()) {
                    stmt.setString(1, entry.getKey());
                    stmt.setString(2, entry.getValue());
                    stmt.setString(3, entry.getValue());
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
            
            conn.commit();
            ctx.json(ApiRespuesta.ok("Configuración actualizada correctamente."));

        } catch (SQLException e) {
            throw new BaseDatosException("Error al actualizar la configuración.", e);
        }
    }
}
