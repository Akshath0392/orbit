package com.orbit.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Comma-separated list of email domains considered "internal"
 * (orbit.internal-email-domains / INTERNAL_EMAIL_DOMAINS).
 *
 * Empty list = no restriction: every domain is allowed and treated as internal.
 */
@Component
public class InternalEmailDomains {

    private final List<String> domains;

    public InternalEmailDomains(@Value("${orbit.internal-email-domains:}") String csv) {
        this.domains = Arrays.stream(csv == null ? new String[0] : csv.split(","))
            .map(d -> d.trim().toLowerCase(Locale.ROOT))
            .filter(d -> !d.isEmpty())
            .toList();
    }

    public List<String> domains() {
        return domains;
    }

    /** True when the email's domain is configured as internal; always true when unconfigured. */
    public boolean isInternal(String email) {
        if (domains.isEmpty()) return true;
        if (email == null) return false;
        int at = email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) return false;
        return domains.contains(email.substring(at + 1).toLowerCase(Locale.ROOT));
    }

    /** Example address for help text, derived from the first configured domain. */
    public String exampleEmail() {
        return domains.isEmpty() ? "you@example.com" : "your.name@" + domains.get(0);
    }
}
