package com.isocode.township.flat;

import com.isocode.township.common.BadRequestException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FlatSearchController {

    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_QUERY_LENGTH = 50;
    private static final int MAX_RESULTS = 20;

    private final JdbcTemplate jdbc;

    public FlatSearchController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/desk/flats/search")
    public List<FlatSearchResult> search(@RequestParam("q") String query) {
        String text = query == null ? "" : query.strip();
        if (text.length() < MIN_QUERY_LENGTH || text.length() > MAX_QUERY_LENGTH) {
            throw new BadRequestException("Type 2 to 50 characters of a flat code or a resident's name.");
        }
        String codePattern = "%" + likeEscaped(text.toUpperCase(Locale.ROOT).replaceAll("[\\s/]+", "-")) + "%";
        String namePattern = "%" + likeEscaped(text) + "%";
        return jdbc.query("""
                SELECT f.id, f.code,
                       string_agg(u.full_name, '|' ORDER BY m.relation, u.full_name) AS residents
                FROM flat f
                LEFT JOIN flat_member m ON m.flat_id = f.id AND m.active
                LEFT JOIN app_user u ON u.id = m.user_id
                WHERE f.code ILIKE ? ESCAPE '\\'
                   OR f.id IN (SELECT m2.flat_id FROM flat_member m2 JOIN app_user u2 ON u2.id = m2.user_id
                               WHERE m2.active AND u2.full_name ILIKE ? ESCAPE '\\')
                GROUP BY f.id, f.code
                ORDER BY f.code
                LIMIT ?
                """, (resultSet, rowNum) -> new FlatSearchResult(resultSet.getLong("id"), resultSet.getString("code"),
                        residents(resultSet.getString("residents"))),
                codePattern, namePattern, MAX_RESULTS);
    }

    private static List<String> residents(String joined) {
        return joined == null ? List.of() : Arrays.asList(joined.split("\\|"));
    }

    private static String likeEscaped(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public record FlatSearchResult(Long id, String code, List<String> residents) {
    }
}
