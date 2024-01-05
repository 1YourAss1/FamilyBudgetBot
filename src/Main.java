import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Main {
    private static final String URL_API_TELEGRAM = "https://api.telegram.org/";
    public static void main(String[] args) {
        System.out.println("Start Family Budget Telegram Bot");
        System.out.println("-----------------------------------");

        String token = System.getenv("TELEGRAM_BUDGETIK_BOT_TOKEN");
        if (token == null || token.isEmpty()) {
            System.out.println("Error: TOKEN env not found. Define in linux with -> export TELEGRAM_BUDGETIK_BOT_TOKEN={telegram bot token}");
            System.exit(0);
        }

        String botToken = "bot" + token;


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL_API_TELEGRAM + botToken + "/getMe"))
                .build();

        try (HttpClient client = HttpClient.newHttpClient()){
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println(response.body());
        } catch (IOException | InterruptedException e) {
            System.out.println("ERROR: " + e.getMessage());
        }
    }
}
