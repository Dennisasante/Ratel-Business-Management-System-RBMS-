package com.ratel.rbms.service;

import com.ratel.rbms.entity.enums.AiChannel;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * The WEB_DEMO channel adapter — the dashboard's own Test AI panel. Unlike
 * every future external adapter, WEB_DEMO's "inbound payload" is just the
 * customer/tester's plain message text from an already-authenticated
 * request (see AiChatController); there is no webhook signature to verify
 * and no external account id to resolve, since AiChannelRouter.routeWebDemo
 * already trusts TenantContext for the business.
 *
 * sendOutbound is intentionally a no-op: WEB_DEMO's answer travels back as
 * the synchronous AiChatResponse HTTP body, never a separate push-style
 * delivery. This class exists purely so WEB_DEMO is shaped like every other
 * channel adapter, not because it needs one to function.
 */
@Component
public class WebDemoChannelAdapter implements AiChannelAdapter {

    @Override
    public AiChannel channel() {
        return AiChannel.WEB_DEMO;
    }

    @Override
    public boolean validateInbound(Object rawPayload) {
        return rawPayload instanceof String text && !text.isBlank();
    }

    @Override
    public IncomingAiMessage normalizeInbound(Object rawPayload) {
        String text = (String) rawPayload;
        return new IncomingAiMessage(AiChannel.WEB_DEMO, null, null, null, null, text, Instant.now(), null);
    }

    @Override
    public void sendOutbound(OutgoingAiMessage message) {
        // No-op — see class-level doc comment.
    }
}
