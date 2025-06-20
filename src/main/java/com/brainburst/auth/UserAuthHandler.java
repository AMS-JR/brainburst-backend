package com.brainburst.auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.Map;

public class UserAuthHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final DynamoDbClient db = DynamoDbClient.create();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        LambdaLogger logger = context.getLogger();

        try {
            Map<String, Object> request = (Map<String, Object>) input.get("request");
            Map<String, String> attributes = (Map<String, String>) request.get("userAttributes");

            String userId = attributes.get("sub");
            String email = attributes.get("email");

            logger.log("Registering new user: " + userId + " (" + email + ")\n");

            db.putItem(PutItemRequest.builder()
                    .tableName(System.getenv("USERS_TABLE"))
                    .item(Map.of(
                            "userId", AttributeValue.fromS(userId),
                            "email", AttributeValue.fromS(email),
                            "highestScore", AttributeValue.fromN("0"),
                            "totalGamesPlayed", AttributeValue.fromN("0")
                    ))
                    .conditionExpression("attribute_not_exists(username)")
                    .build());

            logger.log("User registered successfully.\n");

        } catch (Exception e) {
            logger.log("Failed to insert user: " + e.getMessage() + "\n");
        }

        return input; // Must return original event
    }
}
