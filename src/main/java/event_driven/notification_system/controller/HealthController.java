package event_driven.notification_system.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public Map<String, Object> getHealth() {
        Map<String, Object> healthInfo = new HashMap<>();
        healthInfo.put("status", "UP");
        
        try {
            // Run simple query to test connection to Supabase PostgreSQL database
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (result != null && result == 1) {
                healthInfo.put("database", "CONNECTED");
            } else {
                healthInfo.put("database", "UNEXPECTED_RESPONSE");
            }
        } catch (Exception e) {
            healthInfo.put("status", "DOWN");
            healthInfo.put("database", "DISCONNECTED");
            healthInfo.put("error", e.getMessage());
        }
        
        return healthInfo;
    }
}
