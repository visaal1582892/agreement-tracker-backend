package com.medplus.agreement_tracker_backend.service;

import com.medplus.agreement_tracker_backend.dto.request.BulkPriceOffIdsRequest;
import com.medplus.agreement_tracker_backend.dto.request.BulkPriceOffRejectRequest;
import com.medplus.agreement_tracker_backend.dto.request.BulkUpdateCampaignIdRequest;
import com.medplus.agreement_tracker_backend.dto.request.PriceOffUpdateRequestDto;
import com.medplus.agreement_tracker_backend.dto.request.UpdateCampaignIdRequest;
import com.medplus.agreement_tracker_backend.dto.response.ConsumerPriceOffCampaignResponse;
import com.medplus.agreement_tracker_backend.dto.request.PriceOffListFilters;
import com.medplus.agreement_tracker_backend.dto.response.PagedResponse;
import com.medplus.agreement_tracker_backend.dto.response.PriceOffFilterOptionsResponse;
import com.medplus.agreement_tracker_backend.dto.response.PriceOffLocationOptionDto;
import com.medplus.agreement_tracker_backend.dto.request.PriceOffCommitRequest;
import com.medplus.agreement_tracker_backend.dto.response.PriceOffPreviewResponse;
import com.medplus.agreement_tracker_backend.dto.response.PriceOffUploadSummaryDto;
import com.medplus.agreement_tracker_backend.enums.PriceOffDisplayStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ConsumerPriceOffService {

    byte[] generateTemplate();

    PriceOffPreviewResponse previewUpload(MultipartFile file);

    PriceOffUploadSummaryDto commitCampaigns(PriceOffCommitRequest request, Long currentUserId);

    /** @deprecated Prefer preview + commit. Kept for compatibility — now returns preview-only error. */
    @Deprecated
    PriceOffUploadSummaryDto uploadCampaigns(MultipartFile file, Long currentUserId);

    PriceOffFilterOptionsResponse getFilterOptions();

    List<PriceOffLocationOptionDto> getAllLocations();

    PagedResponse<ConsumerPriceOffCampaignResponse> listCampaigns(
            PriceOffListFilters filters,
            Pageable pageable,
            Long currentUserId);

    ConsumerPriceOffCampaignResponse getCampaign(Long id);

    ConsumerPriceOffCampaignResponse updatePriceOff(Long id, PriceOffUpdateRequestDto request, Long currentUserId);

    ConsumerPriceOffCampaignResponse updateCampaignId(Long id, UpdateCampaignIdRequest request, Long currentUserId);

    List<ConsumerPriceOffCampaignResponse> bulkUpdateCampaignId(
            BulkUpdateCampaignIdRequest request,
            Long currentUserId);

    List<ConsumerPriceOffCampaignResponse> bulkSubmit(BulkPriceOffIdsRequest request, Long currentUserId);

    void bulkDelete(BulkPriceOffIdsRequest request, Long currentUserId);

    List<ConsumerPriceOffCampaignResponse> bulkApprove(BulkPriceOffIdsRequest request, Long currentUserId);

    List<ConsumerPriceOffCampaignResponse> bulkReject(BulkPriceOffRejectRequest request, Long currentUserId);

    ConsumerPriceOffCampaignResponse approve(Long id, Long currentUserId);

    ConsumerPriceOffCampaignResponse reject(Long id, String remarks, Long currentUserId);
}
