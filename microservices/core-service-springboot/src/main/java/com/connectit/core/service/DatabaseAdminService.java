package com.connectit.core.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class DatabaseAdminService {

    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getTables() {
        // Works for MySQL/PostgreSQL assuming Spring standard connection
        String sql = "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() OR table_schema = 'public'";
        List<String> tableNames = jdbcTemplate.queryForList(sql, String.class);
        
        List<Map<String, Object>> tablesInfo = new ArrayList<>();
        for (String tableName : tableNames) {
            Map<String, Object> info = new HashMap<>();
            info.put("name", tableName);
            
            // Get row count
            try {
                Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
                info.put("rowCount", count != null ? count : 0L);
            } catch (Exception e) {
                info.put("rowCount", 0L);
            }
            tablesInfo.add(info);
        }
        return tablesInfo;
    }

    public Map<String, Object> getTableData(String tableName, int page, int size, String sortBy, String sortDirection, String searchTerm) {
        // Basic SQL Injection prevention for structural elements
        if (!tableName.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("Invalid table name");
        }
        if (sortBy != null && !sortBy.matches("^[a-zA-Z0-9_]+$")) {
            sortBy = null;
        }

        // Get columns
        List<Map<String, Object>> columnsInfo = jdbcTemplate.queryForList(
                "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = ? AND (table_schema = DATABASE() OR table_schema = 'public')",
                tableName
        );

        List<String> columns = new ArrayList<>();
        for (Map<String, Object> col : columnsInfo) {
            columns.add((String) col.get("column_name") != null ? (String) col.get("column_name") : (String) col.get("COLUMN_NAME"));
        }

        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableName);
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM ").append(tableName);
        
        List<Object> args = new ArrayList<>();
        
        if (searchTerm != null && !searchTerm.trim().isEmpty() && !columns.isEmpty()) {
            sql.append(" WHERE ");
            countSql.append(" WHERE ");
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    sql.append(" OR ");
                    countSql.append(" OR ");
                }
                sql.append("CAST(").append(columns.get(i)).append(" AS CHAR) LIKE ?");
                countSql.append("CAST(").append(columns.get(i)).append(" AS CHAR) LIKE ?");
                args.add("%" + searchTerm + "%");
            }
        }

        if (sortBy != null) {
            sql.append(" ORDER BY ").append(sortBy).append(" ").append("DESC".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC");
        }

        int offset = (page - 1) * size;
        sql.append(" LIMIT ").append(size).append(" OFFSET ").append(offset);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        Long totalElements = jdbcTemplate.queryForObject(countSql.toString(), Long.class, args.toArray());

        Map<String, Object> result = new HashMap<>();
        result.put("columns", columnsInfo);
        result.put("rows", rows);
        result.put("totalElements", totalElements != null ? totalElements : 0L);
        result.put("totalPages", (int) Math.ceil((double) (totalElements != null ? totalElements : 0L) / size));
        result.put("currentPage", page);
        
        return result;
    }
}
