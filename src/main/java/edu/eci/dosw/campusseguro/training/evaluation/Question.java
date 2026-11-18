package edu.eci.dosw.campusseguro.training.evaluation;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "question")
public class Question {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluation_id", nullable = false)
    private Evaluation evaluation;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 500)
    private String statement;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<QuestionOption> options = new ArrayList<>();

    protected Question() {
    }

    Question(Evaluation evaluation, int position, String statement) {
        this.id = UUID.randomUUID();
        this.evaluation = evaluation;
        this.position = position;
        this.statement = statement;
    }

    public void addOption(String text, boolean correct) {
        options.add(new QuestionOption(this, options.size() + 1, text, correct));
    }

    public UUID getId() {
        return id;
    }

    public int getPosition() {
        return position;
    }

    public String getStatement() {
        return statement;
    }

    public List<QuestionOption> getOptions() {
        return Collections.unmodifiableList(options);
    }
}
