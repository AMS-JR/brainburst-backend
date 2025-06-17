package com.brainburst.leaderboard;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.*;
import java.util.stream.Collectors;

public class LeaderboardHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final DynamoDbClient db = DynamoDbClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context ctx) {
        LambdaLogger logger = ctx.getLogger();

        try {
            logger.log("Fetching leaderboard data grouped by game level...\n");

            ScanResponse resp = db.scan(ScanRequest.builder()
                    .tableName(System.getenv("SCORES_TABLE"))
                    .build());

            // Group by gameLevel
            Map<String, List<Map<String, AttributeValue>>> grouped = resp.items().stream()
                    .filter(item -> item.containsKey("gameLevel"))
                    .collect(Collectors.groupingBy(item -> item.get("gameLevel").s()));

            // For each level, get top 10 sorted by score
            Map<String, List<Map<String, Object>>> result = new HashMap<>();

            for (String level : List.of("easy", "medium", "hard")) {
                List<Map<String, Object>> topScores = Optional.ofNullable(grouped.get(level))
                        .orElse(Collections.emptyList())
                        .stream()
                        .sorted((a, b) -> Integer.compare(
                                Integer.parseInt(b.get("score").n()),
                                Integer.parseInt(a.get("score").n())
                        ))
                        .limit(10)
                        .map(item -> {
                            Map<String, Object> entry = new HashMap<>();
                            entry.put("user", item.get("user").s());
                            entry.put("score", Integer.parseInt(item.get("score").n()));
                            entry.put("timestamp", item.get("timestamp").s());
                            return entry;
                        })
                        .collect(Collectors.toList());

                result.put(level, topScores);
            }

            logger.log("Grouped leaderboard entries by level\n");

            String jsonBody = objectMapper.writeValueAsString(result);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(Map.of(
                            "Content-Type", "application/json",
                            "Access-Control-Allow-Origin", "*",
                            "Access-Control-Allow-Credentials", "true"
                    ))
                    .withBody(jsonBody);

        } catch (Exception e) {
            logger.log("Failed to get leaderboard: " + e.getMessage() + "\n");
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(Map.of(
                            "Content-Type", "application/json",
                            "Access-Control-Allow-Origin", "*",
                            "Access-Control-Allow-Credentials", "true"
                    ))
                    .withBody("{\"error\": \"Failed to get leaderboard\"}");
        }
    }
}