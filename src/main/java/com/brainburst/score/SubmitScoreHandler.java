package com.brainburst.score;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class SubmitScoreHandler implements RequestHandler<Map<String,Object>, String> {
    private final DynamoDbClient db = DynamoDbClient.create();

    @Override
    public String handleRequest(Map<String,Object> input, Context ctx) {
        String username = (String) input.get("username");
        int score = ((Number) input.get("score")).intValue();

        db.putItem(PutItemRequest.builder()
                .tableName(System.getenv("SCORES_TABLE"))
                .item(Map.of(
                        "scoreId", AttributeValue.fromS(UUID.randomUUID().toString()),
                        "username", AttributeValue.fromS(username),
                        "score", AttributeValue.fromN(Integer.toString(score)),
                        "timestamp", AttributeValue.fromS(Instant.now().toString())
                )).build());

        return "Score submitted!";
    }
}
