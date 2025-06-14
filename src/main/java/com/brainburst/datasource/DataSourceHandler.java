package com.brainburst.datasource;

import com.amazonaws.services.lambda.runtime.Context;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;


public class DataSourceHandler {

    private static final DataSourceHandler instance = new DataSourceHandler();
    private Context context;
    private final DynamoDbClient db;
    private final String tableName;

    private DataSourceHandler() {
        db = DynamoDbClient.create();
        tableName = Objects.requireNonNull(System.getenv("SCORES_TABLE"), "SCORES_TABLE environment variable is missing");
    }

    public static DataSourceHandler getInstance(Context context) {
        instance.context = context;
        return instance;
    }

    public String insertScore(String username, int score, String level) {
        String scoreId = UUID.randomUUID().toString();
        try {
            db.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(Map.of(
                            "scoreId", AttributeValue.fromS(scoreId),
                            "user", AttributeValue.fromS(username),
                            "score", AttributeValue.fromN(Integer.toString(score)),
                            "gameLevel", AttributeValue.fromS(level),
                            "timestamp", AttributeValue.fromS(Instant.now().toString())
                    ))
                    .build());

            context.getLogger().log(String.format("Inserted score %d for user %s%n", score, username));
            return scoreId;

        } catch (Exception e) {
            context.getLogger().log(String.format("Failed to insert score for user %s: %s%n", username, e.getMessage()));
            throw e;
        }
    }

    public boolean isInTop10(String scoreId, String gameLevel) {
        try {
            ScanRequest scanRequest = ScanRequest.builder()
                    .tableName(tableName)
                    .projectionExpression("scoreId, score, gameLevel")
                    .build();

            List<Map<String, AttributeValue>> topScores = db.scan(scanRequest).items().stream()
                    .filter(row -> row.containsKey("gameLevel") && gameLevel.equals(row.get("gameLevel").s()))
                    .sorted((a, b) -> Integer.compare(
                            Integer.parseInt(b.get("score").n()),
                            Integer.parseInt(a.get("score").n())
                    ))
                    .limit(10)
                    .toList();

            return topScores.stream()
                    .anyMatch(item -> item.get("scoreId").s().equals(scoreId));
        } catch (Exception e) {
            context.getLogger().log(String.format("Failed to check top 10 for scoreId: %s. Error: %s%n", scoreId, e.getMessage()));
            return false;
        }
    }
}
