package com.lineless.auth_service.integration;

import com.lineless.auth_service.config.Bucket4jConfig;
import com.lineless.auth_service.config.RedisConfig;
import com.lineless.auth_service.exception.RateLimitExceededException;
import com.lineless.auth_service.exception.RateLimitInfrastructureException;
import com.lineless.auth_service.repository.UserRepository;
import com.lineless.auth_service.service.*;
import io.github.bucket4j.BucketExceptions;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = {
        RedisConfig.class,
        Bucket4jConfig.class,
        RateLimitService.class,
        RegistrationRateLimitService.class,
        LoginAttemptRateLimitService.class,
        PasswordResetRateLimitService.class,
        ResendVerificationService.class
})
@Testcontainers
class RateLimitServiceIntegrationTest {

    private static final String REDIS_PASSWORD = "test-password";
    private static final Duration REFILL_PERIOD = Duration.ofHours(1);
    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7"))
                    .withExposedPorts(6379)
                    .withCommand("redis-server", "--requirepass", REDIS_PASSWORD)
                    .waitingFor(Wait.forListeningPort());
    @Autowired
    private ProxyManager<String> proxyManager;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private RegistrationRateLimitService registrationRateLimitService;

    @Autowired
    private LoginAttemptRateLimitService loginAttemptRateLimitService;

    @Autowired
    private PasswordResetRateLimitService passwordResetRateLimitService;

    @Autowired
    private ResendVerificationService resendVerificationService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private EmailVerificationTokenService emailVerificationTokenService;

    @MockitoBean
    private EmailSenderService emailSenderService;

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.data.redis.password", () -> REDIS_PASSWORD);
        registry.add("spring.data.redis.timeout", () -> "2s");
    }

    @Test
    void allowsRequestsUpToCapacityThenRejectsNextRequest() {
        String key = uniqueKey("capacity");

        for (int i = 0; i < 3; i++) {
            assertThat(rateLimitService.tryConsume(key, 3, REFILL_PERIOD))
                    .isTrue();
        }

        assertThat(rateLimitService.tryConsume(key, 3, REFILL_PERIOD))
                .isFalse();
    }

    @Test
    void resetRestoresBucketCapacity() {
        String key = uniqueKey("reset");

        assertThat(rateLimitService.tryConsume(key, 2, REFILL_PERIOD)).isTrue();
        assertThat(rateLimitService.tryConsume(key, 2, REFILL_PERIOD)).isTrue();
        assertThat(rateLimitService.tryConsume(key, 2, REFILL_PERIOD)).isFalse();

        rateLimitService.reset(key, 2, REFILL_PERIOD);

        assertThat(rateLimitService.tryConsume(key, 2, REFILL_PERIOD)).isTrue();
        assertThat(rateLimitService.tryConsume(key, 2, REFILL_PERIOD)).isTrue();
    }

    @Test
    void convertsBucketExecutionExceptionToInfrastructureException() {
        String key = uniqueKey("bucket-execution");
        ProxyManager<String> mockedProxyManager =
                org.mockito.Mockito.mock(ProxyManager.class);
        org.mockito.Mockito.when(
                mockedProxyManager.getProxy(
                        org.mockito.ArgumentMatchers.eq(key),
                        org.mockito.ArgumentMatchers.any()
                )
        ).thenThrow(new BucketExceptions.BucketExecutionException(
                "CAS retry exhaustion"
        ));
        RateLimitService isolatedService =
                new RateLimitService(mockedProxyManager);

        assertThatThrownBy(() ->
                isolatedService.tryConsume(key, 2, REFILL_PERIOD)
        ).isInstanceOf(RateLimitInfrastructureException.class)
                .hasCauseInstanceOf(BucketExceptions.BucketExecutionException.class);
    }

    @Test
    void doesNotConvertUnrelatedProgrammingException() {
        String key = uniqueKey("unrelated");
        IllegalArgumentException exception =
                new IllegalArgumentException("Invalid bucket configuration");

        ProxyManager<String> mockedProxyManager =
                org.mockito.Mockito.mock(ProxyManager.class);
        org.mockito.Mockito.when(
                mockedProxyManager.getProxy(
                        org.mockito.ArgumentMatchers.eq(key),
                        org.mockito.ArgumentMatchers.any()
                )
        ).thenThrow(exception);
        RateLimitService isolatedService =
                new RateLimitService(mockedProxyManager);

        assertThatThrownBy(() ->
                isolatedService.tryConsume(key, 2, REFILL_PERIOD)
        ).isSameAs(exception);
    }

    @Test
    void redisOutageThrowsInfrastructureExceptionAndRecovers() throws Exception {
        String outageKey = uniqueKey("outage");
        boolean pauseAttempted = false;
        boolean pauseCompleted = false;

        try {
            pauseAttempted = true;
            redis.getDockerClient()
                    .pauseContainerCmd(redis.getContainerId())
                    .exec();
            pauseCompleted = true;

            assertThatThrownBy(() ->
                    rateLimitService.tryConsume(
                            outageKey,
                            2,
                            REFILL_PERIOD
                    )
            ).isInstanceOf(RateLimitInfrastructureException.class);
        } finally {
            if (pauseCompleted || pauseAttempted) {
                Boolean paused = null;
                try {
                    paused = redis.getContainerInfo()
                            .getState()
                            .getPaused();
                } catch (RuntimeException ignored) {
                    paused = pauseCompleted;
                }

                if (pauseCompleted || Boolean.TRUE.equals(paused)) {
                    redis.getDockerClient()
                            .unpauseContainerCmd(redis.getContainerId())
                            .exec();
                }
            }
        }

        Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(10))
                .pollInterval(java.time.Duration.ofMillis(100))
                .until(() -> {
                    org.testcontainers.containers.Container.ExecResult result =
                            redis.execInContainer(
                                    "redis-cli",
                                    "-a",
                                    REDIS_PASSWORD,
                                    "PING"
                            );
                    return result.getExitCode() == 0
                            && result.getStdout().contains("PONG");
                });

        assertThat(rateLimitService.tryConsume(
                outageKey,
                2,
                REFILL_PERIOD
        )).isTrue();
    }

    @Test
    void concurrentRequestsConsumeExactlyTheBucketCapacity() throws Exception {
        String key = uniqueKey("concurrent");
        int capacity = 10;
        int requestCount = 30;

        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);

        try {
            java.util.List<Future<?>> futures = new java.util.ArrayList<>();

            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "Concurrent rate-limit requests did not start together");
                        }

                        if (rateLimitService.tryConsume(
                                key,
                                capacity,
                                REFILL_PERIOD
                        )) {
                            successes.incrementAndGet();
                        } else {
                            rejections.incrementAndGet();
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(
                                "Concurrent rate-limit request interrupted",
                                exception
                        );
                    }
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            for (Future<?> future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(successes.get()).isEqualTo(capacity);
        assertThat(rejections.get()).isEqualTo(requestCount - capacity);
        assertThat(rateLimitService.isAllowed(key, capacity, REFILL_PERIOD))
                .isFalse();
    }

    @Test
    void differentKeysHaveIndependentBuckets() {
        String firstKey = uniqueKey("first");
        String secondKey = uniqueKey("second");

        assertThat(rateLimitService.tryConsume(firstKey, 1, REFILL_PERIOD)).isTrue();
        assertThat(rateLimitService.tryConsume(firstKey, 1, REFILL_PERIOD)).isFalse();

        assertThat(rateLimitService.tryConsume(secondKey, 1, REFILL_PERIOD)).isTrue();
    }

    @Test
    void twoRateLimitServiceInstancesShareBucketState() {
        String key = uniqueKey("shared");
        RateLimitService secondService = new RateLimitService(proxyManager);

        assertThat(rateLimitService.tryConsume(key, 3, REFILL_PERIOD)).isTrue();
        assertThat(secondService.tryConsume(key, 3, REFILL_PERIOD)).isTrue();
        assertThat(rateLimitService.tryConsume(key, 3, REFILL_PERIOD)).isTrue();
        assertThat(secondService.tryConsume(key, 3, REFILL_PERIOD)).isFalse();
    }

    @Test
    void registrationEmailLimitAllowsThreeAndRejectsFourth() {
        String email = uniqueKey("registration-email") + "@example.com";
        String ip = "192.0.2." + UUID.randomUUID().toString().hashCode();

        assertThat(registrationRateLimitService.tryConsume(email, ip)).isTrue();
        assertThat(registrationRateLimitService.tryConsume(email, ip)).isTrue();
        assertThat(registrationRateLimitService.tryConsume(email, ip)).isTrue();
        assertThat(registrationRateLimitService.tryConsume(email, ip)).isFalse();
    }

    @Test
    void registrationIpLimitAllowsFiveAndRejectsSixth() {
        String ip = "198.51.100." + Math.abs(UUID.randomUUID().toString().hashCode() % 200);

        for (int i = 0; i < 5; i++) {
            assertThat(registrationRateLimitService.tryConsume(
                    uniqueKey("registration-ip-email") + "@example.com",
                    ip
            )).isTrue();
        }

        assertThat(registrationRateLimitService.tryConsume(
                uniqueKey("registration-ip-sixth") + "@example.com",
                ip
        )).isFalse();
    }

    @Test
    void registrationEmailAndIpBucketsAreIndependent() {
        String emailA = uniqueKey("registration-independent-a") + "@example.com";
        String emailB = uniqueKey("registration-independent-b") + "@example.com";
        String ipA = "203.0.113." + Math.abs(UUID.randomUUID().toString().hashCode() % 200);
        String ipB = "203.0.113." + Math.abs(UUID.randomUUID().toString().hashCode() % 200 + 200);

        for (int i = 0; i < 3; i++) {
            assertThat(registrationRateLimitService.tryConsume(emailA, ipA)).isTrue();
        }
        assertThat(registrationRateLimitService.tryConsume(emailA, ipA)).isFalse();

        assertThat(registrationRateLimitService.tryConsume(emailB, ipA)).isTrue();
        assertThat(registrationRateLimitService.tryConsume(
                uniqueKey("registration-independent-c") + "@example.com",
                ipB
        )).isTrue();
    }

    @Test
    void loginEmailLimitAllowsFiveFailedAttemptsAndRejectsFurtherAttempts() {
        String email = uniqueKey("login-email") + "@example.com";
        String ip = "192.0.2." + Math.abs(UUID.randomUUID().toString().hashCode() % 200);

        for (int i = 0; i < 5; i++) {
            assertThat(loginAttemptRateLimitService.recordFailedAttempt(email, ip)).isTrue();
        }

        assertThat(loginAttemptRateLimitService.recordFailedAttempt(email, ip)).isFalse();
    }

    @Test
    void loginIpLimitAllowsTwentyFailedAttemptsAndRejectsFurtherAttempts() {
        String ip = "198.51.100." + Math.abs(UUID.randomUUID().toString().hashCode() % 200);

        for (int i = 0; i < 20; i++) {
            String email = uniqueKey("login-ip-email") + "@example.com";
            assertThat(loginAttemptRateLimitService.recordFailedAttempt(email, ip)).isTrue();
        }

        assertThat(loginAttemptRateLimitService.recordFailedAttempt(
                uniqueKey("login-ip-sixth") + "@example.com",
                ip
        )).isFalse();
    }

    @Test
    void successfulLoginResetClearsOnlyEmailBucket() {
        String email = uniqueKey("login-reset-email") + "@example.com";
        String ip = "203.0.113." + Math.abs(UUID.randomUUID().toString().hashCode() % 200);

        for (int i = 0; i < 5; i++) {
            assertThat(loginAttemptRateLimitService.recordFailedAttempt(email, ip)).isTrue();
        }
        assertThat(loginAttemptRateLimitService.recordFailedAttempt(email, ip)).isFalse();

        loginAttemptRateLimitService.resetEmailAttempts(email);

        String freshIp = "198.51.100."
                + Math.abs(UUID.randomUUID().toString().hashCode() % 200);

        assertThat(loginAttemptRateLimitService.isAllowed(email, freshIp)).isTrue();

        for (int i = 0; i < 14; i++) {
            assertThat(loginAttemptRateLimitService.recordFailedAttempt(
                    uniqueKey("login-reset-other-email") + "@example.com",
                    ip
            )).isTrue();
        }

        assertThat(loginAttemptRateLimitService.recordFailedAttempt(
                uniqueKey("login-reset-last-email") + "@example.com",
                ip
        )).isFalse();

        /*
         * The email bucket was reset, while the IP bucket was not:
         * the original email is available on a fresh IP, but the
         * original IP remains exhausted.
         */
        assertThat(loginAttemptRateLimitService.isAllowed(email, freshIp)).isTrue();
        assertThat(loginAttemptRateLimitService.isAllowed(
                uniqueKey("login-reset-ip-check") + "@example.com",
                ip
        )).isFalse();
    }

    @Test
    void passwordResetEmailLimitAllowsThreeAndRejectsFourth() {
        String email = uniqueKey("password-reset-email") + "@example.com";
        String ip = "192.0.2." + Math.abs(UUID.randomUUID().toString().hashCode() % 200);

        assertThat(passwordResetRateLimitService.tryConsume(email, ip)).isTrue();
        assertThat(passwordResetRateLimitService.tryConsume(email, ip)).isTrue();
        assertThat(passwordResetRateLimitService.tryConsume(email, ip)).isTrue();
        assertThat(passwordResetRateLimitService.tryConsume(email, ip)).isFalse();
    }

    @Test
    void passwordResetIpLimitAllowsTenAndRejectsEleventh() {
        String ip = "198.51.100." + Math.abs(UUID.randomUUID().toString().hashCode() % 200);

        for (int i = 0; i < 10; i++) {
            assertThat(passwordResetRateLimitService.tryConsume(
                    uniqueKey("password-reset-ip-email") + "@example.com",
                    ip
            )).isTrue();
        }

        assertThat(passwordResetRateLimitService.tryConsume(
                uniqueKey("password-reset-ip-eleventh") + "@example.com",
                ip
        )).isFalse();
    }

    @Test
    void passwordResetEmailAndIpBucketsAreIndependent() {
        String emailA = uniqueKey("password-reset-independent-a") + "@example.com";
        String emailB = uniqueKey("password-reset-independent-b") + "@example.com";
        String ipA = "203.0.113." + Math.abs(UUID.randomUUID().toString().hashCode() % 200);
        String ipB = "203.0.113." + Math.abs(UUID.randomUUID().toString().hashCode() % 200 + 200);

        for (int i = 0; i < 3; i++) {
            assertThat(passwordResetRateLimitService.tryConsume(emailA, ipA)).isTrue();
        }
        assertThat(passwordResetRateLimitService.tryConsume(emailA, ipA)).isFalse();

        assertThat(passwordResetRateLimitService.tryConsume(emailB, ipA)).isTrue();
        assertThat(passwordResetRateLimitService.tryConsume(
                uniqueKey("password-reset-independent-c") + "@example.com",
                ipB
        )).isTrue();
    }

    @Test
    void resendVerificationEmailLimitAllowsThreeAndRejectsFourth() {
        String email = uniqueKey("resend-email") + "@example.com";
        String ip = "192.0.2." + Math.abs(UUID.randomUUID().toString().hashCode() % 200);

        for (int i = 0; i < 3; i++) {
            resendVerificationService.resend(email, ip);
        }

        assertThatThrownBy(() -> resendVerificationService.resend(email, ip))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void resendVerificationIpLimitAllowsTenAndRejectsEleventh() {
        String ip = "198.51.100." + Math.abs(UUID.randomUUID().toString().hashCode() % 200);

        for (int i = 0; i < 10; i++) {
            resendVerificationService.resend(
                    uniqueKey("resend-ip-email") + "@example.com",
                    ip
            );
        }

        assertThatThrownBy(() -> resendVerificationService.resend(
                uniqueKey("resend-ip-eleventh") + "@example.com",
                ip
        )).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void resendVerificationEmailAndIpBucketsAreIndependent() {
        String emailA = uniqueKey("resend-independent-a") + "@example.com";
        String emailB = uniqueKey("resend-independent-b") + "@example.com";
        String ipA = "203.0.113." + Math.abs(UUID.randomUUID().toString().hashCode() % 200);
        String ipB = "203.0.113." + Math.abs(UUID.randomUUID().toString().hashCode() % 200 + 200);

        for (int i = 0; i < 3; i++) {
            resendVerificationService.resend(emailA, ipA);
        }
        assertThatThrownBy(() -> resendVerificationService.resend(emailA, ipA))
                .isInstanceOf(RateLimitExceededException.class);

        resendVerificationService.resend(emailB, ipA);
        resendVerificationService.resend(
                uniqueKey("resend-independent-c") + "@example.com",
                ipB
        );
    }

    private String uniqueKey(String suffix) {
        return "lineless:test:rate-limit:"
                + suffix + ":"
                + UUID.randomUUID();
    }
}
