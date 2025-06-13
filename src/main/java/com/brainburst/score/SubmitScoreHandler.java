package com.brainburst.score;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class SubmitScoreHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    static {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
    }

    private static final Logger logger = LoggerFactory.getLogger(SubmitScoreHandler.class);
    private final DynamoDbClient db = DynamoDbClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context ctx) {
        logger.info("=== TEST LOG ===");
        System.out.println("=== SYSTEM OUT LOG ===");
        logger.error("This is a test ERROR log");
        try {
            logger.info("Received score submission: {}", event.getBody());

            Map<String, Object> input = objectMapper.readValue(event.getBody(), Map.class);
            String username = (String) input.get("user");
            int score = ((Number) input.get("score")).intValue();

            db.putItem(PutItemRequest.builder()
                    .tableName(System.getenv("SCORES_TABLE"))
                    .item(Map.of(
                            "scoreId", AttributeValue.fromS(UUID.randomUUID().toString()),
                            "user", AttributeValue.fromS(username),
                            "score", AttributeValue.fromN(Integer.toString(score)),
                            "timestamp", AttributeValue.fromS(Instant.now().toString())
                    )).build());

            logger.info("Score submitted for user {}: {}", username, score);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody("{\"message\": \"Score submitted!\"}");
        } catch (Exception e) {
            logger.error("Failed to submit score: {}", e.getMessage(), e);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\": \"Failed to submit score.\"}");
        }
    }
}
