package com.brainburst.score;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.brainburst.datasource.DataSourceHandler;
import com.brainburst.notification.EmailNotificationHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class SubmitScoreHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {


    private final ObjectMapper objectMapper = new ObjectMapper();


    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context ctx) {

        ctx.getLogger().log("=== Received SubmitScore request ===\n");

        try {
            ctx.getLogger().log("Request Body: " + event.getBody() + "\n");

            Map<String, Object> input = objectMapper.readValue(event.getBody(), Map.class);
            String username = (String) input.get("user");
            int score = ((Number) input.get("score")).intValue();
            String gameLevel = (String) input.get("level");

            if (username == null || gameLevel == null || score < 0) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(Map.of(
                                "Content-Type", "application/json",
                                "Access-Control-Allow-Origin", "*",
                                "Access-Control-Allow-Credentials", "true"
                        ))
                        .withBody("{\"error\": \"Missing or invalid user, score, or level.\"}");
            }


            DataSourceHandler dataSourceHandler = DataSourceHandler.getInstance(ctx);

            String scoreId = dataSourceHandler.insertScore(username, score, gameLevel);

            if (dataSourceHandler.isInTop10(scoreId, gameLevel)) {
                EmailNotificationHandler emailHandler = EmailNotificationHandler.getInstance(ctx);
                String message = String.format(
                        "\uD83C\uDFC6 Congratulations, %s! Your score of %d has made it to the top 10 on the %s leaderboard!",
                        username, score, gameLevel
                );
                emailHandler.sendEmail(username, "Top 10 Leaderboard Notification", message);
            }

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(Map.of(
                            "Content-Type", "application/json",
                            "Access-Control-Allow-Origin", "*",
                            "Access-Control-Allow-Credentials", "true"
                    ))
                    .withBody("{\"message\": \"Score submitted successfully.\"}");

        } catch (Exception e) {
            ctx.getLogger().log("Error handling request: " + e.getMessage() + "\n");
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(Map.of(
                            "Content-Type", "application/json",
                            "Access-Control-Allow-Origin", "*",
                            "Access-Control-Allow-Credentials", "true"
                    ))
                    .withBody("{\"error\": \"Failed to submit score.\"}");
        }
    }
}
