package com.brainburst.sns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SnsHandler {
    private static final SnsHandler instance = new SnsHandler();
    private final SnsClient sns;
    private final String snsTopicArn;
    private final Logger logger = LoggerFactory.getLogger(SnsHandler.class);


    private final Set<String> confirmedEmails = ConcurrentHashMap.newKeySet();
    private Instant cacheExpiryTime = Instant.MIN;
    private final long cacheTtlSeconds = 300; // 5 minutes

    private SnsHandler() {
        try {
            sns = SnsClient.create();
            snsTopicArn = Objects.requireNonNull(
                    System.getenv("SNS_TOPIC_ARN"),
                    "SNS_TOPIC_ARN environment variable is missing"
            );
        } catch (Exception e) {
            logger.error("SnsHandler initialization failed", e);
            throw e;
        }
    }

    public static SnsHandler getInstance() {
        return instance;
    }

    private synchronized void refreshCacheIfExpired() {
        if (Instant.now().isBefore(cacheExpiryTime)) return;

        logger.info("Refreshing SNS subscription cache...");
        confirmedEmails.clear();

        try {
            sns.listSubscriptionsByTopicPaginator(ListSubscriptionsByTopicRequest.builder()
                            .topicArn(snsTopicArn)
                            .build())
                    .subscriptions()
                    .stream()
                    .filter(sub ->
                            sub.subscriptionArn() != null &&
                                    !sub.subscriptionArn().equals("PendingConfirmation") &&
                                    sub.protocol().equalsIgnoreCase("email"))
                    .forEach(sub -> confirmedEmails.add(sub.endpoint().toLowerCase()));

            cacheExpiryTime = Instant.now().plusSeconds(cacheTtlSeconds);
        } catch (Exception e) {
            logger.error("Failed to refresh SNS subscription cache", e);
        }
    }

    public void subscribeEmailToTopic(String email) {
        refreshCacheIfExpired();

        String normalizedEmail = email.toLowerCase();
        if (confirmedEmails.contains(normalizedEmail)) {
            logger.info("Email already subscribed and confirmed: {}", normalizedEmail);
            return;
        }

        try {
            sns.subscribe(SubscribeRequest.builder()
                    .topicArn(snsTopicArn)
                    .protocol("email")
                    .endpoint(normalizedEmail)
                    .build());

            logger.info("Subscription request sent to email: {}", normalizedEmail);
        } catch (Exception e) {
            logger.error("Failed to subscribe email to SNS topic", e);
        }
    }

    public void publishSns(String message, String email) {
        refreshCacheIfExpired();

        String normalizedEmail = email.toLowerCase();
        if (!confirmedEmails.contains(normalizedEmail)) {
            logger.warn("Email not confirmed for SNS topic, skipping publish: {}", normalizedEmail);
            return;
        }

        try {
            sns.publish(PublishRequest.builder()
                    .topicArn(snsTopicArn)
                    .message(Objects.requireNonNull(message, "Message cannot be null"))
                    .messageAttributes(Map.of(
                            "email", MessageAttributeValue.builder()
                                    .dataType("String")
                                    .stringValue(normalizedEmail)
                                    .build()
                    ))
                    .build());
        } catch (Exception e) {
            logger.error("Failed to publish SNS message", e);
        }
    }
}
