package com.orbit.service.slack;

import com.orbit.config.InternalEmailDomains;
import com.orbit.domain.slack.SlackMagicLink;
import com.orbit.integration.slack.SlackService;
import com.orbit.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Handles /orbit-link <email>. Issues a magic-link token and DMs the user
 * a confirmation URL. Clicking the URL hits POST /api/v1/slack/link/confirm
 * which persists app_users.slack_user_id.
 */
@Service
public class SlackLinkCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(SlackLinkCommandHandler.class);
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final MagicLinkService magicLinks;
    private final AppUserRepository users;
    private final SlackService slack;
    private final InternalEmailDomains internalDomains;
    private final String frontendUrl;

    public SlackLinkCommandHandler(MagicLinkService magicLinks,
                                   AppUserRepository users,
                                   SlackService slack,
                                   InternalEmailDomains internalDomains,
                                   @Value("${orbit.frontend.url:http://localhost:3000}") String frontendUrl) {
        this.magicLinks = magicLinks;
        this.users = users;
        this.slack = slack;
        this.internalDomains = internalDomains;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    public void handle(String slackUserId, String rawText) {
        String email = rawText == null ? "" : rawText.trim().toLowerCase();
        if (!EMAIL.matcher(email).matches()) {
            slack.sendDm(slackUserId, "Usage: `/orbit-link " + internalDomains.exampleEmail() + "`");
            return;
        }
        if (!internalDomains.isInternal(email)) {
            slack.sendDm(slackUserId, "Only company email addresses can be linked (e.g. `"
                + internalDomains.exampleEmail() + "`).");
            log.warn("/orbit-link rejected non-internal domain: slackUserId={} email={}", slackUserId, email);
            return;
        }
        if (users.findByEmail(email).isEmpty()) {
            // Intentionally vague — don't leak which emails exist in the system.
            slack.sendDm(slackUserId, "If `" + email + "` is an Orbit account, a confirmation link has been sent.");
            log.warn("/orbit-link requested for unknown email: slackUserId={} email={}", slackUserId, email);
            return;
        }
        SlackMagicLink link = magicLinks.issue(slackUserId, email);
        String url = frontendUrl + "/slack/link?token=" + link.getToken();
        String msg = "Click to link your Slack to Orbit (`" + email + "`): " + url + "\nLink expires in 15 minutes.";
        slack.sendDm(slackUserId, msg);
        log.info("/orbit-link issued: slackUserId={} email={} expiresAt={}", slackUserId, email, link.getExpiresAt());
    }
}
