package com.brainburst.score;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class SubmitScoreHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final DynamoDbClient db = DynamoDbClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context ctx) {
        try {
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

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody("{\"message\": \"Score submitted!\"}");
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\": \"Failed to submit score.\"}");
        }
    }
}
