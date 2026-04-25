package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.RagConfigurationLevel;
import com.uniovi.rag.domain.UserRole;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JpaEntityFactoryTest {

    @Test
    void knowledgeDocumentEntityFactory_sharedAndChatLocalBranches() {
        ProjectEntity p = new ProjectEntity();
        ConversationEntity c = new ConversationEntity();
        KnowledgeDocumentEntity shared = KnowledgeDocumentEntityFactory.newIngesting(p, "a.pdf");
        assertSame(p, shared.getProject());
        assertNull(shared.getConversation());
        assertEquals(CorpusScope.PROJECT_SHARED, shared.getCorpusScope());
        assertEquals("a.pdf", shared.getFileName());
        assertEquals(ProjectDocumentStatus.INGESTING, shared.getStatus());
        assertNotNull(shared.getUploadedAt());

        KnowledgeDocumentEntity local = KnowledgeDocumentEntityFactory.newChatLocalIngesting(p, c, "b.pdf");
        assertSame(p, local.getProject());
        assertSame(c, local.getConversation());
        assertEquals(CorpusScope.CHAT_LOCAL, local.getCorpusScope());
        assertEquals("b.pdf", local.getFileName());
        assertEquals(ProjectDocumentStatus.INGESTING, local.getStatus());
    }

    @Test
    void userEntityFactory_registeredUserAndCreatedAtBranch() {
        UserEntity u = UserEntityFactory.newRegisteredUser("a@b.c", "N", "hash");
        assertEquals("a@b.c", u.getEmail());
        assertEquals("N", u.getName());
        assertEquals("hash", u.getPasswordHash());
        assertEquals(UserRole.USER, u.getRole());
        assertNotNull(u.getCreatedAt());

        Instant fixed = Instant.parse("2025-06-01T00:00:00Z");
        UserEntity withFixed = UserEntityFactory.newUser("x@y.z", "A", "h", UserRole.ADMIN, fixed);
        assertEquals(fixed, withFixed.getCreatedAt());

        UserEntity withNow = UserEntityFactory.newUser("u@v.w", "B", "h2", UserRole.USER, null);
        assertNotNull(withNow.getCreatedAt());
    }

    @Test
    void ragConfigurationEntityFactory_userDefaultAndProjectScoped() {
        UserEntity user = new UserEntity();
        ProjectEntity project = new ProjectEntity();
        Instant now = Instant.parse("2026-03-01T12:00:00Z");
        Map<String, Object> values = Map.of("k", true);

        RagConfigurationEntity userDef =
                RagConfigurationEntityFactory.newUserDefault(user, values, now);
        assertSame(user, userDef.getUser());
        assertNull(userDef.getProject());
        assertEquals(RagConfigurationLevel.USER_DEFAULT, userDef.getLevel());
        assertEquals("user-default", userDef.getName());
        assertEquals(values, userDef.getValues());
        assertTrue(userDef.isActive());
        assertEquals(now, userDef.getCreatedAt());
        assertEquals(now, userDef.getUpdatedAt());

        RagConfigurationEntity proj =
                RagConfigurationEntityFactory.newProjectScoped(user, project, values, now);
        assertSame(project, proj.getProject());
        assertEquals(RagConfigurationLevel.PROJECT, proj.getLevel());
        assertEquals("project", proj.getName());
    }
}
