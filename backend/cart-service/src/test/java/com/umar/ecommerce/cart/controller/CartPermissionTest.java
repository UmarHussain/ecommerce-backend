package com.umar.ecommerce.cart.controller;

import com.umar.ecommerce.cart.config.SecurityConfig;
import com.umar.ecommerce.cart.service.CartCommandService;
import com.umar.ecommerce.cart.service.Owner;
import com.umar.ecommerce.cart.web.ApiExceptionHandler;
import com.umar.ecommerce.cart.web.CorrelationIdFilter;
import com.umar.ecommerce.cart.web.SecurityProblemWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
@Import({SecurityConfig.class, SecurityProblemWriter.class, ApiExceptionHandler.class, CorrelationIdFilter.class})
class CartPermissionTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CartCommandService carts;

    @Test
    void readOwnCannotMutateAndWriteOwnCan() throws Exception {
        mvc.perform(get("/api/v1/cart").with(customer("PERM_cart.read_own")))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/cart/items/HEADPHONES-BLK")
                        .with(customer("PERM_cart.read_own"))
                        .contentType("application/json")
                        .content("{\"quantity\":1,\"expectedVersion\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CART_ACCESS_DENIED"));
        mvc.perform(put("/api/v1/cart/items/HEADPHONES-BLK")
                        .with(customer("PERM_cart.write_own"))
                        .contentType("application/json")
                        .content("{\"quantity\":2,\"expectedVersion\":3,\"owner\":\"forged\",\"unitPrice\":\"1.00\",\"currency\":\"EUR\"}"))
                .andExpect(status().isOk());
        verify(carts).setQuantity(eq(new Owner("http://issuer.test", "customer-1")), eq("HEADPHONES-BLK"), eq(2), eq(3L), any());
    }

    @Test
    void staffAndCatalogRolesDoNotOpenACart() throws Exception {
        mvc.perform(get("/api/v1/cart").with(jwt().authorities(new SimpleGrantedAuthority("PERM_admin.access"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/cart").with(jwt().authorities(new SimpleGrantedAuthority("PERM_catalog.read"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/cart/other").with(customer("PERM_cart.read_own")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(carts);
    }

    @Test
    void missingTokenAndForgedHeadersAreRejected() throws Exception {
        mvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CART_AUTHENTICATION_REQUIRED"));
        mvc.perform(put("/api/v1/cart/items/HEADPHONES-BLK")
                        .header("X-User-Id", "customer-1")
                        .header("X-Roles", "CUSTOMER")
                        .contentType("application/json")
                        .content("{\"quantity\":1,\"expectedVersion\":0}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(carts);
    }

    @Test
    void deleteRequiresExpectedVersion() throws Exception {
        mvc.perform(delete("/api/v1/cart/items/HEADPHONES-BLK").with(customer("PERM_cart.write_own")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_VALIDATION_FAILED"));
        verify(carts, org.mockito.Mockito.never()).remove(any(), anyString(), anyLong(), any());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor customer(String authority) {
        return jwt().jwt(token -> token.subject("customer-1").issuer("http://issuer.test"))
                .authorities(new SimpleGrantedAuthority(authority));
    }
}
