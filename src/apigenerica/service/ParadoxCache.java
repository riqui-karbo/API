/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.service;

import apigenerica.model.TablaConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Grupo1
 */
public class ParadoxCache {

    private final Connection conexion;
    private final ReentrantLock lock = new ReentrantLock(); // Para bloquear las conexiones
    private final ObjectMapper jackson = new ObjectMapper(); // ObjectMapper de Jackson

    public ParadoxCache(String ruta) throws SQLException {
        String rutaNorm = new java.io.File(ruta).getAbsolutePath().replace("\\", "/");
        this.conexion = DriverManager.getConnection("jdbc:paradox:///" + rutaNorm);
        asegurarTablaMeta();
    }

    /**
     * Crea la tabla meta_cache si no existe todavía.
     * Usa el mismo patrón que ParadoxWorker: intenta CREATE TABLE y captura
     * la excepción "ya existe" en lugar de consultar getTables() (poco fiable
     * con el driver HXTT evaluación).
     */
    private void asegurarTablaMeta() {
        try (java.sql.Statement stmt = conexion.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE meta_cache (" +
                "nombre_logico VARCHAR(150)," +
                "datos         VARCHAR(255)" +
                ")"
            );
            System.out.println("[ParadoxCache] Tabla meta_cache creada por primera vez.");
        } catch (SQLException e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("already") || msg.contains("exist") || msg.contains("duplicate")) {
                return; // La tabla ya existía — OK
            }
            System.err.println("[ParadoxCache] Aviso al crear meta_cache: " + e.getMessage());
        }
    }

    public Optional<TablaConfig> get(String nombreLogico) throws SQLException {
        lock.lock();
        try {
            String sql = "SELECT * FROM meta_cache WHERE nombre_logico = ?";
            try (PreparedStatement stmt = conexion.prepareStatement(sql)) {
                stmt.setString(1, nombreLogico);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return Optional.of(deserializarTabla(rs));
                }
                return Optional.empty();
            }
        } finally {
            lock.unlock();
        }
    }

    public void insertar(TablaConfig tabla) throws SQLException {
        lock.lock();
        // Hashear configuración y guardar como Etag
        //String json = jackson.writeValueAsString(tabla);
        //tabla.setEtag(Integer.toHexString(json.hashCode()));
        try {
            String sql = "INSERT INTO meta_cache (nombre_logico, datos) VALUES (?, ?)";
            try (PreparedStatement stmt = conexion.prepareStatement(sql)) {
                stmt.setString(1, tabla.getNombreLogico());
                stmt.setString(2, serializarTabla(tabla));
                stmt.executeUpdate();
            }
        } finally {
            lock.unlock();
        }
    }

    public void borrar(String nombreLogico) throws SQLException {
        lock.lock();
        try {
            String sql = "DELETE FROM meta_cache WHERE nombre_logico = ?";
            try (PreparedStatement stmt = conexion.prepareStatement(sql)) {
                stmt.setString(1, nombreLogico);
                stmt.executeUpdate();
            }
        } finally {
            lock.unlock();
        }
    }

    private String serializarTabla(TablaConfig tabla) throws SQLException {
        try {
            return jackson.writeValueAsString(tabla);
        } catch (JsonProcessingException e) {
            throw new SQLException("Error al serializar TablaConfig", e);
        }
    }

    private TablaConfig deserializarTabla(ResultSet rs) throws SQLException {
        try {
            String json = rs.getString("datos");
            return jackson.readValue(json, TablaConfig.class);
        } catch (JsonProcessingException e) {
            throw new SQLException("Error al deserializar TablaConfig", e);
        }
    }
}