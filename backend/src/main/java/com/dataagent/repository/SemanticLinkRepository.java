package com.dataagent.repository;

import com.dataagent.domain.SemanticLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SemanticLinkRepository extends JpaRepository<SemanticLink, UUID> {

    List<SemanticLink> findBySourceTypeAndSourceIdOrderByConfidenceDesc(String sourceType, UUID sourceId);

    List<SemanticLink> findByTargetTypeAndTargetIdOrderByConfidenceDesc(String targetType, UUID targetId);

    List<SemanticLink> findByLinkSource(String linkSource);

    Optional<SemanticLink> findBySourceTypeAndSourceIdAndTargetTypeAndTargetId(
            String sourceType, UUID sourceId, String targetType, UUID targetId);

    long countByLinkSource(String linkSource);

    void deleteBySourceTypeAndSourceId(String sourceType, UUID sourceId);

    void deleteByTargetTypeAndTargetId(String targetType, UUID targetId);
}
