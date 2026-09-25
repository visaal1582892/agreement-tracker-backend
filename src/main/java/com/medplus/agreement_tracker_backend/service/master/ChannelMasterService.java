package com.medplus.agreement_tracker_backend.service.master;

import com.medplus.agreement_tracker_backend.dto.request.MasterPageRequest;
import com.medplus.agreement_tracker_backend.dto.request.master.ChannelMasterRequest;
import com.medplus.agreement_tracker_backend.dto.response.PagedResponse;
import com.medplus.agreement_tracker_backend.dto.response.master.ChannelMasterResponse;
import com.medplus.agreement_tracker_backend.entity.ChannelMaster;
import com.medplus.agreement_tracker_backend.exception.DuplicateResourceException;
import com.medplus.agreement_tracker_backend.exception.ResourceNotFoundException;
import com.medplus.agreement_tracker_backend.repository.ChannelMasterRepository;
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
public class ChannelMasterService {

    private final ChannelMasterRepository repository;

    @Transactional(readOnly = true)
    public PagedResponse<ChannelMasterResponse> search(MasterPageRequest req) {
        Specification<ChannelMaster> spec = buildSpec(req.getFilters());
        Sort sort = Sort.by(Sort.Direction.fromString(req.getSortDirection()), req.getSortBy());
        Page<ChannelMaster> page = repository.findAll(spec, PageRequest.of(req.getPage(), req.getSize(), sort));
        return PagedResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public ChannelMasterResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<ChannelMasterResponse> findAllActive() {
        return repository.findByIsActiveTrueOrderByChannelNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    public ChannelMasterResponse create(ChannelMasterRequest req) {
        String code = req.getChannelCode().trim().toUpperCase();
        if (repository.existsByChannelCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Channel code already exists: " + code);
        }
        ChannelMaster entity = ChannelMaster.builder()
                .channelName(req.getChannelName().trim())
                .channelCode(code)
                .build();
        return toResponse(repository.save(entity));
    }

    public ChannelMasterResponse update(Long id, ChannelMasterRequest req) {
        ChannelMaster entity = findOrThrow(id);
        String code = req.getChannelCode().trim().toUpperCase();
        if (!code.equalsIgnoreCase(entity.getChannelCode()) && repository.existsByChannelCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Channel code already exists: " + code);
        }
        entity.setChannelName(req.getChannelName().trim());
        entity.setChannelCode(code);
        if (req.getIsActive() != null) {
            entity.setActive(req.getIsActive());
        }
        return toResponse(repository.save(entity));
    }

    public void toggleStatus(Long id) {
        ChannelMaster entity = findOrThrow(id);
        entity.setActive(!entity.isActive());
        repository.save(entity);
    }

    private ChannelMaster findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ChannelMaster", id));
    }

    private Specification<ChannelMaster> buildSpec(Map<String, String> filters) {
        Specification<ChannelMaster> spec = SpecificationUtils.empty();
        if (filters == null || filters.isEmpty()) {
            return spec;
        }
        if (filters.containsKey("channelName")) {
            spec = spec.and(SpecificationUtils.stringLike("channelName", filters.get("channelName")));
        }
        if (filters.containsKey("channelCode")) {
            spec = spec.and(SpecificationUtils.stringLike("channelCode", filters.get("channelCode")));
        }
        if (filters.containsKey("isActive")) {
            spec = spec.and(SpecificationUtils.booleanEquals(
                    "isActive",
                    SpecificationUtils.parseBoolean(filters.get("isActive"))));
        }
        return spec;
    }

    private ChannelMasterResponse toResponse(ChannelMaster entity) {
        return ChannelMasterResponse.builder()
                .id(entity.getId())
                .channelName(entity.getChannelName())
                .channelCode(entity.getChannelCode())
                .isActive(entity.isActive())
                .build();
    }
}
