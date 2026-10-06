package com.umar.ecommerce.user.infrastructure.keycloak;

import com.umar.ecommerce.user.config.KeycloakAdminProperties;
import com.umar.ecommerce.user.exception.ConflictException;
import com.umar.ecommerce.user.exception.DirectoryUnavailableException;
import com.umar.ecommerce.user.exception.NotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Least-privilege Keycloak Admin REST client. Caches the service-account token
 * in process memory until shortly before expiry.
 */
@Component
public class KeycloakAdminClient {

    private final KeycloakAdminProperties properties;
    private final RestClient tokenClient;
    private final RestClient adminClient;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public KeycloakAdminClient(KeycloakAdminProperties properties) {
        this.properties = properties;
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build()
        );
        factory.setReadTimeout(properties.getReadTimeout());
        this.tokenClient = RestClient.builder().requestFactory(factory).build();
        this.adminClient = RestClient.builder()
                .baseUrl(properties.getAdminBaseUri())
                .requestFactory(factory)
                .build();
    }

    public <T> T get(String path, Class<T> type) {
        return execute(() -> adminClient.get().uri(path)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(type));
    }

    public <T> T get(String path, Class<T> type, Object... uriVariables) {
        return execute(() -> adminClient.get().uri(path, uriVariables)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(type));
    }

    public void post(String path, Object body) {
        execute(() -> {
            adminClient.post().uri(path)
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    public String postForLocation(String path, Object body) {
        return execute(() -> {
            var response = adminClient.post().uri(path)
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            var location = response.getHeaders().getLocation();
            return location == null ? null : location.toString();
        });
    }

    public void delete(String path, Object body) {
        execute(() -> {
            adminClient.method(org.springframework.http.HttpMethod.DELETE).uri(path)
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    public void put(String path, Object body) {
        execute(() -> {
            adminClient.put().uri(path)
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    private String bearer() {
        return "Bearer " + accessToken();
    }

    private String accessToken() {
        Instant now = Instant.now();
        if (cachedToken != null && now.isBefore(tokenExpiresAt.minusSeconds(30))) {
            return cachedToken;
        }
        lock.lock();
        try {
            if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(30))) {
                return cachedToken;
            }
            if (properties.getClientSecret() == null || properties.getClientSecret().isBlank()) {
                throw new DirectoryUnavailableException("KEYCLOAK_ADMIN_CLIENT_SECRET is not configured");
            }
            var form = new LinkedMultiValueMap<String, String>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", properties.getClientId());
            form.add("client_secret", properties.getClientSecret());
            @SuppressWarnings("unchecked")
            Map<String, Object> token = tokenClient.post()
                    .uri(properties.getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            if (token == null || token.get("access_token") == null) {
                throw new DirectoryUnavailableException("Keycloak did not return a service-account token");
            }
            cachedToken = token.get("access_token").toString();
            int expiresIn = token.get("expires_in") instanceof Number number ? number.intValue() : 60;
            tokenExpiresAt = Instant.now().plusSeconds(expiresIn);
            return cachedToken;
        } catch (ResourceAccessException exception) {
            throw new DirectoryUnavailableException("Keycloak token endpoint timed out", exception);
        } catch (RestClientResponseException exception) {
            throw new DirectoryUnavailableException(
                    "Keycloak token endpoint rejected the service account",
                    exception
            );
        } finally {
            lock.unlock();
        }
    }

    private <T> T execute(RemoteCall<T> call) {
        try {
            return call.get();
        } catch (ResourceAccessException exception) {
            throw new DirectoryUnavailableException("Keycloak Admin REST timed out or was unreachable", exception);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 404) {
                throw new NotFoundException("Directory identity was not found");
            }
            if (status == 409) {
                throw new ConflictException("Directory identity already exists");
            }
            if (status == 400) {
                throw new ConflictException("USER_DIRECTORY_REJECTED", "Keycloak rejected the administration request");
            }
            throw new DirectoryUnavailableException(
                    "Keycloak Admin REST returned HTTP " + status,
                    exception
            );
        }
    }

    @FunctionalInterface
    private interface RemoteCall<T> {
        T get();
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> asObjectList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) (List<?>) list;
        }
        return List.of();
    }
}
