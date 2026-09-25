package com.medplus.agreement_tracker_backend.service.master;

import com.medplus.agreement_tracker_backend.dto.request.MasterPageRequest;
import com.medplus.agreement_tracker_backend.dto.request.master.PriceOffLocationMasterRequest;
import com.medplus.agreement_tracker_backend.dto.response.PagedResponse;
import com.medplus.agreement_tracker_backend.dto.response.master.PriceOffLocationMasterResponse;
import com.medplus.agreement_tracker_backend.entity.PriceOffLocationMaster;
import com.medplus.agreement_tracker_backend.exception.DuplicateResourceException;
import com.medplus.agreement_tracker_backend.exception.ResourceNotFoundException;
import com.medplus.agreement_tracker_backend.repository.PriceOffLocationMasterRepository;
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
public class PriceOffLocationMasterService {

    private final PriceOffLocationMasterRepository repository;

    @Transactional(readOnly = true)
    public PagedResponse<PriceOffLocationMasterResponse> search(MasterPageRequest req) {
        Specification<PriceOffLocationMaster> spec = buildSpec(req.getFilters());
        Sort sort = Sort.by(Sort.Direction.fromString(req.getSortDirection()), req.getSortBy());
        Page<PriceOffLocationMaster> page = repository.findAll(spec, PageRequest.of(req.getPage(), req.getSize(), sort));
        return PagedResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PriceOffLocationMasterResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<PriceOffLocationMasterResponse> findAllActive() {
        return repository.findByIsActiveTrueOrderByCodeAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    public PriceOffLocationMasterResponse create(PriceOffLocationMasterRequest req) {
        String code = req.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Price off location code already exists: " + code);
        }
        PriceOffLocationMaster entity = PriceOffLocationMaster.builder()
                .name(req.getName().trim())
                .code(code)
                .build();
        return toResponse(repository.save(entity));
    }

    public PriceOffLocationMasterResponse update(Long id, PriceOffLocationMasterRequest req) {
        PriceOffLocationMaster entity = findOrThrow(id);
        String code = req.getCode().trim().toUpperCase();
        if (!code.equalsIgnoreCase(entity.getCode()) && repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Price off location code already exists: " + code);
        }
        entity.setName(req.getName().trim());
        entity.setCode(code);
        if (req.getIsActive() != null) {
            entity.setActive(req.getIsActive());
        }
        return toResponse(repository.save(entity));
    }

    public void toggleStatus(Long id) {
        PriceOffLocationMaster entity = findOrThrow(id);
        entity.setActive(!entity.isActive());
        repository.save(entity);
    }

    private PriceOffLocationMaster findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PriceOffLocationMaster", id));
    }

    private Specification<PriceOffLocationMaster> buildSpec(Map<String, String> filters) {
        Specification<PriceOffLocationMaster> spec = SpecificationUtils.empty();
        if (filters == null || filters.isEmpty()) {
            return spec;
        }
        if (filters.containsKey("name")) {
            spec = spec.and(SpecificationUtils.stringLike("name", filters.get("name")));
        }
        if (filters.containsKey("code")) {
            spec = spec.and(SpecificationUtils.stringLike("code", filters.get("code")));
        }
        if (filters.containsKey("isActive")) {
            spec = spec.and(SpecificationUtils.booleanEquals(
                    "isActive",
                    SpecificationUtils.parseBoolean(filters.get("isActive"))));
        }
        return spec;
    }

    private PriceOffLocationMasterResponse toResponse(PriceOffLocationMaster entity) {
        return PriceOffLocationMasterResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .code(entity.getCode())
                .isActive(entity.isActive())
                .build();
    }
}
