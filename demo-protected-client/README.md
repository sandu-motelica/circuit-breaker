# Protected HTTP client

A Spring Boot client that calls `demo-unstable-service` through either
`circuit-breaker-core` or Resilience4j. Each route has an independent circuit breaker.
It runs independently on port `8080`; the upstream service runs on port `8081`.

Build both applications from the repository root:

```sh
./mvnw verify
```

In two terminals, start the upstream service and then the client:

```sh
java -jar demo-unstable-service/target/demo-unstable-service-0.1.0-SNAPSHOT.jar
java -jar demo-protected-client/target/demo-protected-client-0.1.0-SNAPSHOT.jar
```

On Windows, use `.\mvnw.cmd` instead of `./mvnw`.

Try the client endpoint:

```sh
curl "http://localhost:8080/api/payments/payment-123?mode=SUCCESS"
curl "http://localhost:8080/api/payments/payment-123?mode=FAILURE"
curl "http://localhost:8080/api/payments/payment-123?mode=SUCCESS&delayMs=20"
curl "http://localhost:8080/api/circuit-breaker"
curl "http://localhost:8080/api/payments/resilience4j/payment-123?mode=SUCCESS"
curl "http://localhost:8080/api/payments/resilience4j/payment-123?mode=FAILURE"
curl "http://localhost:8080/api/resilience4j/circuit-breaker"
```

The `mode` parameter is optional and defaults to `RANDOM`. Its other values are
`SUCCESS`, `FAILURE`, and `SLOW`. The client forwards it to the upstream service
so each behavior can be reproduced. An upstream failure returns HTTP 502 with
`UPSTREAM_FAILURE`; a call blocked by the circuit breaker returns HTTP 503 with
`CIRCUIT_OPEN` and never reaches the upstream service.

For repeatable scenarios, choose `mode=SUCCESS` or `mode=FAILURE` and add a
non-negative `delayMs`. The client forwards both values to the upstream service.
Omitting `delayMs` keeps the upstream service's original timing behavior.
Both payment routes use the same upstream HTTP client and accept the same parameters.

By default, two failures or two slow calls among four measured calls open the
circuit. After five seconds, two half-open probe calls can run. Their outcomes
decide whether the circuit closes or opens again. The status endpoint exposes
the current state, failure and slow-call rates, cumulative call counts, and
average duration in milliseconds. Rates describe the main sliding window; the
counters are cumulative.

After all calls from a scenario have finished, reset the circuit before the
next run:

```sh
curl -X POST "http://localhost:8080/api/circuit-breaker/reset"
curl -X POST "http://localhost:8080/api/resilience4j/circuit-breaker/reset"
```

Each reset returns HTTP 204 and affects only its own circuit. The Resilience4j
status reports native library metrics: rates are percentages (`-1` before enough
calls), `bufferedCalls` and `failedCalls` describe the current sliding window,
and `notPermittedCalls` counts denials only in the current OPEN state. The custom
status uses fractional rates and cumulative call counts.
Reset the upstream service separately through its `/api/simulation/reset`
endpoint.

The upstream URL, circuit-breaker thresholds, and HTTP timeouts are configured
in `src/main/resources/application.yml`. For example, set the environment
variable `DEMO_CLIENT_UPSTREAM_BASE_URL` to use a different upstream address.
