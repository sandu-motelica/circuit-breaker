# Scenario runner

This command-line application sends a repeatable HTTP workload to the protected
client and saves a per-request CSV plus a JSON summary. It needs both demo
services running, but has no code dependency on either service.

Build from the repository root:

```sh
./mvnw verify
```

Start the upstream and protected client in separate terminals:

```sh
java -jar demo-unstable-service/target/demo-unstable-service-0.1.0-SNAPSHOT.jar
java -jar demo-protected-client/target/demo-protected-client-0.1.0-SNAPSHOT.jar
```

Then run a scenario from the repository root:

```sh
java -jar scenario-runner/target/scenario-runner-0.1.0-SNAPSHOT-all.jar \
  scenario-runner/scenarios/outage-recovery.csv scenario-runner/results/outage-recovery
```

On Windows, use `.\mvnw.cmd` for Maven. The runner accepts optional arguments
after the output directory: `threads`, `client-url`, and `upstream-url`. Defaults
are `1`, `http://localhost:8080`, and `http://localhost:8081`. Example:

```sh
java -jar scenario-runner/target/scenario-runner-0.1.0-SNAPSHOT-all.jar \
  scenario-runner/scenarios/healthy.csv scenario-runner/results/healthy \
  4 http://localhost:8080 http://localhost:8081
```

The CSV columns are:

```text
phase,count,intervalMs,modes,delaysMs,pauseBeforeMs
degraded,20,50,SUCCESS|FAILURE,20|1200,0
```

Each row defines one phase. `count` is the number of requests; `intervalMs` is
the planned gap between their start times; `pauseBeforeMs` delays the phase.
`modes` and `delaysMs` are repeating patterns. They can have different lengths.
Only `SUCCESS` and `FAILURE` are accepted so that the planned outcome is known.
The runner expands the entire file before making any HTTP requests.

Before each run, it resets the upstream counter and the circuit breaker. Run
only one scenario at a time against these service instances, with no other
traffic. `calls.csv` records each request's planned and actual start time,
response class, and latency. `summary.json` includes the scenario name, runtime
settings, totals and per-phase results, final circuit state, upstream request
count, and latency percentiles. The upstream request
count indicates how many client calls were actually sent to the unstable
service; the others may have been rejected by the circuit.

The request plan is repeatable. Scheduling, network timing, and circuit state
can vary when multiple threads are used. Start with one thread for behavior
checks, then use several threads for load experiments. The `startedMs` column
shows scheduling delay relative to `scheduledMs`.

For a longer run, use `scenarios/stress-cycles.csv` with 8 threads. It plans
1,166 requests across healthy traffic, slow calls, two failure periods, open
circuit load, failed half-open probes, and two recoveries. Its three 7-second
pauses are intentionally longer than the protected client's default 5-second
open period. It is designed for the demo settings in
`demo-protected-client/src/main/resources/application.yml`.

```sh
java -jar scenario-runner/target/scenario-runner-0.1.0-SNAPSHOT-all.jar \
  scenario-runner/scenarios/stress-cycles.csv scenario-runner/results/stress-cycles 8
```

Check `summary.json` by phase and `calls.csv` for scheduling drift. With 8
threads, timing and the exact number of rejected calls can change between runs;
the input plan remains identical.
