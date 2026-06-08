package com.demo.orchestrator.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class FfmpegCodecService {

    public byte[] decodeChunkToPcm24k(Path input, double offsetSec, double durationSec)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
                "ffmpeg",
                "-hide_banner",
                "-loglevel",
                "error",
                "-ss",
                String.valueOf(offsetSec),
                "-t",
                String.valueOf(durationSec),
                "-i",
                input.toString(),
                "-af",
                "pan=mono|c0=0.5*c0+0.5*c1,aformat=sample_rates=24000:sample_fmts=s16",
                "-f",
                "s16le",
                "pipe:1");
        return runAndCaptureStdout(builder);
    }

    public byte[] encodePcm48kToOggFlac(byte[] pcm48k) throws IOException, InterruptedException {
        Path tempPcm = Files.createTempFile("pcm48k-", ".raw");
        Path tempOgg = Files.createTempFile("chunk-", ".ogg");
        try {
            Files.write(tempPcm, pcm48k);
            Files.delete(tempOgg);
            ProcessBuilder builder = new ProcessBuilder(
                    "ffmpeg",
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-y",
                    "-f",
                    "s16le",
                    "-ar",
                    "48000",
                    "-ac",
                    "1",
                    "-i",
                    tempPcm.toString(),
                    "-c:a",
                    "flac",
                    "-sample_fmt",
                    "s16",
                    "-ar",
                    "48000",
                    "-ac",
                    "1",
                    tempOgg.toString());
            runProcess(builder);
            return Files.readAllBytes(tempOgg);
        } finally {
            Files.deleteIfExists(tempPcm);
            Files.deleteIfExists(tempOgg);
        }
    }

    public void concatOggFiles(List<Path> chunkFiles, Path output) throws IOException, InterruptedException {
        Path listFile = Files.createTempFile("concat-", ".txt");
        try {
            StringBuilder sb = new StringBuilder();
            for (Path chunk : chunkFiles) {
                sb.append("file '").append(chunk.toAbsolutePath()).append("'\n");
            }
            Files.writeString(listFile, sb.toString(), StandardCharsets.UTF_8);
            ProcessBuilder builder = new ProcessBuilder(
                    "ffmpeg",
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-y",
                    "-f",
                    "concat",
                    "-safe",
                    "0",
                    "-i",
                    listFile.toString(),
                    "-c",
                    "copy",
                    output.toString());
            runProcess(builder);
        } finally {
            Files.deleteIfExists(listFile);
        }
    }

    private byte[] runAndCaptureStdout(ProcessBuilder builder) throws IOException, InterruptedException {
        builder.redirectErrorStream(true);
        Process process = builder.start();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream in = process.getInputStream()) {
            in.transferTo(baos);
        }
        int code = process.waitFor();
        if (code != 0) {
            throw new IOException("ffmpeg failed with exit code " + code);
        }
        return baos.toByteArray();
    }

    private void runProcess(ProcessBuilder builder) throws IOException, InterruptedException {
        builder.redirectErrorStream(true);
        Process process = builder.start();
        try (InputStream in = process.getInputStream()) {
            in.transferTo(java.io.OutputStream.nullOutputStream());
        }
        int code = process.waitFor();
        if (code != 0) {
            throw new IOException("ffmpeg failed with exit code " + code);
        }
    }
}
