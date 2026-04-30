package com.p2p.nodeapplication.service;

import com.p2p.nodeapplication.config.NodeConfig;
import com.p2p.nodeapplication.model.FileInfo;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {
   private final NodeConfig nodeConfig;
    // TODO 1 : Sauvegarder localement ──────────────────────────────────────────

    public void saveFile(String filename, byte[] data) {
        Path storageDir = getStorageDir();
        Path filePath = storageDir.resolve(filename);

        try {
            Files.write(filePath, data, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            log.info("[{}] Fichier sauvegardé : {} ({} octets)",
                    nodeConfig.getId(), filename, data.length);
        } catch (IOException e) {
            log.error("[{}] Erreur sauvegarde {} : {}", nodeConfig.getId(), filename, e.getMessage());
            throw new UncheckedIOException("Impossible de sauvegarder : " + filename, e);
        }
    }
    // ─── TODO 2 : Lire un fichier local

    public byte[] getFile(String filename) {
        Path filePath = getStorageDir().resolve(filename);

        if (!Files.exists(filePath)) {
            log.warn("[{}] Fichier introuvable localement : {}", nodeConfig.getId(), filename);
            return null; // sera géré dans getFileWithFallback
        }

        try {
            byte[] data = Files.readAllBytes(filePath);
            log.info("[{}] Fichier lu localement : {} ({} octets)",
                    nodeConfig.getId(), filename, data.length);
            return data;
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de lire : " + filename, e);
        }
    }

    // ─── Lister les fichiers locaux ──────────────────────────────────────────

    public List<FileInfo> listFiles() {
        try {
            return Files.list(getStorageDir())
                    .filter(Files::isRegularFile)
                    .map(path -> {
                        try {
                            return new FileInfo(
                                    path.getFileName().toString(),
                                    Files.size(path),
                                    LocalDateTime.now(),
                                    nodeConfig.getId()
                            );
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("Erreur lecture répertoire", e);
        }
    }

    public boolean existsLocally(String filename) {
        return Files.exists(getStorageDir().resolve(filename));
    }

    // ─── Utilitaire : créer le dossier si absent ─────────────────────────────

    private Path getStorageDir() {
        Path dir = Paths.get(nodeConfig.getStorage());
        try {
            Files.createDirectories(dir);
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de créer le répertoire de stockage", e);
        }
    }

}
