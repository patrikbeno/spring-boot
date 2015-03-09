package org.springframework.boot.launcher.vault;

import java.security.BasicPermission;

import static java.lang.System.getSecurityManager;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public abstract class VaultPermission extends BasicPermission {

    static public final VaultPermission READ_PERMISSION = new VaultPermission("read") {};
    static public final VaultPermission WRITE_PERMISSION = new VaultPermission("write") {};

    private VaultPermission(String name) {
        super(String.format("%s.%s", Vault.class.getName(), name));
    }

    public void check() {
        if (getSecurityManager() != null) {
            getSecurityManager().checkPermission(this);
        }
    }
}
