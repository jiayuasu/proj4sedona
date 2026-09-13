package org.datasyslab.proj4sedona;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarFile;
import org.datasyslab.proj4sedona.constants.EsriAliases;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The artifact must carry the licence and the NOTICE, as the Apache License 2.0 requires.
 * The files are looked up next to this library's own classes (other jars on the test
 * classpath carry NOTICE files of their own), whether those classes sit in a directory or in
 * a jar.
 */
class PackagingTest {

    private static String packaged(String name) throws IOException {
        URL location = EsriAliases.class.getProtectionDomain().getCodeSource().getLocation();
        Path root = Paths.get(java.net.URI.create(location.toString()));
        if (Files.isDirectory(root)) {
            Path file = root.resolve("META-INF").resolve(name);
            assertTrue(Files.isRegularFile(file), file + " is not packaged");
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        }
        try (JarFile jar = new JarFile(root.toFile())) {
            var entry = jar.getJarEntry("META-INF/" + name);
            assertNotNull(entry, "META-INF/" + name + " is not in " + root);
            try (InputStream in = jar.getInputStream(entry)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    @Test
    @DisplayName("META-INF/LICENSE is the Apache License 2.0")
    void licenseIsPackaged() throws IOException {
        assertTrue(packaged("LICENSE").contains("Apache License"));
    }

    @Test
    @DisplayName("META-INF/NOTICE credits the Esri data")
    void noticeIsPackaged() throws IOException {
        String notice = packaged("NOTICE");
        assertTrue(notice.contains("projection-engine-db-doc"), notice);
    }
}
