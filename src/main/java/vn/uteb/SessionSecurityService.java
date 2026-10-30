package vn.uteb;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import static vn.uteb.Domain.Account;

/** Shared login snapshot for HTTP and WebSocket; the database remains authoritative. */
@Service
public class SessionSecurityService {
    static final String ACCOUNT_ID = "uteb.accountId";
    static final String TOKEN_VERSION = "uteb.tokenVersion";
    @PersistenceContext EntityManager em;
    private final Map<String, WebSocketSession> sockets = new ConcurrentHashMap<>();

    void remember(HttpSession session, Account account) {
        session.setAttribute(ACCOUNT_ID, account.id);
        session.setAttribute(TOKEN_VERSION, account.tokenVersion);
    }

    @Transactional(readOnly = true)
    public boolean valid(Object id, Object version) {
        if (!(id instanceof Long accountId) || !(version instanceof Integer tokenVersion)) return false;
        // A scalar query avoids accepting an Account cached in a persistence context.
        return em.createQuery("select count(a) from Account a where a.id=:id and a.tokenVersion=:v and a.active=true and a.verified=true", Long.class)
                .setParameter("id", accountId).setParameter("v", tokenVersion).getSingleResult() == 1;
    }

    @Transactional(readOnly = true)
    public boolean valid(Map<String, Object> attributes) {
        return attributes != null && valid(attributes.get(ACCOUNT_ID), attributes.get(TOKEN_VERSION));
    }

    Map<String, Object> snapshot(HttpSession session) {
        try {
            if (session == null) return Map.of();
            Object id = session.getAttribute(ACCOUNT_ID), version = session.getAttribute(TOKEN_VERSION);
            return id instanceof Long && version instanceof Integer ? Map.of(ACCOUNT_ID, id, TOKEN_VERSION, version) : Map.of();
        } catch (IllegalStateException ignored) { return Map.of(); } // Another request already invalidated it.
    }

    @Transactional(readOnly = true)
    public boolean valid(HttpSession session) { return valid(snapshot(session)); }

    void invalidate(HttpSession session) {
        try { session.invalidate(); } catch (IllegalStateException ignored) { /* Already invalidated concurrently. */ }
    }

    void register(WebSocketSession session) { sockets.put(session.getId(), session); }
    void unregister(WebSocketSession session) { sockets.remove(session.getId()); }

    void revokeAfterCommit(Account account) {
        // A not-yet-persisted account cannot own a session or a WebSocket.
        if (account.id == null) return;
        Long id = account.id;
        int currentVersion = account.tokenVersion;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                sockets.values().forEach(session -> {
                    Map<String, Object> attributes = session.getAttributes();
                    if (id.equals(attributes.get(ACCOUNT_ID))
                            && attributes.get(TOKEN_VERSION) instanceof Integer version && version < currentVersion) {
                        close(session);
                    }
                });
            }
        });
    }

    void close(WebSocketSession session) {
        try { session.close(CloseStatus.POLICY_VIOLATION); }
        catch (IOException | IllegalStateException ignored) { /* Outbound validation still prevents further private messages. */ }
        finally { unregister(session); }
    }
}
