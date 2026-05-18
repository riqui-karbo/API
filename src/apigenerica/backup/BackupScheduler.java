package apigenerica.backup;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BackupScheduler {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final BackupOrchestrator orchestrator = new BackupOrchestrator();

    public void iniciar(int hora, int minuto) {
        long delay = calcularDelay(hora, minuto);
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                try { orchestrator.ejecutarBackupCompleto(); }
                catch (Exception e) { System.err.println("[Scheduler] ❌ Error backup automático: " + e.getMessage()); }
            }
        }, delay, TimeUnit.DAYS.toMillis(1), TimeUnit.MILLISECONDS);
        System.out.println("[Scheduler] ⏰ Backups programados diariamente a las " + hora + ":" + String.format("%02d", minuto));
    }

    public void detener() { scheduler.shutdown(); }

    private long calcularDelay(int h, int m) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime next = now.withHour(h).withMinute(m).withSecond(0).withNano(0);
        if (now.isAfter(next)) next = next.plusDays(1);
        return java.time.Duration.between(now, next).toMillis();
    }
}