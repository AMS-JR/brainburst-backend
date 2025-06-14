package com.brainburst.sns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;


import java.util.Map;
import java.util.Objects;

public class SnsHandler {
    private static final SnsHandler instance = new SnsHandler();
    private final SnsClient sns;
    private final String snsTopicArn;
    private final Logger logger = LoggerFactory.getLogger(SnsHandler.class);

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

    public void publishSns(String message, String email) {
        try {
            sns.publish(PublishRequest.builder()
                    .topicArn(snsTopicArn)
                    .message(Objects.requireNonNull(message, "Message cannot be null"))
                    .messageAttributes(Map.of(
                            "email", MessageAttributeValue.builder()
                                    .dataType("String")
                                    .stringValue(Objects.requireNonNull(email, "Email cannot be null"))
                                    .build()
                    ))
                    .build());
        } catch (Exception e) {
            logger.error("Failed to publish sns", e);
        }
    }

}