package edu.eci.dosw.campusseguro.training.tutor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ContentEmbeddingRepository extends JpaRepository<ContentEmbedding, UUID> {

    List<ContentEmbedding> findByModel(String model);
}
