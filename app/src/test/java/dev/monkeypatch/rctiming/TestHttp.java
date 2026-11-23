package dev.monkeypatch.rctiming;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;

/**
 * Calls the running app over HTTP in integration tests, in place of Spring Boot's {@code TestHttp}
 * (marked for removal).
 *
 * <p>It has the same four calls the tests used, with the same behaviour: an address is relative to the
 * app's random port, and a 4xx or 5xx answer comes back as a {@link ResponseEntity} for the test to assert
 * on instead of throwing. Redirects are not followed, so a test sees the 3xx itself. Cookies are not kept;
 * a test that needs one sends it as a header.
 *
 * <p>Runs on the test thread; one instance is shared by every test class through {@link Config}.
 */
public class TestHttp {

    private final Environment environment;
    private volatile URI base;
    private volatile RestClient client;

    TestHttp(Environment environment) {
        this.environment = environment;
    }

    /**
     * Built on the first call, not at start-up: the app's random port is only set once the web server has
     * started, which is after this bean is created.
     */
    private RestClient client() {
        RestClient ready = client;
        if (ready == null) {
            synchronized (this) {
                if (client == null) {
                    base = URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port"));
                    HttpClient jdk = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
                    client = RestClient.builder()
                            .baseUrl(base.toString())
                            .requestFactory(new JdkClientHttpRequestFactory(jdk))
                            .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { })
                            .build();
                }
                ready = client;
            }
        }
        return ready;
    }

    /** Makes this available to every test class that extends {@link AbstractIntegrationTest}. */
    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        TestHttp testHttp(@Autowired Environment environment) {
            return new TestHttp(environment);
        }
    }

    public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<?> request, Class<T> type,
            Object... uriVariables) {
        return send(client().method(method).uri(url, uriVariables), request, type);
    }

    public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<?> request,
            ParameterizedTypeReference<T> type, Object... uriVariables) {
        return send(client().method(method).uri(url, uriVariables), request, type);
    }

    public <T> ResponseEntity<T> exchange(URI url, HttpMethod method, HttpEntity<?> request, Class<T> type) {
        return send(client().method(method).uri(resolve(url)), request, type);
    }

    public <T> ResponseEntity<T> exchange(RequestEntity<?> request, Class<T> type) {
        return send(client().method(request.getMethod()).uri(resolve(request.getUrl())), request, type);
    }

    public <T> ResponseEntity<T> exchange(RequestEntity<?> request, ParameterizedTypeReference<T> type) {
        return send(client().method(request.getMethod()).uri(resolve(request.getUrl())), request, type);
    }

    public <T> ResponseEntity<T> getForEntity(String url, Class<T> type, Object... uriVariables) {
        return exchange(url, HttpMethod.GET, null, type, uriVariables);
    }

    public <T> ResponseEntity<T> getForEntity(URI url, Class<T> type) {
        return exchange(url, HttpMethod.GET, null, type);
    }

    public <T> T getForObject(String url, Class<T> type, Object... uriVariables) {
        return getForEntity(url, type, uriVariables).getBody();
    }

    public <T> ResponseEntity<T> postForEntity(String url, Object request, Class<T> type, Object... uriVariables) {
        return exchange(url, HttpMethod.POST, asEntity(request), type, uriVariables);
    }

    public <T> ResponseEntity<T> postForEntity(URI url, Object request, Class<T> type) {
        return exchange(url, HttpMethod.POST, asEntity(request), type);
    }

    private static HttpEntity<?> asEntity(Object request) {
        return request instanceof HttpEntity<?> entity ? entity : new HttpEntity<>(request);
    }

    /** An address as a full URI on the app's port; one that is already absolute is kept. */
    private URI resolve(URI url) {
        return url.isAbsolute() ? url : URI.create(base + url.toString());
    }

    private <T> ResponseEntity<T> send(RestClient.RequestBodySpec spec, HttpEntity<?> request, Class<T> type) {
        RestClient.ResponseSpec response = prepare(spec, request).retrieve();
        return type == Void.class ? cast(response.toBodilessEntity()) : response.toEntity(type);
    }

    private <T> ResponseEntity<T> send(RestClient.RequestBodySpec spec, HttpEntity<?> request,
            ParameterizedTypeReference<T> type) {
        return prepare(spec, request).retrieve().toEntity(type);
    }

    private static RestClient.RequestBodySpec prepare(RestClient.RequestBodySpec spec, HttpEntity<?> request) {
        if (request == null) {
            return spec;
        }
        HttpHeaders headers = request.getHeaders();
        spec.headers(target -> target.addAll(headers));
        if (request.getBody() != null) {
            spec.body(request.getBody());
        }
        return spec;
    }

    @SuppressWarnings("unchecked")
    private static <T> ResponseEntity<T> cast(ResponseEntity<Void> response) {
        return (ResponseEntity<T>) response;
    }
}
