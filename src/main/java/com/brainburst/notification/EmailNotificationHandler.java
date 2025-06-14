package com.brainburst.notification;


import com.amazonaws.services.lambda.runtime.Context;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.util.Objects;

public class EmailNotificationHandler {

    private static final EmailNotificationHandler instance = new EmailNotificationHandler();
    private final SesClient sesClient;
    private final String senderEmail;
    private Context context;

    private EmailNotificationHandler() {
        try {
            this.sesClient = SesClient.create();
            this.senderEmail = Objects.requireNonNull(
                    System.getenv("SES_SENDER_EMAIL"),
                    "SES_SENDER_EMAIL environment variable is missing"
            );
            context.getLogger().log("SES EmailHandler initialized for production use.");
        } catch (Exception e) {
            context.getLogger().log("SES EmailHandler initialization failed" + "\nException:" + e);
            throw e;
        }
    }

    public static EmailNotificationHandler getInstance(Context context) {
        instance.context = context;
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
            context.getLogger().log("Email sent via SES to: " + toEmail);

        } catch (SesException e) {
            context.getLogger().log("Failed to send SES email to:" + toEmail + "\nException:" + e.awsErrorDetails().errorMessage());
        }
    }
}

