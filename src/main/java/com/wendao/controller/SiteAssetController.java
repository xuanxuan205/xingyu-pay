package com.wendao.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Serves the PWA manifest with its registered media type.
 *
 * The generic /{page} view mapping would otherwise swallow this root-level
 * file, and the default resource handler has no MIME mapping for .webmanifest.
 */
@Controller
public class SiteAssetController {

    private static final MediaType MANIFEST = MediaType.parseMediaType("application/manifest+json");

    private final String frontendDir;

    public SiteAssetController(@Value("${FRONTEND_DIR:./frontend}") String frontendDir) {
        this.frontendDir = frontendDir;
    }

    @GetMapping(value = "/site.webmanifest", produces = "application/manifest+json")
    public ResponseEntity<byte[]> manifest() throws IOException {
        Path path = Paths.get(frontendDir, "static", "site.webmanifest");
        if (!Files.isReadable(path)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MANIFEST)
                .body(Files.readAllBytes(path));
    }
}
