package com.brainburst.game;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class GameHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Random random = new Random();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        LambdaLogger logger = context.getLogger();

        // Default values
        String operation = "addition";
        String level = "easy";

        // Get query params
        Map<String, String> queryParams = event.getQueryStringParameters();
        if (queryParams != null) {
            operation = queryParams.getOrDefault("operation", operation).toLowerCase();
            level = queryParams.getOrDefault("level", level).toLowerCase();
        }

        int min, max;
        switch (level) {
            case "medium":
                min = 11; max = 100; break;
            case "hard":
                min = 101; max = 999; break;
            default:
                level = "easy"; // fallback
                min = 0; max = 10;
        }

        int a = random.nextInt(max - min + 1) + min;
        int b = random.nextInt(max - min + 1) + min;

        int answer = 0;
        String question;

        switch (operation) {
            case "subtraction":
                // Ensure non-negative result for easier questions
                if (a < b) { int temp = a; a = b; b = temp; }
                question = a + " - " + b + " = ?";
                answer = a - b;
                break;

            case "multiplication":
                question = a + " × " + b + " = ?";
                answer = a * b;
                break;

            case "division":
                // Prevent divide-by-zero and ensure integer division
                b = random.nextInt(max - min) + min;
                answer = random.nextInt(10) + 1; // small quotient for simplicity
                a = b * answer;
                question = a + " ÷ " + b + " = ?";
                break;

            case "addition":
            default:
                operation = "addition";
                question = a + " + " + b + " = ?";
                answer = a + b;
                break;
        }

        logger.log("Generated question: " + question + " Answer: " + answer + "\n");

        try {
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("question", question);
            responseMap.put("answer", answer);
            responseMap.put("operation", operation);
            responseMap.put("level", level);

            String body = objectMapper.writeValueAsString(responseMap);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody(body);
        } catch (Exception e) {
            logger.log("Failed to generate question: " + e.getMessage() + "\n");
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\": \"Failed to generate question\"}");
        }
    }
}
