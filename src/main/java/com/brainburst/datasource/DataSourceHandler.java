package com.brainburst.datasource;

import com.amazonaws.services.lambda.runtime.Context;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

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
    private final String usersTable;

    private DataSourceHandler() {
        db = DynamoDbClient.create();
        tableName = Objects.requireNonNull(System.getenv("SCORES_TABLE"), "SCORES_TABLE environment variable is missing");
        usersTable = Objects.requireNonNull(System.getenv("USERS_TABLE"), "USERS_TABLE environment variable is missing");
    }

    public static DataSourceHandler getInstance(Context context) {
        instance.context = context;
        return instance;
    }

    /**
     * Update this Rubbish method. I need the userId to be able to update score. Note to Self
     * @param username
     * @param score
     * @param level
     * @return
     */
    public String insertScore(String username, int score, String level) {
        String scoreId = UUID.randomUUID().toString();
        try {
            // Insert score into SCORES_TABLE
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

            Map<String, AttributeValue> key = Map.of("userId", AttributeValue.fromS(username));

            String updateExpression = "SET totalGamesPlayed = if_not_exists(totalGamesPlayed, :zero) + :inc " +
                    ", highestScore = if_not_exists(highestScore, :zero)";

            // Step 1: increment totalGamesPlayed
            db.updateItem(UpdateItemRequest.builder()
                    .tableName(usersTable)
                    .key(key)
                    .updateExpression("SET totalGamesPlayed = if_not_exists(totalGamesPlayed, :zero) + :inc")
                    .expressionAttributeValues(Map.of(
                            ":zero", AttributeValue.fromN("0"),
                            ":inc", AttributeValue.fromN("1")
                    ))
                    .build());

            // Step 2: conditionally update highestScore
            try {
                db.updateItem(UpdateItemRequest.builder()
                        .tableName(usersTable)
                        .key(key)
                        .updateExpression("SET highestScore = :newScore")
                        .conditionExpression("attribute_not_exists(highestScore) OR :newScore > highestScore")
                        .expressionAttributeValues(Map.of(
                                ":newScore", AttributeValue.fromN(Integer.toString(score))
                        ))
                        .build());
                context.getLogger().log(String.format("Updated highestScore to %d for user %s%n", score, username));
            } catch (Exception e) {
                // This means condition failed and highestScore was NOT updated (new score <= highestScore)
                context.getLogger().log("Highest score not updated because existing highestScore is greater or equal.\n");
            }

            return scoreId;

        } catch (Exception e) {
            context.getLogger().log(String.format("Failed to insert score or update user stats for user %s: %s%n", username, e.getMessage()));
//            throw e;
            return scoreId;  // remove this
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
