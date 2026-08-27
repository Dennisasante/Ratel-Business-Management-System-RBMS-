package com.ratel.rbms.service;

import com.ratel.rbms.entity.enums.AiChannel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Outbound delivery abstraction (spec §16): accepts one normalized
 * {@link OutgoingAiMessage} and routes it to whichever {@link AiChannelAdapter}
 * owns that message's channel. AiChatService/AiChannelRouter never call an
 * adapter's sendOutbound(...) directly — they go through here, so adding a
 * real WhatsApp/Instagram/Facebook sender later means registering one more
 * adapter bean, not touching any AI-core or routing code.
 *
 * No external sending implementation exists yet — WEB_DEMO's own adapter
 * delivers via a no-op (its answer travels back as the synchronous HTTP
 * response body instead), and that is the only adapter registered today.
 */
@Service
public class AiChannelDeliveryService {

    private final Map<AiChannel, AiChannelAdapter> adaptersByChannel;

    public AiChannelDeliveryService(List<AiChannelAdapter> adapters) {
        this.adaptersByChannel = adapters.stream()
                .collect(java.util.stream.Collectors.toMap(AiChannelAdapter::channel, a -> a));
    }

    public void deliver(OutgoingAiMessage message) {
        AiChannelAdapter adapter = adaptersByChannel.get(message.channel());
        if (adapter == null) {
            throw new IllegalStateException("No channel adapter registered for " + message.channel());
        }
        adapter.sendOutbound(message);
    }
}
