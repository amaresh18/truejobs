package com.aitrujobs.service;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@Slf4j
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${app.oauth2.authorizedRedirectUris:http://localhost:3000/auth/oauth2/redirect}")
    private String[] authorizedRedirectUris;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                       AuthenticationException exception) throws IOException, ServletException {
        String targetUrl = request.getParameter("redirect_uri");

        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            targetUrl = getDefaultFailureUrl();
        }

        log.error("OAuth2 authentication failed", exception);

        targetUrl = UriComponentsBuilder.fromUriString(targetUrl)
                .queryParam("error", exception.getLocalizedMessage())
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private String getDefaultFailureUrl() {
        return "http://localhost:3000/auth?error=oauth_error";
    }
}
