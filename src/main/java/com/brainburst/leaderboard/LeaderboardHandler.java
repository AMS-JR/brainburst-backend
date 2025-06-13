package com.brainburst.leaderboard;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LeaderboardHandler implements RequestHandler<Void, List<Map<String,Object>>> {
    private final DynamoDbClient db = DynamoDbClient.create();

    @Override
    public List<Map<String,Object>> handleRequest(Void input, Context ctx) {
        ScanResponse resp = db.scan(ScanRequest.builder()
                .tableName(System.getenv("SCORES_TABLE"))
                .build());

        return resp.items().stream()
                .sorted((a, b) -> Integer.compare(
                        Integer.parseInt(b.get("score").n()),
                        Integer.parseInt(a.get("score").n())
                ))
                .limit(10)
                .map(item -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("username", item.get("username").s());
                    result.put("score", Integer.parseInt(item.get("score").n()));
                    result.put("timestamp", item.get("timestamp").s());
                    return result;
                })
                .collect(Collectors.toList());
    }
}
