package com.optimisante.backend.domain.catalog.repository;

import com.optimisante.backend.domain.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {
    
    // Find all root categories (no parent) for a specific tenant
    List<Category> findByTenantIdAndParentIsNull(UUID tenantId);
    
    // For fetching children if needed manually, though they are fetched via relationships
    List<Category> findByTenantIdAndParentId(UUID tenantId, UUID parentId);
}
