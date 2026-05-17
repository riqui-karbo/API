/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.service;

import apigenerica.model.OperacionPendiente;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Grupo1
 * Gestiona la cola de operaciones de Paradox.
 * Las operaciones se ponen en cola y se eliminan cuando sean insertadas en MySQL
 */
public class ColaOperaciones {

    private final Connection conexion;
    private final ReentrantLock lock = new ReentrantLock();
    private final ObjectMapper jackson = new ObjectMapper();

    public ColaOperaciones(Connection conexion) {
        this.conexion = conexion;
    }

    /**
     * Añade una operación a la cola de Paradox
     * @param op Objeto que contiene los datos de la operación
     * @throws SQLException 
     */
    public void encolar(OperacionPendiente op) throws SQLException {
        lock.lock();
        try {
            String sql = "INSERT INTO operaciones_pendientes " +
                        "(operacion, nombre_db, nombre_tabla, payload_json, timestamp) " +
                        "VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conexion.prepareStatement(sql)) {
                stmt.setString(1, op.getOperacion());
                stmt.setString(2, op.getNombreDb());
                stmt.setString(3, op.getNombreTabla());
                stmt.setString(4, jackson.writeValueAsString(op.getPayload()));
                stmt.setString(5, op.getTimestamp().toString());
                stmt.executeUpdate();
            }
        } catch (JsonProcessingException e) {
            throw new SQLException("Error al serializar payload", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Obtener todas las operaciones pendientes en orden de ID
     * @return Lista con todas las operaciones pendientes
     * @throws SQLException 
     */
    public List<OperacionPendiente> getTodas() throws SQLException {
        lock.lock();
        try {
            List<OperacionPendiente> lista = new ArrayList<>();
            String sql = "SELECT * FROM operaciones_pendientes ORDER BY id ASC";
            try (PreparedStatement stmt = conexion.prepareStatement(sql);
                    ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearOperacion(rs));
                }
            }
            return lista;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Eliminar operación de la cola
     * @param id
     * @throws SQLException 
     */
    public void confirmar(Long id) throws SQLException {
        lock.lock();
        try {
            String sql = "DELETE FROM operaciones_pendientes WHERE id = ?";
            try (PreparedStatement stmt = conexion.prepareStatement(sql)) {
                stmt.setLong(1, id);
                stmt.executeUpdate();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Transformar valores de Paradox en un objeto Java
     * @param rs
     * @return
     * @throws SQLException 
     */
    private OperacionPendiente mapearOperacion(ResultSet rs) throws SQLException {
        try {
            OperacionPendiente op = new OperacionPendiente();
            op.setId(rs.getLong("id"));
            op.setOperacion(rs.getString("operacion"));
            op.setNombreDb(rs.getString("nombre_db"));
            op.setNombreTabla(rs.getString("nombre_tabla"));
            op.setPayload(jackson.readValue(
                rs.getString("payload_json"), 
                new TypeReference<Map<String, Object>>() {}
            ));
            op.setTimestamp(LocalDateTime.parse(rs.getString("timestamp")));
            return op;
        } catch (JsonProcessingException e) {
            throw new SQLException("Error al deserializar payload", e);
        }
    }
}
