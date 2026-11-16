package io.github.sandumotelica.circuitbreaker.demo.unstable;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulation")
class SimulationController {
    private final PaymentSimulationService simulationService;

    SimulationController(PaymentSimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @GetMapping("/stats")
    SimulationStatistics statistics() {
        return new SimulationStatistics(simulationService.receivedRequests());
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset() {
        simulationService.resetStatistics();
    }
}
