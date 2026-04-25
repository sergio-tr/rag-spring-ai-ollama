package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.application.port.PendingClarificationLoad;
import com.uniovi.rag.application.port.PendingClarificationStore;
import com.uniovi.rag.domain.runtime.clarification.PendingClarificationState;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class JpaPendingClarificationStore implements PendingClarificationStore {

    private final ConversationRepository conversationRepository;

    public JpaPendingClarificationStore(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PendingClarificationLoad load(UUID conversationId) {
        if (conversationId == null) {
            return PendingClarificationLoad.empty();
        }
        ConversationEntity c =
                conversationRepository.findById(conversationId).orElse(null);
        if (c == null) {
            return PendingClarificationLoad.empty();
        }
        Map<String, Object> json = c.getPendingClarification();
        if (json == null || json.isEmpty()) {
            return PendingClarificationLoad.empty();
        }
        PendingClarificationState parsed = PendingClarificationJsonMapper.fromMap(json);
        if (parsed == null) {
            return PendingClarificationLoad.invalid();
        }
        return PendingClarificationLoad.ok(parsed);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveReplace(UUID conversationId, PendingClarificationState state) {
        ConversationEntity c =
                conversationRepository.findById(conversationId).orElseThrow();
        c.setPendingClarification(PendingClarificationJsonMapper.toMap(state));
        c.touchUpdated();
        conversationRepository.save(c);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clear(UUID conversationId) {
        if (conversationId == null) {
            return;
        }
        ConversationEntity c = conversationRepository.findById(conversationId).orElse(null);
        if (c == null) {
            return;
        }
        c.setPendingClarification(null);
        c.touchUpdated();
        conversationRepository.save(c);
    }
}
