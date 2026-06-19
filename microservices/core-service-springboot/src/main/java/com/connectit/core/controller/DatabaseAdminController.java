package com.connectit.core.controller;

import com.connectit.core.service.DatabaseAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/database")
@RequiredArgsConstructor
public class DatabaseAdminController {

    private final DatabaseAdminService databaseAdminService;

    @GetMapping("/tables")
    @PreAuthorize("hasAnyAuthority('ULTRA_SUPER_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getTables() {
        return ResponseEntity.ok(databaseAdminService.getTables());
    }

    @GetMapping("/tables/{tableName}")
    @PreAuthorize("hasAnyAuthority('ULTRA_SUPER_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getTableData(
            @PathVariable String tableName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(databaseAdminService.getTableData(tableName, page, size, sortBy, sortDirection, search));
    }
}
