package com.uniovi.rag.interfaces.rest.admin;

import com.uniovi.rag.interfaces.rest.admin.dto.AdminAllowlistEntryDto;
import com.uniovi.rag.interfaces.rest.admin.dto.CreateAllowlistEntryRequest;
import com.uniovi.rag.interfaces.rest.admin.dto.UpdateAllowlistEntryRequest;
import com.uniovi.rag.application.service.admin.AdminSystemDefaultsService;
import com.uniovi.rag.application.service.admin.AllowlistAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin maintenance: system defaults and allowlist CRUD (product API prefix).
 */
@RestController
@RequestMapping("${rag.api.product-base-path}/admin")
public class AdminController {

    private final AllowlistAdminService allowlistAdminService;
    private final AdminSystemDefaultsService adminSystemDefaultsService;

    public AdminController(
            AllowlistAdminService allowlistAdminService,
            AdminSystemDefaultsService adminSystemDefaultsService) {
        this.allowlistAdminService = allowlistAdminService;
        this.adminSystemDefaultsService = adminSystemDefaultsService;
    }

    @GetMapping("/system-defaults")
    public Map<String, Object> getSystemDefaults() {
        return adminSystemDefaultsService.getDefaults();
    }

    @PutMapping("/system-defaults")
    public Map<String, Object> putSystemDefaults(@RequestBody Map<String, Object> body) {
        return adminSystemDefaultsService.putDefaults(body);
    }

    @GetMapping("/allowlist")
    public List<AdminAllowlistEntryDto> listAllowlist() {
        return allowlistAdminService.list();
    }

    @PostMapping("/allowlist")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminAllowlistEntryDto createAllowlist(@Valid @RequestBody CreateAllowlistEntryRequest body) {
        return allowlistAdminService.create(body);
    }

    @PutMapping("/allowlist/{id}")
    public AdminAllowlistEntryDto updateAllowlist(
            @PathVariable UUID id, @Valid @RequestBody UpdateAllowlistEntryRequest body) {
        return allowlistAdminService.update(id, body);
    }

    @DeleteMapping("/allowlist/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAllowlist(@PathVariable UUID id) {
        allowlistAdminService.delete(id);
    }
}
