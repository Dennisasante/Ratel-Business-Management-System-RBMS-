package com.ratel.rbms.dto;

public record SmsCampaignPreviewResponse(
        int recipients,
        int skippedNoPhone,
        int skippedOptedOut,
        int skippedDuplicate,
        int creditsNeeded,
        int creditsAvailable,
        boolean enoughCredits,
        // Credits per text range from min to max because a longer first name can tip a text into a second segment.
        int minCreditsPerText,
        int maxCreditsPerText,
        // The message exactly as one real customer would get it.
        String sampleMessage
) {
}
