package com.demo.orchestrator.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.demo.orchestrator.domain.ProcessJob;

@Service
public class JobStore {

    private final Map<String, ProcessJob> jobs = new ConcurrentHashMap<>();
    private final Map<String, Boolean> wsConnected = new ConcurrentHashMap<>();

    public void put(ProcessJob job) {
        jobs.put(job.getJobId(), job);
    }

    public Optional<ProcessJob> get(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    public boolean markWsConnected(String jobId) {
        ProcessJob job = jobs.get(jobId);
        if (job == null) {
            return false;
        }
        if (job.getState() == ProcessJob.State.EXPIRED) {
            return false;
        }
        return wsConnected.putIfAbsent(jobId, Boolean.TRUE) == null;
    }

    public void removeWs(String jobId) {
        wsConnected.remove(jobId);
    }

    public void expireIfAwaiting(String jobId) {
        ProcessJob job = jobs.get(jobId);
        if (job != null && job.getState() == ProcessJob.State.AWAITING_WS) {
            job.setState(ProcessJob.State.EXPIRED);
        }
    }
}
