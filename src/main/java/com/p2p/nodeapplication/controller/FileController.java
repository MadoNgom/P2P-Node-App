package com.p2p.nodeapplication.controller;

import com.p2p.nodeapplication.model.FileInfo;
import com.p2p.nodeapplication.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {
    private final FileService fileService;

    // ─── Upload avec réplication automatique ────────────────────────────────
    @PostMapping(value = "/{filename}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<String> upload(
            @PathVariable String filename,
            @RequestBody byte[] data,
            @RequestParam(defaultValue = "true") boolean replicate) {

        fileService.saveFile(filename, data);

        if (replicate) {
            // Réplication asynchrone vers les pairs
            fileService.replicateFile(filename, data);
        }

        return ResponseEntity.ok("Fichier '" + filename + "' sauvegardé");
    }

    // ─── Téléchargement avec recherche distribuée ────────────────────────────
    @GetMapping("/{filename}")
    public ResponseEntity<byte[]> download(@PathVariable String filename) {
        // 1. Cherche localement
        byte[] data = fileService.getFile(filename);

        // 2. Si absent, cherche chez les pairs
        if (data == null) {
            log.info("Fichier '{}' absent localement, recherche chez les pairs...", filename);
            data = fileService.searchInPeers(filename);
        }

        if (data == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(("Fichier '" + filename + "' introuvable dans le réseau").getBytes());
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }

    // ─── Lister les fichiers locaux ──────────────────────────────────────────
    @GetMapping
    public ResponseEntity<List<FileInfo>> list() {
        return ResponseEntity.ok(fileService.listFiles());
    }

    // ─── Vérifier si un fichier existe localement (utilisé par les pairs) ───
    @GetMapping("/{filename}/exists")
    public ResponseEntity<Boolean> exists(@PathVariable String filename) {
        return ResponseEntity.ok(fileService.existsLocally(filename));
    }
}
