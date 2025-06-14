package com.brainburst.score;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.brainburst.datasource.DataSourceHandler;
import com.brainburst.sns.SnsHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class SubmitScoreHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final SnsHandler snsHandler = SnsHandler.getInstance();
    private final DataSourceHandler dataSourceHandler = DataSourceHandler.getInstance();
    private final ObjectMapper objectMapper = new ObjectMapper();

    static {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
    }

    private static final Logger logger = LoggerFactory.getLogger(SubmitScoreHandler.class);


    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context ctx) {

        ctx.getLogger().log("=== TEST LOG ===");
        System.out.println("=== SYSTEM OUT LOG ===");
        ctx.getLogger().log("Error: " + "This is a test ERROR log");
        try {
            ctx.getLogger().log("Received score submission: " + event.getBody());

            Map<String, Object> input = objectMapper.readValue(event.getBody(), Map.class);
            String username = (String) input.get("user");
            int score = ((Number) input.get("score")).intValue();
            String email = (String) input.get("email");

            if (username == null || email == null || score < 0) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(Map.of("Content-Type", "application/json"))
                        .withBody("{\"error\": \"Missing or invalid user, email, or score.\"}");
            }


            String scoreId = dataSourceHandler.insertScore(
                    username,
                    score
            );


            if (dataSourceHandler.isInTop10(scoreId)) {

                String message = String.format("Congratulations, %s! Your score of %d has made it to the top 10 on the leaderboard!",
                        username, score);
                snsHandler.publishSns(message, email);

            }

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody("{\"message\": \"Score submitted!\"}");

        } catch (Exception e) {
            ctx.getLogger().log("Error: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody("{\"error\": \"Failed to submit score.\"}");
        }
    }
}
