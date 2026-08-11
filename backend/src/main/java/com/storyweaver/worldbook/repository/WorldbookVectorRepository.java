package com.storyweaver.worldbook.repository;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorldbookVectorRepository {
    private final JdbcTemplate jdbc;

    public WorldbookVectorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void write(UUID entryId, float[] vector) {
        jdbc.update(
                "update worldbook_entry set embedding=cast(? as vector) where id=?", vectorLiteral(vector), entryId);
    }

    public void clear(UUID entryId) {
        jdbc.update("update worldbook_entry set embedding=null where id=?", entryId);
    }

    public List<VectorMatch> search(UUID projectId, float[] query, int topK) {
        return jdbc.query(
                """
                select id, greatest(0.0, 1.0 - (embedding <=> cast(? as vector))) as similarity
                from worldbook_entry
                where project_id=? and active=true and vector_enabled=true
                  and embedding_status='AVAILABLE' and embedding is not null
                order by embedding <=> cast(? as vector), priority desc
                limit ?
                """,
                (rs, row) -> new VectorMatch(rs.getObject("id", UUID.class), rs.getDouble("similarity")),
                vectorLiteral(query),
                projectId,
                vectorLiteral(query),
                topK);
    }

    private String vectorLiteral(float[] vector) {
        StringBuilder literal = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) literal.append(',');
            literal.append(String.format(Locale.ROOT, "%.8f", vector[i]));
        }
        return literal.append(']').toString();
    }

    public record VectorMatch(UUID entryId, double similarity) {}
}
