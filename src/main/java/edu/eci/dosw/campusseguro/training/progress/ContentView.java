package edu.eci.dosw.campusseguro.training.progress;

import edu.eci.dosw.campusseguro.training.content.Content;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

// RF-13: a content opened by a user. There is a single row per user and content (rule 2).
@Entity
@Table(name = "content_view",
        uniqueConstraints = @UniqueConstraint(name = "ux_content_view_user_content",
                columnNames = {"user_id", "content_id"}))
public class ContentView {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @Column(nullable = false)
    private Instant firstViewedAt;

    @Column(nullable = false)
    private Instant lastViewedAt;

    protected ContentView() {
    }

    public ContentView(UUID userId, Content content, Instant viewedAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.content = content;
        this.firstViewedAt = viewedAt;
        this.lastViewedAt = viewedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Content getContent() {
        return content;
    }

    public Instant getFirstViewedAt() {
        return firstViewedAt;
    }

    public Instant getLastViewedAt() {
        return lastViewedAt;
    }
}
