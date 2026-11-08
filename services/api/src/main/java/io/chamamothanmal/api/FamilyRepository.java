package io.chamamothanmal.api;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FamilyRepository {
    final JdbcTemplate jdbc;
    public FamilyRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Map<String,Object>> list(String sql, Object... args) {
        return jdbc.query(sql, (rs, index) -> {
            Map<String,Object> row = new LinkedHashMap<>();
            var md = rs.getMetaData();
            for (int i=1; i<=md.getColumnCount(); i++) {
                String key = md.getColumnLabel(i).toLowerCase(Locale.ROOT);
                StringBuilder camel = new StringBuilder(); boolean upper = false;
                for (char c : key.toCharArray()) {
                    if (c == '_') upper = true;
                    else { camel.append(upper ? Character.toUpperCase(c) : c); upper = false; }
                }
                row.put(camel.toString(), rs.getObject(i));
            }
            return row;
        }, args);
    }
    public Map<String,Object> one(String sql, Object... args) {
        var rows = list(sql,args);
        if (rows.isEmpty()) throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,
            "NOT_FOUND", "The resource is unavailable or not permitted.");
        return rows.get(0);
    }
    public int update(String sql,Object... args) { return jdbc.update(sql,args); }
    public void ensureUser(String id) {
        String product = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)
            c -> c.getMetaData().getDatabaseProductName());
        if ("PostgreSQL".equals(product)) update("INSERT INTO cm_users(id) VALUES(?) ON CONFLICT(id) DO NOTHING",id);
        else update("MERGE INTO cm_users(id) KEY(id) VALUES(?)",id);
    }
}
