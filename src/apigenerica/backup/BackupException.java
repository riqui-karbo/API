package apigenerica.backup;

/**
 * Excepción personalizada para errores del sistema de backups.
 */
public class BackupException extends Exception {
    public BackupException(String message) {
        super(message);
    }
    public BackupException(String message, Throwable cause) {
        super(message, cause);
    }
}