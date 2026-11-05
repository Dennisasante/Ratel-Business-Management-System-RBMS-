package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record SmsCampaignRequest(
        @Size(max = 80, message = "Keep the campaign name under 80 characters")
        String name,

        // {name} is replaced with each customer's first name.
        @NotBlank(message = "Write the message first")
        @Size(max = 600, message = "That message is too long")
        String message,

        @NotBlank(message = "Choose who to send it to")
        @Pattern(regexp = "^(ALL|SELECTED)$", message = "Audience must be ALL or SELECTED")
        String audience,

        // Only for SELECTED.
        @Size(max = 5000, message = "That's too many customers for one campaign")
        List<UUID> customerIds,

        // Defaults to true: "Reply STOP to opt out." added to the end of every text.
        Boolean includeOptOutFooter,

        // Chosen by the browser when the confirm screen opens. Sending the same key twice makes one campaign.
        UUID requestKey
) {
}
