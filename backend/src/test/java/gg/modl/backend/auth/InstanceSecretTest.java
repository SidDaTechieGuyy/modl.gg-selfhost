package gg.modl.backend.auth;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstanceSecretTest {
    @TempDir Path directory;
    @Test void generatesOnceAndPersistsAcrossRestart() throws Exception {
        String first = new InstanceSecret(directory.toString(), "").getValue();
        assertTrue(first.length() >= 32);
        assertEquals(first, new InstanceSecret(directory.toString(), "").getValue());
        assertEquals(first, Files.readString(directory.resolve(".instance-secret")));
        assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(directory.resolve(".instance-secret"))));
    }
    @Test void preservesAnExistingConfiguredKeyAndThenAllowsRemovingTheOverride() {
        String previous = "existing-instance-key-kept-for-valid-old-links";
        assertEquals(previous, new InstanceSecret(directory.toString(), previous).getValue());
        assertEquals(previous, new InstanceSecret(directory.toString(), "").getValue());
        assertThrows(IllegalStateException.class, () -> new InstanceSecret(directory.toString(), "different-key-must-not-silently-replace-original").getValue());
    }
    @Test void rejectsShortOverridesAndCorruptOrSymlinkedKeys(@TempDir Path outside) throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new InstanceSecret(directory.toString(), "short"));
        Path file = directory.resolve(".instance-secret");
        Files.writeString(file, "broken");
        assertThrows(IllegalStateException.class, () -> new InstanceSecret(directory.toString(), ""));
        Files.delete(file);
        Path other = Files.writeString(outside.resolve("secret"), "external-key-that-must-never-be-followed");
        Files.createSymbolicLink(file, other);
        assertThrows(IllegalStateException.class, () -> new InstanceSecret(directory.toString(), ""));
    }
    @Test void concurrentInitializersUseTheSameKey() throws Exception {
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var futures = pool.invokeAll(java.util.Collections.nCopies(4,
                (java.util.concurrent.Callable<String>) () -> new InstanceSecret(directory.toString(), "").getValue()));
            String expected = futures.getFirst().get();
            for (var future : futures) assertEquals(expected, future.get());
        }
    }
}
