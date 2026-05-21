/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.controller;

import apigenerica.excepciones.BaseDatosException;
import apigenerica.excepciones.RecursoNoEncontradoException;
import apigenerica.model.ApiRespuesta;
import apigenerica.model.Fichero;
import apigenerica.service.FicheroService;
import io.javalin.http.Context;
import io.javalin.http.HttpCode;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

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
}