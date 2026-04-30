package com.p2p.nodeapplication.service;

import com.p2p.nodeapplication.config.NodeConfig;
import com.p2p.nodeapplication.model.FileInfo;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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
    private final RestTemplate restTemplate;
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
// ─── TODO 3 : Réplication vers les pairs ────────────────────────────────────

    public void replicateFile(String filename, byte[] data) {
        if (nodeConfig.getPeers() == null || nodeConfig.getPeers().isEmpty()) {
            log.warn("[{}] Aucun pair configuré, réplication impossible.", nodeConfig.getId());
            return;
        }

        for (String peerUrl : nodeConfig.getPeers()) {
            String url = peerUrl + "/files/" + filename + "?replicate=false";
            try {
                restTemplate.postForEntity(
                        url,
                        data,
                        String.class,
                        // pas de Content-Type header par défaut avec byte[] → on configure
                        buildRequestEntity(data)
                );
                log.info("[{}] ✓ Répliqué '{}' vers {}", nodeConfig.getId(), filename, peerUrl);
            } catch (Exception e) {
                // Tolérance aux pannes : on continue même si un pair est hors ligne
                log.warn("[{}] ✗ Réplication échouée vers {} : {}", nodeConfig.getId(), peerUrl, e.getMessage());
            }
        }
    }

// ─── TODO 4 : Recherche chez les pairs ──────────────────────────────────────

    public byte[] searchInPeers(String filename) {
        if (nodeConfig.getPeers() == null || nodeConfig.getPeers().isEmpty()) {
            return null;
        }

        for (String peerUrl : nodeConfig.getPeers()) {
            String url = peerUrl + "/files/" + filename;
            try {
                ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    log.info("[{}] ✓ Fichier '{}' trouvé chez {}", nodeConfig.getId(), filename, peerUrl);
                    return response.getBody();
                }
            } catch (Exception e) {
                log.warn("[{}] ✗ Pair {} injoignable : {}", nodeConfig.getId(), peerUrl, e.getMessage());
            }
        }

        log.error("[{}] Fichier '{}' introuvable dans tout le réseau.", nodeConfig.getId(), filename);
        return null;
    }

// ─── Vérifier si le nœud est en vie (health check) ──────────────────────────

    public boolean isPeerAlive(String peerUrl) {
        try {
            restTemplate.getForEntity(peerUrl + "/actuator/health", String.class);
            return true;
        } catch (Exception e) {
            return false;
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
    private org.springframework.http.HttpEntity<byte[]> buildRequestEntity(byte[] data) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
        return new org.springframework.http.HttpEntity<>(data, headers);
    }
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
