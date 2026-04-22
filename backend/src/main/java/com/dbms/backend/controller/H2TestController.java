package com.dbms.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/test")
@CrossOrigin(origins = "http://localhost:5173")
public class H2TestController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/h2")
    public Map<String, Object> test() {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS test_table (id INT PRIMARY KEY, name VARCHAR(100))");
            jdbcTemplate.execute("INSERT INTO test_table VALUES (1, 'Hello H2')");
            List<Map<String, Object>> result = jdbcTemplate.queryForList("SELECT * FROM test_table");
            return Map.of(
                "success", true,
                "data", result,
                "driver", jdbcTemplate.getDataSource().getConnection().getMetaData().getDriverName()
            );
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }
}