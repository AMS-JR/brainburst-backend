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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LeaderboardHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final DynamoDbClient db = DynamoDbClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context ctx) {
        LambdaLogger logger = ctx.getLogger();

        try {
            logger.log("Fetching leaderboard data...\n");

            ScanResponse resp = db.scan(ScanRequest.builder()
                    .tableName(System.getenv("SCORES_TABLE"))
                    .build());

            List<Map<String, Object>> topScores = resp.items().stream()
                    .sorted((a, b) -> Integer.compare(
                            Integer.parseInt(b.get("score").n()),
                            Integer.parseInt(a.get("score").n())
                    ))
                    .limit(10)
                    .map(item -> {
                        Map<String, Object> result = new HashMap<>();
                        result.put("user", item.get("user").s());
                        result.put("score", Integer.parseInt(item.get("score").n()));
                        result.put("timestamp", item.get("timestamp").s());
                        return result;
                    })
                    .collect(Collectors.toList());

            logger.log("Fetched " + topScores.size() + " leaderboard entries\n");

            String jsonBody = objectMapper.writeValueAsString(topScores);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody(jsonBody);
        } catch (Exception e) {
            logger.log("Failed to get leaderboard: " + e.getMessage() + "\n");
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\": \"Failed to get leaderboard\"}");
        }
    }
}
