package com.umar.ecommerce.inventory.controller;

import com.umar.ecommerce.inventory.config.SecurityConfig;
import com.umar.ecommerce.inventory.dto.response.PageResponse;
import com.umar.ecommerce.inventory.service.StockCommandService;
import com.umar.ecommerce.inventory.service.StockQueryService;
import com.umar.ecommerce.inventory.web.SecurityProblemWriter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminInventoryController.class)
@Import({SecurityConfig.class, SecurityProblemWriter.class})
class InventoryPermissionTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    StockQueryService queries;

    @MockitoBean
    StockCommandService commands;

    @Test
    void readerCanListAndCannotAdjust() throws Exception {
        when(queries.list(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true, true, "sku,asc"));
        mvc.perform(get("/api/v1/admin/inventory/stock-items")
                        .with(jwt().authorities(new SimpleGrantedAuthority("PERM_inventory.read"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/inventory/stock-items")
                        .with(jwt().authorities(new SimpleGrantedAuthority("PERM_inventory.read")))
                        .header("Idempotency-Key", "key-1")
                        .contentType("application/json")
                        .content("{\"catalogVariantId\":\"00000000-0000-0000-0000-000000000010\",\"initialOnHand\":1,\"reasonCode\":\"OPENING_BALANCE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVENTORY_ACCESS_DENIED"));
        verifyNoInteractions(commands);
    }

    @Test
    void adjusterCannotReadWithoutInventoryRead() throws Exception {
        mvc.perform(get("/api/v1/admin/inventory/stock-items")
                        .with(jwt().authorities(new SimpleGrantedAuthority("PERM_inventory.adjust"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(queries);
    }

    @Test
    void catalogReadDoesNotGrantInventory() throws Exception {
        mvc.perform(get("/api/v1/admin/inventory/stock-items")
                        .with(jwt().authorities(new SimpleGrantedAuthority("PERM_catalog.read"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(queries);
    }

    @Test
    void forgedIdentityHeadersDoNotAuthenticate() throws Exception {
        mvc.perform(post("/api/v1/admin/inventory/stock-items")
                        .header("X-User-Id", "admin")
                        .header("X-Roles", "INVENTORY_MANAGER")
                        .header("Idempotency-Key", "key-1")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVENTORY_AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(commands);
    }

    @Test
    void unknownMethodsAreDenied() throws Exception {
        mvc.perform(put("/api/v1/admin/inventory/stock-items/00000000-0000-0000-0000-000000000010")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("PERM_inventory.read"),
                                new SimpleGrantedAuthority("PERM_inventory.adjust")
                        ))
                        .contentType("application/json")
                        .content("{\"onHand\":5}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(commands);
        verifyNoInteractions(queries);
    }
}
