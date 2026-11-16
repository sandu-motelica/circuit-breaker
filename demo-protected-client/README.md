# Protected HTTP client

A Spring Boot client that calls `demo-unstable-service` through `circuit-breaker-core`.
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
curl "http://localhost:8080/api/circuit-breaker"
```

The `mode` parameter is optional and defaults to `RANDOM`. Its other values are
`SUCCESS`, `FAILURE`, and `SLOW`. The client forwards it to the upstream service
so each behavior can be reproduced. An upstream failure returns HTTP 502 with
`UPSTREAM_FAILURE`; a call blocked by the circuit breaker returns HTTP 503 with
`CIRCUIT_OPEN` and never reaches the upstream service.

By default, two failures or two slow calls among four measured calls open the
circuit. After five seconds, two half-open probe calls can run. Their outcomes
decide whether the circuit closes or opens again. The status endpoint exposes
the current state, failure and slow-call rates, cumulative call counts, and
average duration in milliseconds. Rates describe the main sliding window; the
counters are cumulative.

The upstream URL, circuit-breaker thresholds, and HTTP timeouts are configured
in `src/main/resources/application.yml`. For example, set the environment
variable `DEMO_CLIENT_UPSTREAM_BASE_URL` to use a different upstream address.
