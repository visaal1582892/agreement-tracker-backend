package com.medplus.agreement_tracker_backend.enums;

public enum AdHocSubType {
    QPS,
    /** @deprecated Legacy value — new agreements always use QPS */
    @Deprecated
    CONSUMER_PRICE_OFF
}
