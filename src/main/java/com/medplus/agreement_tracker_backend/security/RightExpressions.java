package com.medplus.agreement_tracker_backend.security;

/**
 * SpEL-friendly authority expressions for {@code @PreAuthorize}.
 * Mirrors {@link com.medplus.agreement_tracker_backend.enums.RightCode}.
 */
public final class RightExpressions {

    private RightExpressions() {
    }

    public static final String DASHBOARD_VIEW = "hasAuthority('DASHBOARD_VIEW')";
    public static final String AGREEMENT_VIEW = "hasAnyAuthority('AGREEMENT_VIEW_MY', 'AGREEMENT_VIEW_ALL', 'DRAFT_VIEW_MY', 'DRAFT_VIEW_ALL')";
    public static final String AGREEMENT_VIEW_ALL = "hasAnyAuthority('AGREEMENT_VIEW_ALL', 'DRAFT_VIEW_ALL')";
    public static final String AGREEMENT_CREATE = "hasAuthority('AGREEMENT_CREATE')";
    public static final String ASSET_UPLOAD = "hasAnyAuthority('AGREEMENT_CREATE', 'AGREEMENT_EDIT_ALL', 'AGREEMENT_EDIT_MY', 'DRAFT_EDIT_ALL', 'DRAFT_EDIT_MY')";

    // DRAFT EDIT (id = versionId)
    public static final String DRAFT_EDIT = "hasAuthority('DRAFT_EDIT_ALL') or (hasAuthority('DRAFT_EDIT_MY') and @agreementSecurity.isVersionOwner(#id, principal.id))";
    // DRAFT DELETE (id = agreementId vs versionId)
    public static final String DRAFT_DELETE_A = "hasAuthority('DRAFT_DELETE_ALL') or (hasAuthority('DRAFT_DELETE_MY') and @agreementSecurity.isAgreementOwner(#id, principal.id))";
    public static final String DRAFT_DELETE_V = "hasAuthority('DRAFT_DELETE_ALL') or (hasAuthority('DRAFT_DELETE_MY') and @agreementSecurity.isVersionOwner(#id, principal.id))";

    // AGREEMENT SUBMIT (id = agreementId vs versionId)
    public static final String AGREEMENT_SUBMIT_A = "hasAuthority('AGREEMENT_SUBMIT_ALL') or (hasAuthority('AGREEMENT_SUBMIT_MY') and @agreementSecurity.isAgreementOwner(#id, principal.id))";
    public static final String AGREEMENT_SUBMIT_V = "hasAuthority('AGREEMENT_SUBMIT_ALL') or (hasAuthority('AGREEMENT_SUBMIT_MY') and @agreementSecurity.isVersionOwner(#id, principal.id))";

    // RENEW / REVISE (id = versionId)
    public static final String AGREEMENT_RENEW = "hasAuthority('AGREEMENT_RENEW_ALL') or (hasAuthority('AGREEMENT_RENEW_MY') and @agreementSecurity.isVersionOwner(#id, principal.id))";
    public static final String AGREEMENT_REVISE = "hasAuthority('AGREEMENT_REVISE_ALL') or (hasAuthority('AGREEMENT_REVISE_MY') and @agreementSecurity.isVersionOwner(#id, principal.id))";

    // TERMINATE (id = versionId vs agreementVersionId)
    public static final String AGREEMENT_TERMINATE = "hasAuthority('AGREEMENT_TERMINATE_ALL') or (hasAuthority('AGREEMENT_TERMINATE_MY') and @agreementSecurity.isVersionOwner(#agreementVersionId, principal.id))";
    public static final String AGREEMENT_TERMINATE_V = "hasAuthority('AGREEMENT_TERMINATE_ALL') or (hasAuthority('AGREEMENT_TERMINATE_MY') and @agreementSecurity.isVersionOwner(#id, principal.id))";

    // IN PROGRESS (id = agreementId)
    public static final String AGREEMENT_IN_PROGRESS = "hasAuthority('AGREEMENT_IN_PROGRESS_ALL') or (hasAuthority('AGREEMENT_IN_PROGRESS_MY') and @agreementSecurity.isAgreementOwner(#id, principal.id))";

    // Generic AGREEMENT_EDIT used in stateless parsing and sub-controllers
    public static final String AGREEMENT_EDIT_VERSION_ID = "hasAuthority('AGREEMENT_EDIT_ALL') or (hasAuthority('AGREEMENT_EDIT_MY') and @agreementSecurity.isVersionOwner(#versionId, principal.id))";
    public static final String AGREEMENT_EDIT_AGREEMENT_VERSION_ID = "hasAuthority('AGREEMENT_EDIT_ALL') or (hasAuthority('AGREEMENT_EDIT_MY') and @agreementSecurity.isVersionOwner(#agreementVersionId, principal.id))";
    public static final String AGREEMENT_EDIT_SOURCE_VERSION_ID = "hasAuthority('AGREEMENT_EDIT_ALL') or (hasAuthority('AGREEMENT_EDIT_MY') and @agreementSecurity.isVersionOwner(#sourceVersionId, principal.id))";
    public static final String AGREEMENT_EDIT_AGREEMENT_ID = "hasAuthority('AGREEMENT_EDIT_ALL') or (hasAuthority('AGREEMENT_EDIT_MY') and @agreementSecurity.isAgreementOwner(#id, principal.id))";
    public static final String AGREEMENT_EDIT_ID = "hasAuthority('AGREEMENT_EDIT_ALL') or (hasAuthority('AGREEMENT_EDIT_MY') and @agreementSecurity.isVersionOwner(#id, principal.id))";

    public static final String AGREEMENT_APPROVE = "hasAuthority('AGREEMENT_APPROVE')";
    public static final String AGREEMENT_REJECT = "hasAuthority('AGREEMENT_REJECT')";
    public static final String AGREEMENT_CLONE = "hasAuthority('AGREEMENT_CLONE')";
    public static final String AGREEMENT_TRANSFER = "hasAuthority('AGREEMENT_TRANSFER')";
    public static final String MASTER_VIEW = "hasAnyAuthority('MASTER_VIEW', 'MASTER_MANAGE')";
    public static final String MASTER_MANAGE = "hasAuthority('MASTER_MANAGE')";
    public static final String ADMIN_USERS = "hasAuthority('ADMIN_USERS')";
    public static final String PRICE_OFF_VIEW = "hasAnyAuthority('PRICE_OFF_VIEW', 'PRICE_OFF_MANAGE', 'PRICE_OFF_APPROVE')";
    public static final String PRICE_OFF_MANAGE = "hasAuthority('PRICE_OFF_MANAGE')";
    public static final String PRICE_OFF_APPROVE = "hasAuthority('PRICE_OFF_APPROVE')";
    public static final String COMMERCIAL_PAYOUT_CALCULATE = "hasAuthority('COMMERCIAL_PAYOUT_CALCULATE')";
    public static final String MASTER_OR_AGREEMENT_READ = "hasAnyAuthority('MASTER_VIEW', 'MASTER_MANAGE', 'AGREEMENT_VIEW', 'AGREEMENT_CREATE')";
}
