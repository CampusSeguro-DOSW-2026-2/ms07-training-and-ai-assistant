package edu.eci.dosw.campusseguro.training.category;

import edu.eci.dosw.campusseguro.training.common.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

// RF-11: content category. It is never deleted, only deactivated.
@Entity
@Table(name = "category")
public class Category {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    protected Category() {
    }

    public Category(String name, String description) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.status = Status.ACTIVO;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void deactivate() {
        this.status = Status.INACTIVO;
    }

    public void reactivate() {
        this.status = Status.ACTIVO;
    }

    public boolean isActive() {
        return status == Status.ACTIVO;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Status getStatus() {
        return status;
    }
}
