package apigenerica.backup;

import apigenerica.model.ApiRespuesta;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BackupController implements Handler {
    private final BackupOrchestrator orchestrator = new BackupOrchestrator();

    @Override
    public void handle(Context ctx) throws Exception {
        String accion = ctx.pathParam("accion");
        switch (accion) {
            case "crear":
                crearBackup(ctx);
                break;
            case "listar":
                listarBackups(ctx);
                break;
            case "restaurar":
                restaurarBackup(ctx);
                break;
            case "verificar":
                verificarEstado(ctx);
                break;
            default:
                ctx.json(nuevaRespuestaError("Acción no válida")).status(400);
                break;
        }
    }

    /**
     * Crea una respuesta de éxito con datos adicionales
     * Compatible con cualquier estructura de ApiRespuesta
     */
    private Object nuevaRespuestaExito(String mensaje, Map<String, Object> datos) {
        // OPCIÓN A: Si ApiRespuesta tiene constructor con datos
        // return new ApiRespuesta(true, mensaje, datos);
        
        // OPCIÓN B: Si ApiRespuesta es un Map simple (más común en Javalin)
        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("success", true);
        respuesta.put("message", mensaje);
        if (datos != null) {
            respuesta.put("data", datos);
        }
        return respuesta;
    }

    /**
     * Crea una respuesta de error
     */
    private Object nuevaRespuestaError(String mensaje) {
        // OPCIÓN A: Si usas ApiRespuesta.error()
        // return ApiRespuesta.error(mensaje);
        
        // OPCIÓN B: Map genérico compatible
        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("success", false);
        respuesta.put("message", mensaje);
        return respuesta;
    }

    private void crearBackup(Context ctx) throws Exception {
        try {
            File backup = orchestrator.ejecutarBackupCompleto();
            Map<String, Object> datos = new HashMap<>();
            datos.put("ruta", backup.getAbsolutePath());
            datos.put("nombre", backup.getName());
            datos.put("tamano", backup.length());
            
            ctx.json(nuevaRespuestaExito("Backup creado exitosamente", datos));
        } catch (Exception e) {
            ctx.json(nuevaRespuestaError("Error al crear backup: " + e.getMessage())).status(500);
            e.printStackTrace(); // Log para depuración
        }
    }

    private void listarBackups(Context ctx) {
        File dir = BackupConfig.getDirectorioBackups();
        File[] backups = dir.listFiles(new java.io.FileFilter() {
            public boolean accept(File f) {
                return f.getName().startsWith(BackupConfig.PREFIJO_BACKUP) && 
                       f.getName().endsWith(BackupConfig.EXTENSION_BACKUP);
            }
        });
        
        List<Map<String, Object>> lista = new ArrayList<>();
        if (backups != null) {
            for (File f : backups) {
                Map<String, Object> info = new HashMap<>();
                info.put("nombre", f.getName());
                info.put("tamano", f.length());
                info.put("fecha", new Date(f.lastModified()));
                info.put("ruta", f.getAbsolutePath());
                lista.add(info);
            }
            // Ordenar por fecha descendente (Java 8 compatible)
            java.util.Collections.sort(lista, new java.util.Comparator<Map<String, Object>>() {
                public int compare(Map<String, Object> a, Map<String, Object> b) {
                    return ((Date) b.get("fecha")).compareTo((Date) a.get("fecha"));
                }
            });
        }
        
        Map<String, Object> datos = new HashMap<>();
        datos.put("total", lista.size());
        datos.put("backups", lista);
        
        ctx.json(nuevaRespuestaExito("Backups disponibles", datos));
    }

    private void restaurarBackup(Context ctx) throws Exception {
        String backupNombre = ctx.queryParam("backup");
        if (backupNombre == null) {
            ctx.json(nuevaRespuestaError("Falta parámetro: ?backup=nombre.zip")).status(400);
            return;
        }
        
        // Confirmación de seguridad para restauración
        String confirmacion = ctx.queryParam("confirmacion");
        if (!"CONFIRMAR".equals(confirmacion)) {
            ctx.json(nuevaRespuestaError(
                "Operación crítica: requiere ?confirmacion=CONFIRMAR para restaurar"
            )).status(400);
            return;
        }
        
        File backupFile = new File(BackupConfig.getDirectorioBackups(), backupNombre);
        if (!backupFile.exists()) {
            ctx.json(nuevaRespuestaError("Backup no encontrado: " + backupNombre)).status(404);
            return;
        }
        
        try {
            orchestrator.restaurarBackupCompleto(backupFile);
            ctx.json(nuevaRespuestaExito("Restauración completada exitosamente", null));
        } catch (Exception e) {
            ctx.json(nuevaRespuestaError("Error al restaurar: " + e.getMessage())).status(500);
            e.printStackTrace();
        }
    }

    private void verificarEstado(Context ctx) {
        Map<String, Object> estado = new HashMap<>();
        estado.put("mysqldump_disponible", new MysqlBackupManager().verificarMysqldumpDisponible());
        estado.put("directorio_backups_escritura", BackupConfig.getDirectorioBackups().canWrite());
        estado.put("directorio_backups", BackupConfig.getDirectorioBackups().getAbsolutePath());
        estado.put("mysql_host", BackupConfig.MYSQL_HOST + ":" + BackupConfig.MYSQL_PORT);
        
        ctx.json(nuevaRespuestaExito("Estado del sistema de backups", estado));
    }
}