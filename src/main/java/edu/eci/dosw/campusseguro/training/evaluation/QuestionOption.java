package edu.eci.dosw.campusseguro.training.evaluation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "question_option")
public class QuestionOption {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 300)
    private String text;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    protected QuestionOption() {
    }

    QuestionOption(Question question, int position, String text, boolean correct) {
        this.id = UUID.randomUUID();
        this.question = question;
        this.position = position;
        this.text = text;
        this.correct = correct;
    }

    public UUID getId() {
        return id;
    }

    public int getPosition() {
        return position;
    }

    public String getText() {
        return text;
    }

    public boolean isCorrect() {
        return correct;
    }
}
