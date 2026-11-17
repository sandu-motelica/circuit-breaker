# Successful-call throughput

Millions of operations per second, one thread. The chart uses the throughput
scores in [`results.csv`](../results.csv); higher bars mean more completed calls
per second in this local JMH run.

```mermaid
xychart-beta
    title "Successful calls through the breaker"
    x-axis ["Count custom", "Count Resilience4j", "Time custom", "Time Resilience4j"]
    y-axis "M operations/s" 0 --> 16
    bar [12.30, 15.06, 11.23, 13.15]
```
