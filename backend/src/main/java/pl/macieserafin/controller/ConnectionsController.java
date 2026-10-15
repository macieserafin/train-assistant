package pl.macieserafin.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import pl.macieserafin.model.ConnectionSearchResult;
import pl.macieserafin.service.ConnectionService;
import java.time.LocalDateTime;

@RestController
public class ConnectionsController {
    private final ConnectionService connections;

    public ConnectionsController(ConnectionService connections) { this.connections = connections; }

    @GetMapping("/api/connections")
    public ConnectionSearchResult search(@RequestParam String from, @RequestParam String to,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime arrivalBefore,
            @RequestParam(defaultValue = "recommended") String mode) {
        return connections.find(from, to, arrivalBefore, mode);
    }
}
