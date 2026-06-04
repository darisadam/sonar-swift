package io.sonarswift.cli.pipeline.run;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** End-of-run state — serialised to {@code .sonar/ci/&lt;runId&gt;/state.json}. */
public class RunResult {

    public enum Status { PASS, FAIL, SKIPPED, RUNNING }

    public String runId = "";
    public String pipelineName = "";
    public String pipelineFile = "";
    public Instant startedAt;
    public Instant finishedAt;
    public long durationMs;
    public Status status = Status.RUNNING;
    public List<StageResult> stages = new ArrayList<>();

    public static class StageResult {
        public String id;
        public String name;
        public Status status = Status.RUNNING;
        public List<StepResult> steps = new ArrayList<>();
        public Instant startedAt;
        public Instant finishedAt;
        public long durationMs;
        public String skippedReason;
        /** Mirrors {@code Stage.continueOnError} so the dashboard + summary can see it. */
        public boolean continueOnError;
    }

    public static class StepResult {
        public String id;
        public String name;
        public String uses;
        public Status status = Status.RUNNING;
        public int exitCode = -1;
        public Instant startedAt;
        public Instant finishedAt;
        public long durationMs;
        public String command;
        public String logPath;
        public String skippedReason;
        public Map<String, String> env = new LinkedHashMap<>();
        /** Tail of step stderr+stdout for the dashboard preview (first 4 KB). */
        public String outputTail = "";
    }
}
