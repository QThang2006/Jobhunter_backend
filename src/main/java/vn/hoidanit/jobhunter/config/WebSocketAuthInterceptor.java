package vn.hoidanit.jobhunter.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.service.AccessService;
import java.util.Set;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {
    private final JwtDecoder decoder;
    private final UserRepository users;
    private final AccessService access;

    public WebSocketAuthInterceptor(JwtDecoder decoder, UserRepository users, AccessService access) {
        this.decoder = decoder;
        this.users = users;
        this.access = access;
    }

    @Override
    @Transactional(readOnly = true)
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null || headers.getCommand() == null) return message;
        if (headers.getCommand() == StompCommand.CONNECT) {
            String bearer = headers.getFirstNativeHeader("Authorization");
            if (bearer == null || !bearer.startsWith("Bearer ")) throw new AccessDeniedException("Authentication required");
            Jwt jwt = decoder.decode(bearer.substring(7));
            requireUser(jwt);
            headers.setUser(new JwtAuthenticationToken(jwt));
        } else if (headers.getCommand() == StompCommand.SUBSCRIBE) {
            if (!(headers.getUser() instanceof JwtAuthenticationToken authentication)) throw new AccessDeniedException("Authentication required");
            Jwt jwt = decoder.decode(authentication.getToken().getTokenValue());
            User user = requireUser(jwt);
            String destination = headers.getDestination();
            // These shared topics carry refresh markers only, including role changes for the account UI.
            boolean allowed = Set.of("/topic/jobs", "/topic/companies", "/topic/skills", "/topic/roles", "/topic/permissions").contains(destination);
            for (String resource : Set.of("users", "resumes", "shifts", "notifications")) {
                allowed |= ("/topic/" + resource + "/" + user.getId()).equals(destination);
            }
            for (String resource : Set.of("users", "resumes", "shifts")) {
                allowed |= ("/topic/" + resource).equals(destination)
                        && access.hasPermission(user, "/api/v1/" + resource, "GET");
            }
            if (!allowed) throw new AccessDeniedException("Subscription not allowed");
        } else if (headers.getCommand() == StompCommand.SEND) {
            throw new AccessDeniedException("Clients cannot publish server notifications");
        }
        return message;
    }

    private User requireUser(Jwt jwt) {
        User user = users.findByEmail(jwt.getSubject());
        if (user == null || user.getRole() == null || !user.getRole().isActive()) throw new AccessDeniedException("Inactive account");
        return user;
    }
}
