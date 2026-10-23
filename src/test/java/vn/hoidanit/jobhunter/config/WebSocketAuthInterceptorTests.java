package vn.hoidanit.jobhunter.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import vn.hoidanit.jobhunter.domain.*;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.service.AccessService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketAuthInterceptorTests {
    @Test
    void requiresConnectTokenAndRejectsOtherUsersTopicsAndClientPublishing() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        UserRepository users = mock(UserRepository.class);
        AccessService access = mock(AccessService.class);
        var interceptor = new WebSocketAuthInterceptor(decoder, users, access);
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(StompCommand.CONNECT, null, null), null));
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS512").subject("user@example.test").build();
        when(decoder.decode("token")).thenReturn(jwt);
        User user = new User(); user.setId(1L); Role role = new Role(); role.setActive(true); user.setRole(role);
        when(users.findByEmail("user@example.test")).thenReturn(user);
        assertDoesNotThrow(() -> interceptor.preSend(message(StompCommand.SUBSCRIBE, "/topic/notifications/1", jwt), null));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(StompCommand.SUBSCRIBE, "/topic/notifications/2", jwt), null));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(StompCommand.SUBSCRIBE, "/topic/resumes", jwt), null));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(StompCommand.SEND, "/topic/jobs", jwt), null));
    }

    private Message<byte[]> message(StompCommand command, String destination, Jwt jwt) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(command);
        if (destination != null) headers.setDestination(destination);
        if (jwt != null) headers.setUser(new JwtAuthenticationToken(jwt));
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
