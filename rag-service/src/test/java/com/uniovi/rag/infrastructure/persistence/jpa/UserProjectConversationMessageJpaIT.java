package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.Application;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.MessageFeedbackRepository;
import com.uniovi.rag.infrastructure.persistence.MessageRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.infrastructure.persistence.UserPersonalizationRepository;
import com.uniovi.rag.infrastructure.persistence.UserPreferencesRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.testsupport.TestAiStubConfiguration;
import com.uniovi.rag.testsupport.TestcontainersDatasourceConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "rag.jwt.secret=test-secret-key-for-jwt-signing-must-be-long-enough-32",
            "management.otlp.tracing.endpoint=http://127.0.0.1:4318/v1/traces",
            "management.otlp.metrics.export.url=http://127.0.0.1:4318/v1/metrics"
        })
@Import({TestAiStubConfiguration.class, TestcontainersDatasourceConfiguration.class})
@ActiveProfiles("test")
@EnabledIf(
        value = "com.uniovi.rag.testsupport.TestEnvironment#isSpringBootPostgresAvailable",
        disabledReason = "Postgres/Testcontainers not available")
class UserProjectConversationMessageJpaIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserPreferencesRepository userPreferencesRepository;

    @Autowired
    private UserPersonalizationRepository userPersonalizationRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private MessageFeedbackRepository messageFeedbackRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void persistUserGraph_preferencesPersonalizationProjectConversationMessageAndFeedback() {
        Instant now = Instant.parse("2026-05-01T12:00:00Z");
        UserEntity user =
                userRepository.save(
                        UserEntityFactory.newRegisteredUser(
                                "jpa-msg-" + UUID.randomUUID() + "@test.local", "IT User", "ph"));

        UserPreferencesEntity prefs = UserPreferencesEntity.newForUser(user);
        prefs.setPreferences(Map.of("theme", "dark"));
        prefs.setUpdatedAt(now);
        userPreferencesRepository.save(prefs);

        UserPersonalizationEntity pers = UserPersonalizationEntity.newForUser(user);
        pers.setPersonalization(Map.of("tone", "formal"));
        pers.setUpdatedAt(now);
        userPersonalizationRepository.save(pers);

        ProjectEntity project = new ProjectEntity();
        project.setOwner(user);
        project.setName("it-project");
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        project = projectRepository.save(project);

        ConversationEntity conv =
                conversationRepository.save(
                        ConversationEntity.create(user, project, "it-conv", List.of("d1")));

        MessageEntity msg =
                messageRepository.save(MessageEntity.userMessage(conv, "hello", 1));

        MessageFeedbackEntity fb =
                messageFeedbackRepository.save(
                        MessageFeedbackEntity.create(msg, user, 4, "helpful", now));

        messageFeedbackRepository.flush();
        entityManager.clear();

        MessageFeedbackEntity loaded = messageFeedbackRepository.findById(fb.getId()).orElseThrow();
        assertThat(loaded.getRating()).isEqualTo(4);
        assertThat(loaded.getComment()).isEqualTo("helpful");
        assertThat(loaded.getCreatedAt()).isEqualTo(now);

        UserPreferencesEntity pLoaded =
                userPreferencesRepository.findById(user.getId()).orElseThrow();
        assertThat(pLoaded.getPreferences()).containsEntry("theme", "dark");

        UserPersonalizationEntity zLoaded =
                userPersonalizationRepository.findById(user.getId()).orElseThrow();
        assertThat(zLoaded.getPersonalization()).containsEntry("tone", "formal");

        MessageEntity mLoaded = messageRepository.findById(msg.getId()).orElseThrow();
        assertThat(mLoaded.getContent()).isEqualTo("hello");
        assertThat(mLoaded.getSeq()).isEqualTo(1);
    }
}
