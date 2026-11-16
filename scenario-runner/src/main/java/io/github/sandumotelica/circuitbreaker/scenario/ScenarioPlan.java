package io.github.sandumotelica.circuitbreaker.scenario;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

record ScenarioPlan(String name, List<PlannedCall> calls) {
    private static final String HEADER = "phase,count,intervalMs,modes,delaysMs,pauseBeforeMs";

    static ScenarioPlan load(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        if (lines.isEmpty() || !HEADER.equals(lines.getFirst().strip())) {
            throw new IllegalArgumentException("Expected CSV header: " + HEADER);
        }

        List<PlannedCall> calls = new ArrayList<>();
        long nextTimeMs = 0;
        for (int lineNumber = 2; lineNumber <= lines.size(); lineNumber++) {
            String line = lines.get(lineNumber - 1).strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] columns = line.split(",", -1);
            if (columns.length != 6) {
                throw new IllegalArgumentException("Line " + lineNumber + ": expected 6 columns");
            }
            try {
                String phase = columns[0].strip();
                if (!phase.matches("[A-Za-z0-9_-]+")) {
                    throw new IllegalArgumentException("phase must use letters, digits, _ or -");
                }
                int count = Integer.parseInt(columns[1].strip());
                long intervalMs = Long.parseLong(columns[2].strip());
                String[] modes = columns[3].strip().split("\\|", -1);
                String[] delays = columns[4].strip().split("\\|", -1);
                long pauseMs = Long.parseLong(columns[5].strip());
                if (count <= 0 || intervalMs < 0 || pauseMs < 0) {
                    throw new IllegalArgumentException("count must be positive; times must be non-negative");
                }
                for (String mode : modes) {
                    if (!mode.equals("SUCCESS") && !mode.equals("FAILURE")) {
                        throw new IllegalArgumentException("mode must be SUCCESS or FAILURE");
                    }
                }
                long[] delayValues = new long[delays.length];
                for (int i = 0; i < delays.length; i++) {
                    delayValues[i] = Long.parseLong(delays[i]);
                    if (delayValues[i] < 0) {
                        throw new IllegalArgumentException("delays must be non-negative");
                    }
                }
                nextTimeMs = Math.addExact(nextTimeMs, pauseMs);
                for (int i = 0; i < count; i++) {
                    calls.add(new PlannedCall(calls.size() + 1, phase, nextTimeMs,
                            modes[i % modes.length], delayValues[i % delayValues.length]));
                    nextTimeMs = Math.addExact(nextTimeMs, intervalMs);
                }
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Line " + lineNumber + ": " + exception.getMessage(), exception);
            }
        }
        if (calls.isEmpty()) {
            throw new IllegalArgumentException("Scenario contains no calls");
        }
        return new ScenarioPlan(path.getFileName().toString(), List.copyOf(calls));
    }

    record PlannedCall(int sequence, String phase, long scheduledMs, String mode, long delayMs) {
    }
}
