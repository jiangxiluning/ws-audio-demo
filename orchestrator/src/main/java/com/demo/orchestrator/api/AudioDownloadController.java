package com.demo.orchestrator.api;

import java.io.IOException;
import java.nio.file.Files;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.demo.orchestrator.domain.ProcessJob;
import com.demo.orchestrator.domain.ProcessJob.State;
import com.demo.orchestrator.service.ProcessJobService;

@RestController
@RequestMapping("/api/v1/audio")
public class AudioDownloadController {

    private final ProcessJobService processJobService;

    public AudioDownloadController(ProcessJobService processJobService) {
        this.processJobService = processJobService;
    }

    @GetMapping("/download/{jobId}")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable String jobId) throws IOException {
        ProcessJob job = processJobService.requireJob(jobId);
        if (job.getState() != State.COMPLETED || job.getMergedPath() == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        byte[] data = Files.readAllBytes(job.getMergedPath());
        StreamingResponseBody body = outputStream -> outputStream.write(data);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + jobId + "_processed.ogg\"")
                .contentType(MediaType.parseMediaType("audio/ogg"))
                .contentLength(data.length)
                .body(body);
    }
}
