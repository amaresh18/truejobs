package com.aitrujobs.service;

import com.aitrujobs.config.JwtUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtils jwtUtils;

    @Value("${app.oauth2.authorizedRedirectUris:http://localhost:3000/auth/oauth2/redirect}")
    private String[] authorizedRedirectUris;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                       Authentication authentication) throws IOException, ServletException {
        String targetUrl = determineTargetUrl(request, response, authentication);

        if (response.isCommitted()) {
            log.debug("Response has already been committed. Unable to redirect to " + targetUrl);
            return;
        }

        clearAuthenticationAttributes(request, response);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response,
                                      Authentication authentication) {
        String targetUrl = request.getParameter("redirect_uri");

        if (targetUrl != null && !isAuthorizedRedirectUri(targetUrl)) {
            throw new RuntimeException("Unauthorized Redirect URI and can't proceed with the authentication");
        }

        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            targetUrl = getDefaultTargetUrl();
        }

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        // Extract role from authorities
        String role = userPrincipal.getAuthorities().stream()
            .findFirst()
            .map(authority -> authority.getAuthority().replace("ROLE_", ""))
            .orElse("CANDIDATE");
            
        String token = jwtUtils.generateJwtToken(userPrincipal.getEmail(), userPrincipal.getId(), role);

        return UriComponentsBuilder.fromUriString(targetUrl)
                .queryParam("token", token)
                .build().toUriString();
    }

    protected void clearAuthenticationAttributes(HttpServletRequest request, HttpServletResponse response) {
        super.clearAuthenticationAttributes(request);
        // Clear any OAuth2 related cookies if needed
    }

    private boolean isAuthorizedRedirectUri(String uri) {
        URI clientRedirectUri = URI.create(uri);

        for (String authorizedUri : authorizedRedirectUris) {
            URI authorizedURI = URI.create(authorizedUri);
            if (authorizedURI.getHost().equalsIgnoreCase(clientRedirectUri.getHost())
                    && authorizedURI.getPort() == clientRedirectUri.getPort()) {
                return true;
            }
        }
        return false;
    }
}
