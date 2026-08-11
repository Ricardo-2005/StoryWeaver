package com.storyweaver.consistency.repository;

import com.storyweaver.consistency.domain.ItemOwnership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemOwnershipRepository extends JpaRepository<ItemOwnership, UUID> {
    Optional<ItemOwnership> findByProjectIdAndItemKey(UUID projectId, String itemKey);

    List<ItemOwnership> findAllByProjectIdOrderByItemNameAsc(UUID projectId);
}
