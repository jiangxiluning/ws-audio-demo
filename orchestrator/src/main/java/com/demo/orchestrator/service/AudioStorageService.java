package com.demo.orchestrator.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.demo.orchestrator.config.MediaProperties;
import com.demo.orchestrator.domain.StoredAudio;
import com.demo.orchestrator.exception.ResourceNotFoundException;

@Service
public class AudioStorageService {

    private final Path uploadDir;
    private final Map<String, StoredAudio> byUri = new ConcurrentHashMap<>();

    public AudioStorageService(MediaProperties properties) throws IOException {
        this.uploadDir = Path.of(properties.storageDir(), "uploads");
        Files.createDirectories(uploadDir);
    }

    public StoredAudio save(MultipartFile file) throws IOException {
        String id = UUID.randomUUID().toString();
        Path target = uploadDir.resolve(id + ".flac");
        Files.copy(file.getInputStream(), target);
        String uri = "audio://" + id;
        StoredAudio stored = new StoredAudio(uri, 0, target.toString());
        byUri.put(uri, stored);
        return stored;
    }

    public void updateDuration(String uri, double durationSeconds) {
        StoredAudio stored = resolve(uri);
        byUri.put(uri, new StoredAudio(stored.uri(), durationSeconds, stored.path()));
    }

    public StoredAudio resolve(String uri) {
        StoredAudio stored = byUri.get(uri);
        if (stored == null) {
            throw new ResourceNotFoundException("Unknown audio uri: " + uri);
        }
        return stored;
    }

    public Path resolvePath(String uri) {
        return Path.of(resolve(uri).path());
    }

    public Path jobDir(String jobId) throws IOException {
        Path dir = Path.of(uploadDir.getParent().toString(), "jobs", jobId);
        Files.createDirectories(dir);
        return dir;
    }
}
