package com.brainburst.auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.Map;

public class UserAuthHandler implements RequestHandler<Map<String,Object>, String> {
    private final DynamoDbClient db = DynamoDbClient.create();
    @Override
    public String handleRequest(Map<String,Object> input, Context ctx) {
        Map<String,String> claims = (Map<String,String>) input.get("claims");
        String username = claims.get("cognito:username");
        String email = claims.get("email");

        // Insert if not exists
        db.putItem(PutItemRequest.builder()
                .tableName(System.getenv("USERS_TABLE"))
                .item(Map.of(
                        "username", AttributeValue.fromS(username),
                        "email", AttributeValue.fromS(email),
                        "highestScore", AttributeValue.fromN("0"),
                        "totalGamesPlayed", AttributeValue.fromN("0")
                ))
                .conditionExpression("attribute_not_exists(username)")
                .build());

        return "OK";
    }
}
