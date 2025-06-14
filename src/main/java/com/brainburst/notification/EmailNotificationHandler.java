package com.brainburst.notification;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.util.Objects;

public class EmailNotificationHandler {

    private static final EmailNotificationHandler instance = new EmailNotificationHandler();
    private final SesClient sesClient;
    private final Logger logger = LoggerFactory.getLogger(EmailNotificationHandler.class);
    private final String senderEmail;

    private EmailNotificationHandler() {
        try {
            this.sesClient = SesClient.create();
            this.senderEmail = Objects.requireNonNull(
                    System.getenv("SES_SENDER_EMAIL"),
                    "SES_SENDER_EMAIL environment variable is missing"
            );
            logger.info("SES EmailHandler initialized for production use.");
        } catch (Exception e) {
            logger.error("SES EmailHandler initialization failed", e);
            throw e;
        }
    }

    public static EmailNotificationHandler getInstance() {
        return instance;
    }

    public void sendEmail(String toEmail, String subject, String message) {
        try {
            SendEmailRequest emailRequest = SendEmailRequest.builder()
                    .destination(Destination.builder()
                            .toAddresses(toEmail)
                            .build())
                    .message(Message.builder()
                            .subject(Content.builder()
                                    .data(subject)
                                    .charset("UTF-8")
                                    .build())
                            .body(Body.builder()
                                    .text(Content.builder()
                                            .data(message)
                                            .charset("UTF-8")
                                            .build())
                                    .build())
                            .build())
                    .source(senderEmail)
                    .build();

            sesClient.sendEmail(emailRequest);
            logger.info("Email sent via SES to: {}", toEmail);

        } catch (SesException e) {
            logger.error("Failed to send SES email to {}: {}", toEmail, e.awsErrorDetails().errorMessage());
        }
    }
}

