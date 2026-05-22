package logs.dao;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DAO para la tabla de logs.
 *
 * STORAGE PRIMARIO: archivo logs_backup.json en base_de_datos/
 *   - Lectura/escritura directa en JSON (sin driver JDBC, sin subprocesos).
 *   - 100% fiable entre reinicios: los datos sobreviven siempre.
 *   - Formato: una línea JSON por registro para append atómico.
 *
 * STORAGE SECUNDARIO (best-effort): Paradox via ParadoxWorker
 *   - Se intenta también escribir en logs.db para compatibilidad.
 *   - Si falla, se ignora silenciosamente.
 *   - La persistencia NO depende de que Paradox funcione.
 */
public class LogDAO {

    private static final AtomicInteger contadorId  = new AtomicInteger(-1);
    private static final Map<Integer, String[]> bufferMemoria = new LinkedHashMap<>();
    private static final String CARPETA_ABSOLUTA   = resolverCarpetaAbsoluta();
    private static final String JSON_FILE          = CARPETA_ABSOLUTA + File.separator + "logs_backup.json";

    private static final SimpleDateFormat SDF =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    // ---------------------------------------------------------------
    // Resolver carpeta de datos
    // ---------------------------------------------------------------
    private static String resolverCarpetaAbsoluta() {
        try {
            URL url = LogDAO.class.getProtectionDomain().getCodeSource().getLocation();
            File base    = new File(url.toURI()).getCanonicalFile();
            File raiz    = base.getParentFile().getParentFile();
            File carpeta = new File(raiz, "base_de_datos");
            carpeta.mkdirs();
            System.out.println("[LogDAO] Carpeta: " + carpeta.getAbsolutePath());
            return carpeta.getAbsolutePath();
        } catch (Exception e) {
            File carpeta = new File("base_de_datos").getAbsoluteFile();
            carpeta.mkdirs();
            return carpeta.getAbsolutePath();
        }
    }

    // ---------------------------------------------------------------
    // Inicializar: cargar JSON al arrancar
    // ---------------------------------------------------------------
    public static void inicializar() {
        cargarDesdeJSON();
        inicializarContadorSiNecesario();
        System.out.println("[LogDAO] Inicializado. " + bufferMemoria.size()
                + " registro(s) cargados. Próximo ID: " + contadorId.get());

        // Intentar también leer desde Paradox (best-effort, no crítico)
        try {
            String json = ejecutarWorker(new String[]{"SELECT_ALL"});
            if (json != null && json.startsWith("[")) {
                Map<Integer, String[]> paradoxRows = new LinkedHashMap<>();
                parsearJsonFilas(json, paradoxRows);
                synchronized (bufferMemoria) {
                    for (Map.Entry<Integer, String[]> e : paradoxRows.entrySet()) {
                        bufferMemoria.putIfAbsent(e.getKey(), e.getValue());
                    }
                }
                guardarTodoElBufferEnJSON();
                System.out.println("[LogDAO] Datos de Paradox fusionados.");
            }
        } catch (Exception e) {
            System.err.println("[LogDAO] Paradox no disponible al arrancar (no crítico): " + e.getMessage());
        }
    }

    /**
     * Reinicializa el DAO tras una restauración de backup.
     *
     * Limpia el buffer en RAM y el contador de IDs, luego recarga el contenido
     * del logs_backup.json que acaba de ser restaurado. Así el buffer queda
     * perfectamente sincronizado con el fichero restaurado, sin mezclar entradas
     * del estado anterior a la restauración.
     *
     * NO fusiona Paradox aquí a propósito: el JSON restaurado ya es la fuente
     * de verdad; Paradox se sincronizará de forma best-effort en el próximo
     * arranque normal o ciclo del monitor.
     */
    public static synchronized void reinicializar() {
        synchronized (bufferMemoria) {
            bufferMemoria.clear();
        }
        // Resetear el contador para que se recalcule desde los datos restaurados
        contadorId.set(-1);

        cargarDesdeJSON();
        inicializarContadorSiNecesario();
        System.out.println("[LogDAO] Reinicializado tras restauración. "
                + bufferMemoria.size() + " registro(s). Próximo ID: " + contadorId.get());
    }

    // ---------------------------------------------------------------
    // Insertar un registro de log
    // ---------------------------------------------------------------
    public static void registrar(String usuario, String operacion,
                                  String tablaAfectada, String descripcion) {
        inicializarContadorSiNecesario();

        int    nuevoId = contadorId.getAndIncrement();
        String ts      = SDF.format(new Date());

        String[] fila = {
            String.valueOf(nuevoId),
            usuario       != null ? usuario                   : "sistema",
            operacion     != null ? operacion.toUpperCase()   : "",
            tablaAfectada != null ? tablaAfectada             : "",
            descripcion   != null ? descripcion               : "",
            ts
        };

        // 1. Buffer RAM (visible de inmediato)
        synchronized (bufferMemoria) {
            bufferMemoria.put(nuevoId, fila);
        }

        // 2. Persistir en JSON (fiable, sin dependencias externas)
        persistirFilaEnJSON(fila);

        // 3. Intentar también en Paradox (best-effort)
        try {
            String[] cmd = {
                "INSERT",
                String.valueOf(nuevoId),
                encode(fila[1]), encode(fila[2]), encode(fila[3]), encode(fila[4])
            };
            ejecutarWorkerAsync(cmd);
        } catch (Exception e) {
            System.err.println("[LogDAO] Paradox INSERT ignorado: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Leer todos los logs (más reciente primero)
    // ---------------------------------------------------------------
    public static List<String[]> obtenerTodos() {
        Map<Integer, String[]> combinado = new LinkedHashMap<>();

        synchronized (bufferMemoria) {
            combinado.putAll(bufferMemoria);
        }

        // Complementar con Paradox si responde (best-effort)
        try {
            String json = ejecutarWorker(new String[]{"SELECT_ALL"});
            if (json != null && json.startsWith("[")) {
                Map<Integer, String[]> paradoxRows = new LinkedHashMap<>();
                parsearJsonFilas(json, paradoxRows);
                for (Map.Entry<Integer, String[]> e : paradoxRows.entrySet()) {
                    combinado.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        } catch (Exception ignored) {}

        List<Integer> ids = new ArrayList<>(combinado.keySet());
        ids.sort((a, b) -> Integer.compare(b, a));   // DESC
        List<String[]> lista = new ArrayList<>(ids.size());
        for (int id : ids) lista.add(combinado.get(id));
        return lista;
    }

    /**
     * Busca logs por usuario, operación o tabla (búsqueda simple case-insensitive).
     * Devuelve siempre en orden descendente por ID.
     */
    public static List<String[]> buscar(String filtro) {
        if (filtro == null || filtro.trim().isEmpty()) return obtenerTodos();
        String f = filtro.toLowerCase();
        List<String[]> todos = obtenerTodos();
        List<String[]> resultado = new ArrayList<>();
        for (String[] fila : todos) {
            for (String campo : fila) {
                if (campo != null && campo.toLowerCase().contains(f)) {
                    resultado.add(fila);
                    break;
                }
            }
        }
        return resultado;
    }

    // ---------------------------------------------------------------
    // JSON storage — persistencia primaria
    // ---------------------------------------------------------------

    private static void cargarDesdeJSON() {
        File f = new File(JSON_FILE);
        if (!f.exists()) {
            System.out.println("[LogDAO] No existe logs_backup.json todavía (primera vez).");
            return;
        }
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            int cargados = 0;
            synchronized (bufferMemoria) {
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    String[] fila = parseLineaJSON(line);
                    if (fila != null) {
                        try {
                            int id = Integer.parseInt(fila[0]);
                            bufferMemoria.put(id, fila);
                            cargados++;
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            System.out.println("[LogDAO] Cargados " + cargados + " registro(s) desde JSON.");
        } catch (IOException e) {
            System.err.println("[LogDAO] Error leyendo JSON: " + e.getMessage());
        }
    }

    private static void persistirFilaEnJSON(String[] fila) {
        try (FileOutputStream fos  = new FileOutputStream(JSON_FILE, true);
             PrintWriter       pw  = new PrintWriter(
                     new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {
            pw.println(filaALineaJSON(fila));
            pw.flush();
        } catch (IOException e) {
            System.err.println("[LogDAO] Error escribiendo en JSON: " + e.getMessage());
        }
    }

    private static void guardarTodoElBufferEnJSON() {
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(JSON_FILE, false),
                                       StandardCharsets.UTF_8))) {
            synchronized (bufferMemoria) {
                List<Integer> ids = new ArrayList<>(bufferMemoria.keySet());
                ids.sort(Integer::compare);
                for (int id : ids) pw.println(filaALineaJSON(bufferMemoria.get(id)));
            }
            pw.flush();
        } catch (IOException e) {
            System.err.println("[LogDAO] Error reescribiendo JSON: " + e.getMessage());
        }
    }

    private static String filaALineaJSON(String[] f) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < f.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escaparJSON(f[i] != null ? f[i] : "")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String[] parseLineaJSON(String line) {
        if (!line.startsWith("[") || !line.endsWith("]")) return null;
        String inner = line.substring(1, line.length() - 1);
        String[] campos = splitCsv(inner);
        if (campos.length < 6) return null;
        String[] fila = new String[6];
        for (int i = 0; i < 6; i++) {
            String v = campos[i].trim();
            if (v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length() - 1);
            fila[i] = desescaparJSON(v);
        }
        return fila;
    }

    private static String escaparJSON(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String desescaparJSON(String s) {
        return s.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    // ---------------------------------------------------------------
    // Contador de IDs
    // ---------------------------------------------------------------
    private static synchronized void inicializarContadorSiNecesario() {
        if (contadorId.get() != -1) return;
        int maxBuffer;
        synchronized (bufferMemoria) {
            maxBuffer = bufferMemoria.keySet().stream()
                    .mapToInt(Integer::intValue).max().orElse(0);
        }
        contadorId.set(maxBuffer + 1);
        System.out.println("[LogDAO] Contador iniciado en: " + (maxBuffer + 1));
    }

    // ---------------------------------------------------------------
    // Paradox worker (best-effort)
    // ---------------------------------------------------------------
    private static String ejecutarWorker(String[] workerArgs) {
        try {
            String classpath = System.getProperty("java.class.path");
            String javaExe   = System.getProperty("java.home") + "/bin/java";
            List<String> cmdList = new ArrayList<>();
            cmdList.add(javaExe); cmdList.add("-cp"); cmdList.add(classpath);
            cmdList.add("dao.ParadoxWorker");
            cmdList.add(CARPETA_ABSOLUTA);
            for (String a : workerArgs) cmdList.add(a);
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
            return null;
        }
    }

    private static void ejecutarWorkerAsync(String[] workerArgs) {
        new Thread(() -> ejecutarWorker(workerArgs), "paradox-worker").start();
    }

    // ---------------------------------------------------------------
    // Helpers internos de parseo JSON
    // ---------------------------------------------------------------
    private static void parsearJsonFilas(String json, Map<Integer, String[]> destino) {
        json = json.trim();
        if (json.equals("[]")) return;
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]"))   json = json.substring(0, json.length() - 1);
        int i = 0;
        while (i < json.length()) {
            int inicio = json.indexOf('[', i);
            if (inicio < 0) break;
            int fin = json.indexOf(']', inicio);
            if (fin < 0) break;
            String frag = json.substring(inicio + 1, fin);
            String[] campos = splitCsv(frag);
            if (campos.length >= 6) {
                try {
                    int id = Integer.parseInt(campos[0].replace("\"", "").trim());
                    String[] fila = new String[6];
                    for (int k = 0; k < 6; k++) {
                        String v = campos[k].trim();
                        if (v.equals("null")) { fila[k] = ""; }
                        else {
                            if (v.startsWith("\"") && v.endsWith("\""))
                                v = v.substring(1, v.length() - 1);
                            fila[k] = desescaparJSON(v);
                        }
                    }
                    destino.put(id, fila);
                } catch (NumberFormatException ignored) {}
            }
            i = fin + 1;
        }
    }

    private static String[] splitCsv(String s) {
        List<String> result = new ArrayList<>();
        boolean enComilla = false;
        int inicio = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') enComilla = !enComilla;
            else if (c == ',' && !enComilla) {
                result.add(s.substring(inicio, i));
                inicio = i + 1;
            }
        }
        result.add(s.substring(inicio));
        return result.toArray(new String[0]);
    }

    private static String encode(String s) {
        if (s == null || s.isEmpty()) return "__EMPTY__";
        return s.replace(" ", "__SPACE__");
    }
}