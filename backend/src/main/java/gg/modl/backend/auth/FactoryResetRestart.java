package gg.modl.backend.auth;

import org.springframework.stereotype.Component;

@Component
public class FactoryResetRestart {
    public void schedule() {
        Thread.ofPlatform().name("factory-reset-restart").start(() -> {
            try { Thread.sleep(1500); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            // Compose restart: unless-stopped restarts the process and executes the durable reset request.
            System.exit(0);
        });
    }
}
