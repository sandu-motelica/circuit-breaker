# Unstable HTTP service

A small payment endpoint for testing clients protected by the circuit-breaker library. It is a standalone Spring Boot application and does not depend on `circuit-breaker-core`.

Run from the repository root:

```sh
./mvnw -pl demo-unstable-service spring-boot:run
```

On Windows, replace `./mvnw` with `.\mvnw.cmd`. The service listens on port `8081`.

```sh
curl "http://localhost:8081/api/payments/payment-123"
curl "http://localhost:8081/api/payments/payment-123?mode=SUCCESS"
curl "http://localhost:8081/api/payments/payment-123?mode=FAILURE"
curl "http://localhost:8081/api/payments/payment-123?mode=SLOW"
curl "http://localhost:8081/api/payments/payment-123?mode=SUCCESS&delayMs=20"
curl "http://localhost:8081/api/payments/payment-123?mode=FAILURE&delayMs=1500"
```

The default `RANDOM` mode chooses failures and slow responses independently. Failures return HTTP 503; successful responses contain a payment ID and `APPROVED` status. The explicit modes let you reproduce each behavior while developing or testing the protected client.

For repeatable scenarios, set `mode=SUCCESS` or `mode=FAILURE` and provide a
non-negative `delayMs`. The delay overrides the default timing for that request,
so outcome and duration can be chosen independently. `mode=SLOW` still uses
the configured slow delay when `delayMs` is omitted.

Before each scenario run, reset the upstream request count. After the run, read
the count to see how many payment requests reached this service:

```sh
curl -X POST "http://localhost:8081/api/simulation/reset"
curl "http://localhost:8081/api/simulation/stats"
```

The response contains `receivedRequests`. Reset only after all requests from
the previous run have completed.

Edit `src/main/resources/application.yml` to change `demo.unstable.failure-probability`, `demo.unstable.slow-probability`, and `demo.unstable.slow-delay`. The default values are 0.4, 0.3, and 1500 ms respectively.
