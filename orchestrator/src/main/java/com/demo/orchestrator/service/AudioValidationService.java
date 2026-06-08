package com.demo.orchestrator.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.demo.orchestrator.config.MediaProperties;
import com.demo.orchestrator.exception.InvalidDurationException;

@Service
public class AudioValidationService {

    private static final Pattern DURATION_PATTERN = Pattern.compile("Duration:\\s(\\d+):(\\d+):(\\d+\\.\\d+)");

    private final MediaProperties properties;

    public AudioValidationService(MediaProperties properties) {
        this.properties = properties;
    }

    public double probeDurationSeconds(Path audioPath) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                        "ffprobe",
                        "-v",
                        "error",
                        "-show_entries",
                        "format=duration",
                        "-of",
                        "default=noprint_wrappers=1:nokey=1",
                        audioPath.toString())
                .redirectErrorStream(true)
                .start();
        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            output = reader.readLine();
        }
        int code = process.waitFor();
        if (code != 0 || output == null || output.isBlank()) {
            parseLegacyDuration(audioPath);
            throw new InvalidDurationException("Unable to probe audio duration");
        }
        double duration = Double.parseDouble(output.trim());
        validateDuration(duration);
        return duration;
    }

    private void parseLegacyDuration(Path audioPath) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("ffprobe", "-i", audioPath.toString())
                .redirectErrorStream(true)
                .start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = DURATION_PATTERN.matcher(line);
                if (matcher.find()) {
                    double duration = Integer.parseInt(matcher.group(1)) * 3600
                            + Integer.parseInt(matcher.group(2)) * 60
                            + Double.parseDouble(matcher.group(3));
                    validateDuration(duration);
                    return;
                }
            }
        }
        process.waitFor();
    }

    public void validateDuration(double durationSeconds) {
        if (durationSeconds < properties.minDurationSec() || durationSeconds > properties.maxDurationSec()) {
            throw new InvalidDurationException(
                    "Duration must be between " + properties.minDurationSec() + " and " + properties.maxDurationSec());
        }
    }
}
