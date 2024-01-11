import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class TelegramBot {
    private static final String URL_API_TELEGRAM = "https://api.telegram.org/bot";
    private static final int TIMEOUT = 10;

    public void start(String token) {
        System.out.println("Start Family Budget Telegram Bot");
        System.out.println("-----------------------------------");
        // Check token
        if (token == null || token.isEmpty()) {
            System.err.println("Error: TOKEN env not found. Define in linux with -> export TELEGRAM_BUDGETIK_BOT_TOKEN={telegram bot token}");
            System.exit(0);
        }
        // Check bot
        System.out.println("Check Family Budget Telegram Bot...");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL_API_TELEGRAM + token + "/getMe"))
                .GET()
                .build();
        try (HttpClient client = HttpClient.newHttpClient()){
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject jsonResponse = new JSONObject(response.body());
            if ((Boolean) jsonResponse.get("ok")) {
                System.out.println("Bot is working");
            } else {
                System.exit(0);
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("ERROR: " + e.getMessage());
        }
        System.out.println("-----------------------------------");
        // Start
        System.out.println("Start getting updates");
        int offset = 0;
        while (true) {
            try (HttpClient client = HttpClient.newHttpClient()) {
                request = HttpRequest.newBuilder()
                        .uri(URI.create(URL_API_TELEGRAM + token + "/getUpdates"
                                + "?offset=" + offset
                                + "&timeout=" + TIMEOUT))
                        .GET()
                        .build();
                System.out.println("Start request: " + request);
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                JSONObject jsonResponse = new JSONObject(response.body());
                JSONArray result = (JSONArray) jsonResponse.get("result");
                if (!result.isEmpty()) {
                    // Recalculate offset
                    offset = (int) ((JSONObject) result.get(0)).get("update_id") + 1;
                    // Check user id
                    JSONObject message = (JSONObject) ((JSONObject) result.get(0)).get("message");
                    System.out.println(message.get("text"));
                }
            } catch (IOException | InterruptedException e) {
                System.err.println("ERROR: " + e.getMessage());
            }
        }
    }
}
