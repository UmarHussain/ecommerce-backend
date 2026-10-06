package com.umar.ecommerce.catalog.controller;
import com.umar.ecommerce.catalog.config.SecurityConfig;
import com.umar.ecommerce.catalog.service.*;
import com.umar.ecommerce.catalog.web.SecurityProblemWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verifyNoInteractions;
@WebMvcTest(AdminCatalogController.class)
@Import({SecurityConfig.class, SecurityProblemWriter.class})
class CatalogPermissionTest {
 @Autowired MockMvc mvc;
 @MockitoBean CategoryService categories;
 @MockitoBean ProductService products;
 @MockitoBean ProductVariantService variants;
 @Test void creatorCannotUpdateExistingProduct() throws Exception {
  mvc.perform(put("/api/v1/admin/catalog/products/00000000-0000-0000-0000-000000000001")
   .with(jwt().authorities(new SimpleGrantedAuthority("PERM_catalog.create")))
   .contentType("application/json").content("{}"))
   .andExpect(status().isForbidden());
  verifyNoInteractions(products);
 }
 @Test void editorCannotCreate() throws Exception {
  mvc.perform(post("/api/v1/admin/catalog/categories")
   .with(jwt().authorities(new SimpleGrantedAuthority("PERM_catalog.update")))
   .contentType("application/json").content("{}"))
   .andExpect(status().isForbidden());
  verifyNoInteractions(categories);
 }
 @Test void claimedUserHeadersDoNotAuthenticate() throws Exception {
  mvc.perform(post("/api/v1/admin/catalog/categories").header("X-Roles","PLATFORM_ADMIN")
   .header("X-User-Id","admin").contentType("application/json").content("{}"))
   .andExpect(status().isUnauthorized());
  verifyNoInteractions(categories);
 }
}
