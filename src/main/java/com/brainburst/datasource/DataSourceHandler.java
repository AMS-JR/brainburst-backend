package com.brainburst.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final DynamoDbClient db;
    private final String tableName;
    private final Logger logger = LoggerFactory.getLogger(DataSourceHandler.class);

    private DataSourceHandler() {
        try {
            db = DynamoDbClient.create();
            tableName = Objects.requireNonNull(
                    System.getenv("SCORES_TABLE"),
                    "SCORES_TABLE environment variable is missing"
            );
        } catch (Exception e) {
            throw new IllegalStateException("DatabaseHandler initialization failed", e);
        }
    }

    public static DataSourceHandler getInstance() {
        return instance;
    }

    public String insertScore(String username, int score) {
        String scoreId = UUID.randomUUID().toString();
        try {
            db.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(Map.of(
                            "scoreId", AttributeValue.fromS(UUID.randomUUID().toString()),
                            "user", AttributeValue.fromS(username),
                            "score", AttributeValue.fromN(Integer.toString(score)),
                            "timestamp", AttributeValue.fromS(Instant.now().toString())
                    )).build());
            logger.info("Inserted score {} for user {}", score, username);

            return scoreId;
        } catch (Exception e) {
            logger.error("Failed to insert score for user: {}", username, e);
            throw e;
        }
    }

    public boolean isInTop10(String scoreId, String gameLevel) {
        try {
            ScanRequest scanRequest = ScanRequest.builder()
                    .tableName(tableName)
                    .projectionExpression("scoreId, score, level")
                    .build();

            List<Map<String, AttributeValue>> topScores = db.scan(scanRequest).items().stream()
                    .filter(row -> row.containsKey("level") && gameLevel.equals(row.get("level").s()))
                    .sorted((a, b) -> Integer.compare(
                            Integer.parseInt(b.get("score").n()),
                            Integer.parseInt(a.get("score").n())
                    ))
                    .limit(10)
                    .toList();

            return topScores.stream()
                    .anyMatch(item -> item.get("scoreId").s().equals(scoreId));
        } catch (Exception e) {
            logger.error("Failed to check top 10 for scoreId: {}", scoreId, e);
            throw e;
        }
    }


}

