package com.aitrujobs.service;

import com.aitrujobs.entity.User;
import com.aitrujobs.entity.User.Role;
import com.aitrujobs.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);
        
        try {
            return processOAuth2User(userRequest, oauth2User);
        } catch (Exception ex) {
            log.error("Error processing OAuth2 user", ex);
            throw new OAuth2AuthenticationException("Error processing OAuth2 user: " + ex.getMessage());
        }
    }

    private OAuth2User processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oauth2User) {
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(registrationId, oauth2User.getAttributes());
        
        if (userInfo.getEmail() == null || userInfo.getEmail().trim().isEmpty()) {
            throw new OAuth2AuthenticationException("Email not found from OAuth2 provider");
        }

        Optional<User> userOptional = userRepository.findByEmail(userInfo.getEmail());
        User user;
        
        if (userOptional.isPresent()) {
            user = userOptional.get();
            if (!user.getProvider().equals(registrationId)) {
                throw new OAuth2AuthenticationException("User already exists with email: " + userInfo.getEmail() + 
                    " but with different provider: " + user.getProvider());
            }
            user = updateExistingUser(user, userInfo);
        } else {
            user = registerNewUser(userRequest, userInfo);
        }

        return UserPrincipal.create(user, oauth2User.getAttributes());
    }

    private User registerNewUser(OAuth2UserRequest oAuth2UserRequest, OAuth2UserInfo oAuth2UserInfo) {
        User user = new User();
        user.setName(oAuth2UserInfo.getName());
        user.setEmail(oAuth2UserInfo.getEmail());
        user.setImageUrl(oAuth2UserInfo.getImageUrl());
        user.setProvider(oAuth2UserRequest.getClientRegistration().getRegistrationId());
        user.setProviderId(oAuth2UserInfo.getId());
        user.setRole(Role.CANDIDATE); // Default role for OAuth users
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        
        return userRepository.save(user);
    }

    private User updateExistingUser(User existingUser, OAuth2UserInfo oAuth2UserInfo) {
        existingUser.setName(oAuth2UserInfo.getName());
        existingUser.setImageUrl(oAuth2UserInfo.getImageUrl());
        existingUser.setUpdatedAt(LocalDateTime.now());
        
        return userRepository.save(existingUser);
    }
}

// OAuth2 User Info interface
interface OAuth2UserInfo {
    String getId();
    String getName();
    String getEmail();
    String getImageUrl();
}

// Factory for different OAuth2 providers
class OAuth2UserInfoFactory {
    public static OAuth2UserInfo getOAuth2UserInfo(String registrationId, Map<String, Object> attributes) {
        switch (registrationId.toLowerCase()) {
            case "google":
                return new GoogleOAuth2UserInfo(attributes);
            case "linkedin":
                return new LinkedInOAuth2UserInfo(attributes);
            case "github":
                return new GitHubOAuth2UserInfo(attributes);
            case "microsoft":
                return new MicrosoftOAuth2UserInfo(attributes);
            default:
                throw new OAuth2AuthenticationException("Login with " + registrationId + " is not supported");
        }
    }
}

// Google OAuth2 User Info
class GoogleOAuth2UserInfo implements OAuth2UserInfo {
    private final Map<String, Object> attributes;

    public GoogleOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String getId() {
        return (String) attributes.get("sub");
    }

    @Override
    public String getName() {
        return (String) attributes.get("name");
    }

    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }

    @Override
    public String getImageUrl() {
        return (String) attributes.get("picture");
    }
}

// LinkedIn OAuth2 User Info
class LinkedInOAuth2UserInfo implements OAuth2UserInfo {
    private final Map<String, Object> attributes;

    public LinkedInOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String getId() {
        return (String) attributes.get("id");
    }

    @Override
    public String getName() {
        Map<String, Object> localizedFirstName = (Map<String, Object>) attributes.get("localizedFirstName");
        Map<String, Object> localizedLastName = (Map<String, Object>) attributes.get("localizedLastName");
        
        if (localizedFirstName != null && localizedLastName != null) {
            return localizedFirstName.toString() + " " + localizedLastName.toString();
        }
        return (String) attributes.get("formattedName");
    }

    @Override
    public String getEmail() {
        return (String) attributes.get("emailAddress");
    }

    @Override
    public String getImageUrl() {
        Map<String, Object> profilePicture = (Map<String, Object>) attributes.get("profilePicture");
        if (profilePicture != null) {
            Map<String, Object> displayImage = (Map<String, Object>) profilePicture.get("displayImage~");
            if (displayImage != null) {
                // LinkedIn profile picture logic
                return (String) displayImage.get("elements");
            }
        }
        return null;
    }
}

// GitHub OAuth2 User Info
class GitHubOAuth2UserInfo implements OAuth2UserInfo {
    private final Map<String, Object> attributes;

    public GitHubOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String getId() {
        return String.valueOf(attributes.get("id"));
    }

    @Override
    public String getName() {
        return (String) attributes.get("name");
    }

    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }

    @Override
    public String getImageUrl() {
        return (String) attributes.get("avatar_url");
    }
}

// Microsoft OAuth2 User Info
class MicrosoftOAuth2UserInfo implements OAuth2UserInfo {
    private final Map<String, Object> attributes;

    public MicrosoftOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String getId() {
        return (String) attributes.get("id");
    }

    @Override
    public String getName() {
        return (String) attributes.get("displayName");
    }

    @Override
    public String getEmail() {
        return (String) attributes.get("mail");
    }

    @Override
    public String getImageUrl() {
        // Microsoft Graph API doesn't provide profile picture URL directly
        return null;
    }
}
