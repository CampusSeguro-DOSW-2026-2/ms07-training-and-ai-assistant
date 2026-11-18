package edu.eci.dosw.campusseguro.training.evaluation;

import edu.eci.dosw.campusseguro.training.category.Category;
import edu.eci.dosw.campusseguro.training.common.Status;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

// RF-14: short evaluation of a category. It is never deleted, only deactivated.
@Entity
@Table(name = "evaluation")
public class Evaluation {

    @Id
    private UUID id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 150)
    private String topic;

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

    @OneToMany(mappedBy = "evaluation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<Question> questions = new ArrayList<>();

    protected Evaluation() {
    }

    public Evaluation(String title, String topic, Category category) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.topic = topic;
        this.category = category;
        this.status = Status.ACTIVO;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String title, String topic, Category category) {
        this.title = title;
        this.topic = topic;
        this.category = category;
        this.updatedAt = Instant.now();
    }

    public void clearQuestions() {
        questions.clear();
    }

    public Question addQuestion(String statement) {
        Question question = new Question(this, questions.size() + 1, statement);
        questions.add(question);
        return question;
    }

    public void deactivate() {
        this.status = Status.INACTIVO;
        this.updatedAt = Instant.now();
    }

    public void reactivate() {
        this.status = Status.ACTIVO;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return status == Status.ACTIVO;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getTopic() {
        return topic;
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

    public List<Question> getQuestions() {
        return Collections.unmodifiableList(questions);
    }
}
