package com.demo.orchestrator.api;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.demo.orchestrator.api.dto.ProcessRequest;
import com.demo.orchestrator.api.dto.ProcessResponse;
import com.demo.orchestrator.api.dto.UploadResponse;
import com.demo.orchestrator.domain.ProcessJob;
import com.demo.orchestrator.service.AudioStorageService;
import com.demo.orchestrator.service.AudioValidationService;
import com.demo.orchestrator.service.ProcessJobService;
import com.demo.orchestrator.api.dto.ChunkDto;

@RestController
@RequestMapping("/api/v1/audio")
public class AudioController {

    private final AudioStorageService storageService;
    private final AudioValidationService validationService;
    private final ProcessJobService processJobService;

    public AudioController(
            AudioStorageService storageService,
            AudioValidationService validationService,
            ProcessJobService processJobService) {
        this.storageService = storageService;
        this.validationService = validationService;
        this.processJobService = processJobService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResponse upload(MultipartFile file) throws IOException, InterruptedException {
        var stored = storageService.save(file);
        double duration = validationService.probeDurationSeconds(java.nio.file.Path.of(stored.path()));
        storageService.updateDuration(stored.uri(), duration);
        return new UploadResponse(stored.uri(), duration, "flac");
    }
    @PostMapping("/process")
    public ProcessResponse process(@RequestBody ProcessRequest request) throws IOException, InterruptedException {
        ProcessJob job = processJobService.createJob(request.uri(), request.gainDb());
        return new ProcessResponse(
                job.getJobId(),
                job.getSourceDurationSec(),
                job.getChunks().size(),
                job.getChunks().stream().map(ChunkDto::from).toList(),
                job.estimatedPythonProcessingSec(),
                "/ws/v1/stream/" + job.getJobId());
    }
}
