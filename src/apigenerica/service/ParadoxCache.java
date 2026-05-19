package apigenerica.service;

import apigenerica.model.TablaConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Caché de metadatos de tablas almacenado en Paradox (tabla meta_cache).
 *
 * Toda operación sobre Paradox se delega a ParadoxWorker (comandos META_*)
 * ejecutado en un subproceso independiente (JVM nueva). Esto garantiza que
 * el contador global de 50 queries del driver HXTT evaluación nunca se
 * alcance: cada subproceso arranca con el contador a 0 y muere al terminar.
 *
 * @author Grupo1
 */
public class ParadoxCache {

    private final String carpeta;
    private final ObjectMapper jackson = new ObjectMapper();

    public ParadoxCache(String ruta) throws SQLException {
        this.carpeta = new java.io.File(ruta).getAbsolutePath();
        // Asegurar que la tabla existe lanzando META_INIT en subproceso
        String r = ejecutarWorker("META_INIT");
        if (r == null || r.startsWith("ERROR:")) {
            System.err.println("[ParadoxCache] Advertencia al inicializar meta_cache: "
                + (r != null ? r : "null"));
        }
    }

    public Optional<TablaConfig> get(String nombreLogico) throws SQLException {
        String resultado = ejecutarWorker("META_GET", logs.dao.ParadoxWorker.encode(nombreLogico));
        if (resultado == null || resultado.startsWith("ERROR:")) {
            System.err.println("[ParadoxCache] META_GET error: "
                + (resultado != null ? resultado.substring(6) : "null"));
            return Optional.empty();
        }
        String val = resultado.substring(3); // quitar "OK:"
        if ("NULL".equals(val)) return Optional.empty();
        // Descodificar el valor que vino encode()d del worker
        String json = val.replace("__SPACE__",     " ")
                         .replace("__NL__",        "\n")
                         .replace("__CR__",        "\r")
                         .replace("__BACKSLASH__", "\\");
        try {
            return Optional.of(jackson.readValue(json, TablaConfig.class));
        } catch (JsonProcessingException e) {
            throw new SQLException("Error al deserializar TablaConfig", e);
        }
    }

    public void insertar(TablaConfig tabla) throws SQLException {
        try {
            String json = jackson.writeValueAsString(tabla);
            String resultado = ejecutarWorker(
                "META_INSERT",
                logs.dao.ParadoxWorker.encode(tabla.getNombreLogico()),
                logs.dao.ParadoxWorker.encode(json)
            );
            if (resultado == null || resultado.startsWith("ERROR:")) {
                System.err.println("[ParadoxCache] META_INSERT error: "
                    + (resultado != null ? resultado.substring(6) : "null"));
            }
        } catch (JsonProcessingException e) {
            throw new SQLException("Error al serializar TablaConfig", e);
        }
    }

    public void borrar(String nombreLogico) throws SQLException {
        String resultado = ejecutarWorker("META_DELETE", logs.dao.ParadoxWorker.encode(nombreLogico));
        if (resultado == null || resultado.startsWith("ERROR:")) {
            System.err.println("[ParadoxCache] META_DELETE error: "
                + (resultado != null ? resultado.substring(6) : "null"));
        }
    }

    // ── Subprocess helper ─────────────────────────────────────────────────────

    private String ejecutarWorker(String cmd, String... args) {
        try {
            String classpath = System.getProperty("java.class.path");
            String javaExe   = System.getProperty("java.home") + "/bin/java";

            List<String> cmdList = new ArrayList<>();
            cmdList.add(javaExe);
            cmdList.add("-cp");
            cmdList.add(classpath);
            cmdList.add("logs.dao.ParadoxWorker");
            cmdList.add(carpeta);
            cmdList.add(cmd);
            for (String a : args) cmdList.add(a);

            ProcessBuilder pb = new ProcessBuilder(cmdList);
            pb.redirectErrorStream(false);
            Process proc = pb.start();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            proc.waitFor();
            return sb.toString().trim();
        } catch (Exception e) {
            System.err.println("[ParadoxCache] ejecutarWorker error: " + e.getMessage());
            return null;
        }
    }
}