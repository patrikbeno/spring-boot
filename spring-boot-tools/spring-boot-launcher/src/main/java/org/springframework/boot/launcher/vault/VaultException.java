package org.springframework.boot.launcher.vault;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class VaultException extends RuntimeException {

    public VaultException(String message) {
        super(message);
    }

    public VaultException(Throwable cause, String message) {
        super(message, cause);
    }
}
