package edu.eci.dosw.campusseguro.training.tutor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

// Embedding of a content for the search by meaning (RF-15). One per content; it is replaced when the content changes.
@Entity
@Table(name = "content_embedding")
public class ContentEmbedding {

    @Id
    @Column(name = "content_id")
    private UUID contentId;

    @Column(nullable = false, length = 100)
    private String model;

    // JSON array of numbers: "[0.0123,-0.04,...]"
    @Column(nullable = false, columnDefinition = "TEXT")
    private String vector;

    @Column(name = "content_updated_at", nullable = false)
    private Instant contentUpdatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ContentEmbedding() {
    }

    public ContentEmbedding(UUID contentId, String model, float[] vector, Instant contentUpdatedAt) {
        this.contentId = contentId;
        this.model = model;
        this.vector = toJson(vector);
        this.contentUpdatedAt = contentUpdatedAt;
        this.createdAt = Instant.now();
    }

    public UUID getContentId() {
        return contentId;
    }

    public String getModel() {
        return model;
    }

    public Instant getContentUpdatedAt() {
        return contentUpdatedAt;
    }

    public float[] vector() {
        return fromJson(vector);
    }

    static String toJson(float[] vector) {
        StringBuilder text = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(String.format(Locale.ROOT, "%.6g", vector[i]));
        }
        return text.append(']').toString();
    }

    static float[] fromJson(String json) {
        String body = json.strip();
        body = body.substring(1, body.length() - 1).strip();
        if (body.isEmpty()) {
            return new float[0];
        }
        String[] parts = body.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i].strip());
        }
        return vector;
    }
}
