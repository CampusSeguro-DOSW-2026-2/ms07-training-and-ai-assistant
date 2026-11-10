package edu.eci.dosw.campusseguro.training.content;

import edu.eci.dosw.campusseguro.training.category.Category;
import edu.eci.dosw.campusseguro.training.common.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// RF-10: learning content. It is never deleted, only deactivated.
// Dates are instants in UTC (TIMESTAMPTZ columns), like MS01 and MS04.
@Entity
@Table(name = "content")
public class Content {

    @Id
    private UUID id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 20000)
    private String body;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Content() {
    }

    public Content(String title, String body, Category category) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.body = body;
        this.category = category;
        this.status = Status.ACTIVO;
        this.createdAt = now();
        this.updatedAt = this.createdAt;
    }

    public void update(String title, String body, Category category) {
        this.title = title;
        this.body = body;
        this.category = category;
        this.updatedAt = now();
    }

    public void deactivate() {
        this.status = Status.INACTIVO;
        this.updatedAt = now();
    }

    public void reactivate() {
        this.status = Status.ACTIVO;
        this.updatedAt = now();
    }

    public boolean isActive() {
        return status == Status.ACTIVO;
    }

    // Visible to the community only when the content and its category are both active.
    public boolean isVisibleToCommunity() {
        return isActive() && category.isActive();
    }

    private static Instant now() {
        return Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public Category getCategory() {
        return category;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
