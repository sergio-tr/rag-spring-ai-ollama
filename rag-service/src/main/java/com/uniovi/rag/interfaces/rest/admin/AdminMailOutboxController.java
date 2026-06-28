package com.uniovi.rag.interfaces.rest.admin;

import com.uniovi.rag.infrastructure.persistence.MailOutboxRepository;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminMailOutboxEntryDto;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only mail outbox inspection for dev/e2e environments.
 *
 * <p>Never enable this controller in production: it returns email bodies which may contain one-time tokens.
 */
@RestController
@Profile({ "dev", "e2e" })
@RequestMapping("${rag.api.product-base-path}/admin/mail-outbox")
public class AdminMailOutboxController {

    private final MailOutboxRepository mailOutboxRepository;

    public AdminMailOutboxController(MailOutboxRepository mailOutboxRepository) {
        this.mailOutboxRepository = mailOutboxRepository;
    }

    @GetMapping
    public List<AdminMailOutboxEntryDto> list(@RequestParam(defaultValue = "10") int limit) {
        int n = Math.max(1, Math.min(50, limit));
        return mailOutboxRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .limit(n)
                .map(AdminMailOutboxEntryDto::fromEntity)
                .toList();
    }
}

