package com.medplus.agreement_tracker_backend.util;

import com.medplus.agreement_tracker_backend.enums.PayoutFrequency;

public final class JbpExcelSheetLayout {

    public static final String[] VALUE_TYPE_OPTIONS = {"ABSOLUTE", "RELATIVE"};
    public static final String[] ABSOLUTE_ONLY_OPTIONS = {"ABSOLUTE"};
    public static final String[] OPTIONAL_VALUE_TYPE_OPTIONS = {"", "ABSOLUTE", "RELATIVE"};

    private JbpExcelSheetLayout() {
    }

    public record Columns(
            int colEntityId,
            int colParentPeriod,
            int colSubPeriod,
            int colSlabTier,
            int colTargetType,   // -1 for master (column removed); valid index for spread
            int colTarget,
            int colQualifierPercent,
            int colPayoutType,
            int colPayout,
            int colMaxPurchase,
            int colMaxPayout,
            int colConfigId,
            boolean master,
            String[] headers) {

        public int editableStartCol() {
            // For master: Target Type is gone, first editable is Target.
            // For spread: first editable is Target Type.
            return master ? colTarget : colTargetType;
        }

        public int editableEndCol() {
            return colMaxPayout;
        }

        public int colPeriod() {
            return master ? colParentPeriod : colSubPeriod;
        }
    }

    public static Columns forSheet(boolean master) {
        return master ? masterThreshold() : spreadThreshold();
    }

    public static String canonicalMasterSheetName(String configId, PayoutFrequency frequency) {
        return "Config" + configId + "-Master_" + frequency.name();
    }

    public static String canonicalSpreadSheetName(String configId, PayoutFrequency frequency) {
        return "Config" + configId + "-Spread_" + frequency.name();
    }

    /**
     * Target-interval sheet naming convention.
     * Each selected target interval gets its own dedicated sheet (tab).
     */
    public static String canonicalTargetSheetName(String configId, PayoutFrequency frequency) {
        return "Config" + configId + "-Target_" + frequency.name();
    }

    /**
     * Master sheet layout — Target Type column is REMOVED (always ABSOLUTE, hardcoded by parser).
     * Column order: Entity ID(0), Period(1), Slab Tier(2), Target(3), Qualifier%(4),
     *               Payout Type(5), Payout(6), Max Purchase(7), Max Payout(8), Config ID(9)
     */
    private static Columns masterThreshold() {
        return new Columns(
                0, 1, -1, 2, -1, 3, 4, 5, 6, 7, 8, 9, true,
                new String[]{
                        "Entity ID", "Period", "Slab Tier", "Target", "Qualifier %",
                        "Payout Type", "Payout", "Max Purchase (Optional)", "Max Payout (Optional)", "Config ID"
                });
    }

    /**
     * Spread (sub-period) sheet layout — Target Type column is KEPT (can be ABSOLUTE or RELATIVE).
     * Column order: Entity ID(0), Parent Period(1), Sub Period(2), Slab Tier(3), Target Type(4),
     *               Target(5), Qualifier%(6), Payout Type(7), Payout(8), Max Purchase(9), Max Payout(10), Config ID(11)
     */
    private static Columns spreadThreshold() {
        return new Columns(
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, false,
                new String[]{
                        "Entity ID", "Parent Period", "Sub Period", "Slab Tier", "Target Type", "Target",
                        "Qualifier %", "Payout Type (Optional)", "Payout (Optional)", "Max Purchase (Optional)", "Max Payout (Optional)",
                        "Config ID"
                });
    }
}
