package com.brainburst.game;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.util.Map;
import java.util.Random;

public class GameHandler implements RequestHandler<Map<String,Object>, Map<String,Object>> {
    @Override
    public Map<String,Object> handleRequest(Map<String,Object> input, Context ctx) {
        // Simple arithmetic questions logic
        int a = new Random().nextInt(10);
        int b = new Random().nextInt(10);
        return Map.of("question", a + " + " + b + " = ?", "answer", a + b);
    }
}
