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

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        LambdaLogger logger = context.getLogger();

        int a = new Random().nextInt(10);
        int b = new Random().nextInt(10);

        Map<String, Object> question = new HashMap<>();
        question.put("question", a + " + " + b + " = ?");
        question.put("answer", a + b);

        logger.log("Generated question: " + a + " + " + b + "\n");

        try {
            String body = objectMapper.writeValueAsString(question);
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
