package com.brainburst.score;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class SubmitScoreHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final DynamoDbClient db = DynamoDbClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context ctx) {
        LambdaLogger logger = ctx.getLogger();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            logger.log("Received score submission: " + event.getBody() + "\n");
            logger.log("RO IS SEXY");
            // process event
            logger.log("EVENT: " + gson.toJson(event));

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

            logger.log("Score successfully submitted for user: " + username + " with score: " + score + "\n");

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody("{\"message\": \"Score submitted!\"}");
        } catch (Exception e) {
            logger.log("ERROR submitting score: " + e.getMessage() + "\n");
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\": \"Failed to submit score.\"}");
        }
    }
}
