package com.medplus.agreement_tracker_backend.service.master;

import com.medplus.agreement_tracker_backend.dto.request.MasterPageRequest;
import com.medplus.agreement_tracker_backend.dto.request.master.AgreementGroupMasterRequest;
import com.medplus.agreement_tracker_backend.dto.response.PagedResponse;
import com.medplus.agreement_tracker_backend.dto.response.master.AgreementGroupMasterResponse;
import com.medplus.agreement_tracker_backend.entity.AgreementGroup;
import com.medplus.agreement_tracker_backend.exception.DuplicateResourceException;
import com.medplus.agreement_tracker_backend.exception.ResourceNotFoundException;
import com.medplus.agreement_tracker_backend.repository.AgreementGroupRepository;
import com.medplus.agreement_tracker_backend.util.SpecificationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class AgreementGroupMasterService {

    private final AgreementGroupRepository repository;

    @Transactional(readOnly = true)
    public PagedResponse<AgreementGroupMasterResponse> search(MasterPageRequest req) {
        Specification<AgreementGroup> spec = buildSpec(req.getFilters());
        Sort sort = Sort.by(Sort.Direction.fromString(req.getSortDirection()), req.getSortBy());
        Page<AgreementGroup> page = repository.findAll(spec, PageRequest.of(req.getPage(), req.getSize(), sort));
        return PagedResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public AgreementGroupMasterResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<AgreementGroupMasterResponse> findAllActive() {
        return repository.findByIsActiveTrueOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    public AgreementGroupMasterResponse create(AgreementGroupMasterRequest req, Long userId) {
        String name = req.getName().trim();
        if (repository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("Agreement group name already exists: " + name);
        }
        AgreementGroup entity = AgreementGroup.builder()
                .name(name)
                .build();
        entity.setCreatedByUserId(userId);
        return toResponse(repository.save(entity));
    }

    public AgreementGroupMasterResponse update(Long id, AgreementGroupMasterRequest req, Long userId) {
        AgreementGroup entity = findOrThrow(id);
        String name = req.getName().trim();
        if (!name.equalsIgnoreCase(entity.getName()) && repository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("Agreement group name already exists: " + name);
        }
        entity.setName(name);
        if (req.getIsActive() != null) {
            entity.setActive(req.getIsActive());
        }
        entity.setUpdatedByUserId(userId);
        return toResponse(repository.save(entity));
    }

    public void toggleStatus(Long id, Long userId) {
        AgreementGroup entity = findOrThrow(id);
        entity.setActive(!entity.isActive());
        entity.setUpdatedByUserId(userId);
        repository.save(entity);
    }

    private AgreementGroup findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("AgreementGroup", id));
    }

    private Specification<AgreementGroup> buildSpec(Map<String, String> filters) {
        Specification<AgreementGroup> spec = SpecificationUtils.empty();
        if (filters == null || filters.isEmpty()) {
            return spec;
        }
        if (filters.containsKey("name")) {
            spec = spec.and(SpecificationUtils.stringLike("name", filters.get("name")));
        }
        if (filters.containsKey("isActive")) {
            spec = spec.and(SpecificationUtils.booleanEquals("isActive",
                    SpecificationUtils.parseBoolean(filters.get("isActive"))));
        }
        return spec;
    }

    private AgreementGroupMasterResponse toResponse(AgreementGroup entity) {
        return AgreementGroupMasterResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .isActive(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .createdByUserId(entity.getCreatedByUserId())
                .updatedAt(entity.getUpdatedAt())
                .updatedByUserId(entity.getUpdatedByUserId())
                .build();
    }
}
