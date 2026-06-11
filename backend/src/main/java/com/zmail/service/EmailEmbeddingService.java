package com.zmail.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zmail.model.ProcessingResult;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailEmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final com.zmail.config.AgentProperties agentProperties;

    /** Embeds the email summary and persists it to email_embeddings. Returns false on failure. */
    public boolean embed(ProcessingResult result) {
        try {
            String content = buildContent(result);
            float[] vector = embeddingModel.embed(content).content().vector();

            jdbcTemplate.update(
                "INSERT INTO email_embeddings (user_id, source_id, content, embedding, metadata) " +
                "VALUES (?, ?, ?, ?::vector, ?::jsonb) " +
                "ON CONFLICT (user_id, source_id) DO UPDATE " +
                "  SET content = EXCLUDED.content, " +
                "      embedding = EXCLUDED.embedding, " +
                "      metadata = EXCLUDED.metadata",
                result.getUserId(),
                result.getEmailProviderId(),
                content,
                toVectorString(vector),
                buildMetadata(result)
            );
            log.debug("Embedded email {} for user {}", result.getEmailProviderId(), result.getUserId());
            return true;
        } catch (Exception e) {
            log.warn("Failed to embed email {} for user {}: {}",
                result.getEmailProviderId(), result.getUserId(), e.getMessage());
            return false;
        }
    }

    /**
     * Finds the top-K emails most semantically similar to the query.
     * Returns an empty list if the vector search fails.
     */
    @Transactional(readOnly = true)
    public List<SimilarEmailResult> search(UUID userId, String query, int topK) {
        try {
            float[] queryVector = embeddingModel.embed(query).content().vector();
            String vec = toVectorString(queryVector);

            return jdbcTemplate.execute((java.sql.Connection conn) -> {
                // SET LOCAL only lives for this transaction; raises probes from default 1
                // to 10 so IVFFlat scans 10% of clusters instead of 1%, giving acceptable recall.
                try (var stmt = conn.createStatement()) {
                    stmt.execute("SET LOCAL ivfflat.probes = 10");
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT pr.id, pr.subject, pr.sender, pr.summary, pr.category, pr.priority " +
                        "FROM email_embeddings ee " +
                        "JOIN processing_results pr " +
                        "  ON pr.email_provider_id = ee.source_id AND pr.user_id = ee.user_id " +
                        "WHERE ee.user_id = ? " +
                        "  AND ee.embedding <=> ?::vector < ? " +
                        "ORDER BY ee.embedding <=> ?::vector " +
                        "LIMIT ?")) {
                    ps.setObject(1, userId);
                    ps.setString(2, vec);
                    ps.setDouble(3, agentProperties.getEmbeddingSearchThreshold());
                    ps.setString(4, vec);
                    ps.setInt(5, topK);
                    try (ResultSet rs = ps.executeQuery()) {
                        List<SimilarEmailResult> results = new ArrayList<>();
                        while (rs.next()) {
                            results.add(new SimilarEmailResult(
                                rs.getObject("id", UUID.class),
                                rs.getString("subject"),
                                rs.getString("sender"),
                                rs.getString("summary"),
                                rs.getString("category"),
                                rs.getString("priority")
                            ));
                        }
                        return results;
                    }
                }
            });
        } catch (Exception e) {
            log.error("Vector search failed for user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private String buildContent(ProcessingResult result) {
        return "Subject: " + nullSafe(result.getSubject()) +
               "\nFrom: " + nullSafe(result.getSender()) +
               "\nSummary: " + nullSafe(result.getSummary());
    }

    private String toVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format("%.8f", vector[i]));
        }
        return sb.append("]").toString();
    }

    private String buildMetadata(ProcessingResult result) {
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("category", result.getCategory());
            meta.put("priority", result.getPriority());
            meta.put("sentiment", result.getSentiment());
            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }

    public record SimilarEmailResult(
        UUID id,
        String subject,
        String sender,
        String summary,
        String category,
        String priority
    ) {}
}