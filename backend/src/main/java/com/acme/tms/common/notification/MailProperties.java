package com.acme.tms.common.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled false sends nothing and logs instead, which is what a developer without an SMTP
 *     server wants and what CI needs
 * @param baseUrl where links in emails point; must include the SPA's basePath, since the frontend
 *     is served under /TournamentManagementSystem
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(boolean enabled, String from, String baseUrl) {

    /** Joins without doubling or dropping the separator, whatever the configured value ends with. */
    public String link(String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + (path.startsWith("/") ? path : "/" + path);
    }
}
