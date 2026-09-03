package com.orbit.service.agent;

import com.orbit.service.ai.AiGateway;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class StandupAgent {

    private final AiGateway ai;
    private final SimpMessagingTemplate ws;
    private final AtomicInteger countdown = new AtomicInteger(0);

    public StandupAgent(AiGateway ai, SimpMessagingTemplate ws) {
        this.ai = ai; this.ws = ws;
    }

    @Scheduled(cron = "${orbit.agents.standup.cron:0 0 8 * * MON-FRI}")
    public void triggerStandup() {
        countdown.set(1800);
        ws.convertAndSend("/topic/standup/all",
            Map.of("type", "standup_countdown", "projectId", "all", "secondsRemaining", 1800));
    }

    @Scheduled(fixedDelay = 60000)
    public void broadcastCountdown() {
        int remaining = countdown.get();
        if (remaining > 0) {
            remaining = Math.max(0, remaining - 60);
            countdown.set(remaining);
            ws.convertAndSend("/topic/standup/all",
                Map.of("type","standup_countdown","projectId","all","secondsRemaining", remaining));
        }
    }
}
