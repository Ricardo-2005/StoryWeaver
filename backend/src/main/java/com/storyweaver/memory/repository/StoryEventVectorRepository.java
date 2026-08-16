package com.storyweaver.memory.repository;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StoryEventVectorRepository {
    private final JdbcTemplate jdbc;

    public StoryEventVectorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void write(UUID eventId, float[] vector) {
        jdbc.update("update story_event set embedding=cast(? as vector) where id=?", vectorLiteral(vector), eventId);
    }

    public void clear(UUID eventId) {
        jdbc.update("update story_event set embedding=null where id=?", eventId);
    }

    public List<VectorMatch> search(UUID projectId, float[] query, Integer maxChapterNo, int limit) {
        String literal = vectorLiteral(query);
        return jdbc.query(
                """
                select id, greatest(0.0, 1.0 - (embedding <=> cast(? as vector))) as similarity
                from story_event
                where project_id=? and embedding_status='AVAILABLE' and embedding is not null
                  and retrieval_eligible=true and lifecycle_status='ACTIVE'
                  and (? is null or chapter_no is null or chapter_no <= ?)
                order by embedding <=> cast(? as vector)
                limit ?
                """,
                (rs, row) -> new VectorMatch(rs.getObject("id", UUID.class), rs.getDouble("similarity")),
                literal,
                projectId,
                maxChapterNo,
                maxChapterNo,
                literal,
                limit);
    }

    private String vectorLiteral(float[] vector) {
        StringBuilder literal = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) literal.append(',');
            literal.append(String.format(Locale.ROOT, "%.8f", vector[i]));
        }
        return literal.append(']').toString();
    }

    public record VectorMatch(UUID eventId, double similarity) {}
}
