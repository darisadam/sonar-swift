package io.sonarswift.cli.pipeline.run;

import io.sonarswift.cli.pipeline.model.Pipeline;
import io.sonarswift.cli.pipeline.model.Stage;
import io.sonarswift.cli.pipeline.model.Step;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Streams pipeline progress to the terminal.
 *
 * <p>Two presentation modes:</p>
 * <ul>
 *   <li>Plain TTY — colourised stage/step headers + raw step output</li>
 *   <li>GitHub Actions — wraps step output in {@code ::group::} markers so the
 *       Actions UI gets collapsible groups</li>
 * </ul>
 *
 * <p>Decision: we don't suppress step stdout in TTY mode. Engineers familiar with
 * GitHub Actions / GitLab CI expect to see what the tool printed — wrapping it
 * up loses the context they need to debug.</p>
 */
public class Reporter {

    private final PrintStream out;
    private final boolean useColor;
    private final boolean githubActions;

    public Reporter(PrintStream out, boolean useColor, boolean githubActions) {
        this.out = out;
        this.useColor = useColor;
        this.githubActions = githubActions;
    }

    /** Default reporter: stderr-style output with colour when on a TTY. */
    public static Reporter defaultReporter() {
        boolean isGha = "true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"));
        boolean tty = !isGha && System.console() != null;
        return new Reporter(System.out, tty, isGha);
    }

    public void pipelineStarted(Pipeline pipeline, String runId) {
        printRule("Pipeline: " + pipeline.name + " (run " + runId + ")");
    }

    public void pipelineFinished(Pipeline pipeline, RunResult result) {
        printRule("Pipeline " + pipeline.name + " " + result.status + " in "
                + formatDuration(result.durationMs));
        // Brief stage summary
        for (RunResult.StageResult sr : result.stages) {
            String color = color(sr.status);
            out.printf("  %s%-6s%s %-25s %s%n",
                    color, sr.status.name(), reset(), sr.id,
                    sr.skippedReason != null ? "(" + sr.skippedReason + ")" : "");
        }
    }

    public void stageStarted(Stage stage) {
        println();
        printRule("▶ " + stage.displayName());
    }

    public void stageSkipped(Stage stage, String reason) {
        println(faint("⏭  Stage " + stage.displayName() + " skipped — " + reason));
    }

    public void stageFinished(Stage stage, RunResult.StageResult sr) {
        String dot = sr.status == RunResult.Status.PASS ? "✓" : "✗";
        String c = color(sr.status);
        println(c + dot + reset() + " " + stage.displayName()
                + " (" + formatDuration(sr.durationMs) + ")");
    }

    public void stepStarted(Stage stage, Step step, String command, Path logFile) {
        String header = "    » " + step.displayName();
        if (step.uses != null && !step.uses.isBlank()) {
            header += "  (uses: " + step.uses + ")";
        }
        if (githubActions) {
            out.println("::group::" + header);
        } else {
            println(header);
            println(faint("       $ " + command));
        }
    }

    public void stepOutput(Stage stage, Step step, String chunk) {
        // Stream raw; if needed, prefix per line. For brevity we keep it raw.
        out.print(chunk);
    }

    public void stepSkipped(Stage stage, Step step, String reason) {
        println(faint("    ⏭  " + step.displayName() + " skipped — " + reason));
    }

    public void stepFinished(Stage stage, Step step, RunResult.StepResult sr) {
        if (githubActions) {
            out.println("::endgroup::");
            if (sr.status == RunResult.Status.FAIL) {
                out.println("::error::Step " + step.displayName() + " failed (exit "
                        + sr.exitCode + ")");
            }
        }
        String c = color(sr.status);
        println(c + "    " + symbol(sr.status) + " " + step.displayName() + reset()
                + faint(" — " + formatDuration(sr.durationMs)
                + (sr.exitCode >= 0 ? ", exit " + sr.exitCode : "")));
    }

    private void println() { out.println(); }
    private void println(String s) { out.println(s); }

    private void printRule(String title) {
        if (githubActions) {
            out.println("::group::" + title);
            out.println("::endgroup::");
            out.println(title);
            return;
        }
        String hr = "─".repeat(Math.max(0, 72 - title.length()));
        println(bold("─── " + title + " ") + hr);
    }

    // ---- ANSI helpers ----

    private String color(RunResult.Status s) {
        if (!useColor) return "";
        return switch (s) {
            case PASS -> "\033[32m";
            case FAIL -> "\033[31m";
            case SKIPPED -> "\033[33m";
            case RUNNING -> "\033[36m";
        };
    }

    private String symbol(RunResult.Status s) {
        return switch (s) {
            case PASS -> "✓";
            case FAIL -> "✗";
            case SKIPPED -> "⏭";
            case RUNNING -> "·";
        };
    }

    private String reset() { return useColor ? "\033[0m" : ""; }
    private String bold(String s) { return useColor ? "\033[1m" + s + "\033[0m" : s; }
    private String faint(String s) { return useColor ? "\033[2m" + s + "\033[0m" : s; }

    private String formatDuration(long ms) {
        Duration d = Duration.ofMillis(ms);
        if (d.toMinutes() > 0) {
            return d.toMinutes() + "m" + (d.toSecondsPart()) + "s";
        }
        if (d.toSeconds() > 0) {
            return d.toSeconds() + "s";
        }
        return ms + "ms";
    }
}
