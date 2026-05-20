package com.uniovi.rag.application.service.account;

import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Irreversible removal of the user row; DB CASCADE cleans dependent rows.
 */
@Service
public class AccountDeletionApplicationService {

    private final UserRepository userRepository;

    public AccountDeletionApplicationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void runDeletion(AsyncTaskEntity task, AsyncTaskMutationService mutation) {
        UUID taskId = task.getId();
        UUID userId = task.getUser().getId();
        mutation.markSucceeded(
                taskId,
                Map.of(AccountJobPayloadKeys.TASK_TYPE, "ACCOUNT_DELETION", "deleted", Boolean.TRUE));
        userRepository.deleteById(userId);
    }
}
