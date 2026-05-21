/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.controller;

import apigenerica.config.ConexionMysql;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.excepciones.RecursoNoEncontradoException;
import apigenerica.excepciones.ValidacionException;
import apigenerica.model.ApiRespuesta;
import apigenerica.model.Fichero;
import apigenerica.service.FicheroService;
import io.javalin.http.Context;
import io.javalin.http.HttpCode;
import io.javalin.http.UploadedFile;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 *
 * @author Grupo1
 */
public class FicheroController {

    private final FicheroService ficheroService;
    
    public FicheroController(FicheroService ficheroService) {
        this.ficheroService = ficheroService;
    }

    /**
     * Devuelve los metadatos de un fichero: nombre, tamaño, mimetype y fecha
     * de subida
     *
     * @param ctx Contexto de la petición HTTP
     */
    public void obtenerInfo(Context ctx) {
        String uuid = ctx.pathParam("uuid");
        Fichero meta = ficheroService.obtenerMetadatos(uuid);

        if (meta != null) {
            Map<String, Object> info = new HashMap<>();
            info.put("nombre", meta.getNombreFichero());
            info.put("tamano", meta.getTamano());
            info.put("tipo", meta.getMimeType());
            info.put("fecha", meta.getFechaSubida() != null
            ? meta.getFechaSubida().toString() : null);

            ctx.status(HttpCode.OK).json(ApiRespuesta.ok(info));
        } else {
            throw new RecursoNoEncontradoException("Error al recuperar el fichero.");
        }
    }

    /**
     * Devuelve el contenido del fichero
     * 
     * @param ctx Contexto de la petición HTTP
     */
    public void descargar(Context ctx) {
        String uuid = ctx.pathParam("uuid");

        try {
            Fichero meta = ficheroService.obtenerMetadatos(uuid);

            if (meta != null) {
                InputStream stream = ficheroService.obtenerStream(uuid);

                if (stream != null) {
                    ctx.contentType(meta.getMimeType());
                    ctx.header("Content-Disposition", "attachment; filename=\"" + meta.getNombreFichero() + "\"");
                    ctx.result(stream);
                } else { // Si el fichero no tiene contenido
                    throw new RecursoNoEncontradoException("El contenido del archivo ha desaparecido.");
                }
            } else { // Si no se encontró el fichero en db4o
                throw new RecursoNoEncontradoException("No se ha encontrado el fichero.");
            }
        } catch (IOException e) {
            System.err.println("Error al servir el fichero " + uuid + ": " + e.getMessage());
            throw new BaseDatosException("Error interno al recuperar el archivo.", e);
        }
    }
    
     /**
     * Sube un fichero, lo procesa (cifrado/db4o) y devuelve sus metadatos básicos.
     */
    public void subir(Context ctx) {
        UploadedFile file = ctx.uploadedFile("archivo");
        if (file == null) {
            throw new ValidacionException("No se recibió ningún archivo (campo: 'archivo').");
        }
        
        String tabla = ctx.pathParam("tabla");
        String uuid = UUID.randomUUID().toString();
        
        // Guardar a través del servicio
        ficheroService.guardar(uuid, tabla, file);
        
        // Recuperar metadatos para la respuesta
        apigenerica.model.Fichero meta = ficheroService.obtenerMetadatos(uuid);
        
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("uuid", uuid);
        resp.put("tipoDetectado", (meta != null && meta.getTipoDetectado() != null) ? meta.getTipoDetectado() : "");
        
        ctx.status(201).json(ApiRespuesta.ok(resp));
    }

    /**
     * Lista todos los ficheros indexados en la base de datos relacional MySQL.
     */
    public void listar(Context ctx) {
        List<Map<String, Object>> lista = new ArrayList<>();
        String sql = "SELECT * FROM erp_sistema.erp_ficheros ORDER BY fecha_subida DESC";

        try (Connection conn = ConexionMysql.getConexion();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
             
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("uuid", rs.getString("uuid"));
                row.put("nombre_original", rs.getString("nombre_original"));
                row.put("mime_type", rs.getString("mime_type"));
                row.put("tipo_detectado", rs.getString("tipo_detectado"));
                row.put("tamano_bytes", rs.getLong("tamano_bytes"));
                row.put("esta_en_disco", rs.getInt("esta_en_disco") == 1);
                row.put("tabla_origen", rs.getString("tabla_origen"));
                row.put("fecha_subida", rs.getString("fecha_subida"));
                lista.add(row);
            }
        } catch (Exception e) {
            throw new BaseDatosException("Error al consultar el listado de ficheros.", e);
        }
        
        ctx.json(ApiRespuesta.ok(lista));
    }

    /**
     * Elimina un fichero físicamente y sus metadatos asociados.
     */
    public void eliminar(Context ctx) {
        String uuid = ctx.pathParam("uuid");
        ficheroService.eliminar(uuid);
        ctx.json(ApiRespuesta.ok("Fichero eliminado correctamente."));
    }
}