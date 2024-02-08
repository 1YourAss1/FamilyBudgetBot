import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class TelegramBot {
    private static final String URL_API_TELEGRAM = "https://api.telegram.org/bot";
    private static final int TIMEOUT = 10;
    private final String token;
    private final int[] user_id_array;
    private BudgetDB budgetDB = null;

    TelegramBot(String token, int[] user_id) {
        this.token = token;
        this.user_id_array = user_id;
    }

    public void start() {
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
                System.out.println("Bot is active");
            } else {
                System.err.println("Bot is NOT active");
                System.exit(0);
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(0);
        }
        System.out.println("-----------------------------------");
        System.out.println("Connect to Budget Database");
        budgetDB = new BudgetDB();
        if (budgetDB.connect()) {
            System.out.println("Connected to database");
        } else {
            budgetDB.close();
            System.err.println("Error: cannot connect to database");
            System.exit(0);
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
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                JSONObject jsonResponse = new JSONObject(response.body());
                JSONArray result = (JSONArray) jsonResponse.get("result");
                if (!result.isEmpty()) {
                    // Recalculate offset
                    offset = (int) ((JSONObject) result.get(0)).get("update_id") + 1;
                    // Handle update
                    JSONObject update = (JSONObject) result.get(0);
                    if (update.has("message")) {
                        JSONObject message = (JSONObject) update.get("message");
                        handleMessage(message);
                    }
                }
            } catch (IOException | InterruptedException e) {
                System.err.println("ERROR: " + e.getMessage());
            }
        }
    }

    private void handleMessage(JSONObject message) {
        JSONObject from = (JSONObject) message.get("from");
        int from_id = from.getInt("id");
        if (IntStream.of(user_id_array).anyMatch(user_id -> user_id == from_id)) {
            String text = message.getString("text");
            System.out.println(from_id + ": " + text);
            switch (text.split(" ")[0]) {
                case "/today" -> sendMessage(from_id, "Расходы сегодня: " + budgetDB.getTodayStatistic() + " руб.");
                case "/month" -> sendMessage(from_id, "Расходы за месяц: " + budgetDB.getMonthStatistic() + " руб.");
                case "/categories" -> sendMessage(from_id, "Категории:\n" + budgetDB.getAllCategories());
                case "/expenses" -> sendMessage(from_id, "Последние расходы:\n" + budgetDB.getLastExpenses());
                default -> writeData(message.getString("text"), from_id);
            }
        } else {
            sendMessage(from_id, "Доступ запрещен");
        }
    }

    private void writeData(String text, int from_id) {
        Map<String, String> expense = parseMessage(text);
        if (expense != null) {
            expense = budgetDB.insertExpense(expense, from_id, text);
            if (expense != null) {
                for (int user_id : user_id_array) {
                    sendMessage(user_id, "Добавлен расход: " + expense.get("amount") + " руб. на " + expense.get("category"));
                }
            }
        } else {
            sendMessage(from_id, "Неверный формат. Пример нужного формата: \n1000 продукты");
        }
    }


    private Map<String, String> parseMessage(String text) {
        Map<String, String> result = new HashMap<>();
        Matcher matcher = Pattern.compile("([\\d ]+)(.*)").matcher(text);
        if (matcher.find() && Integer.parseInt(matcher.group(1).trim()) > 0 && !matcher.group(2).trim().isEmpty()) {
            result.put("amount", matcher.group(1).trim());
            result.put("category", matcher.group(2).trim());
            return result;
        }
        return null;
    }

    private void sendMessage(int chat_id, String text) {
        if (text == null || text.isEmpty()) {
            System.err.println("text in sendMessage is null or empty");
            return;
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL_API_TELEGRAM + token + "/sendMessage"
                        + "?chat_id=" + chat_id
                        + "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8)))
                .GET()
                .build();
        try (HttpClient client = HttpClient.newHttpClient()){
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject jsonResponse = new JSONObject(response.body());
            if (!(Boolean) jsonResponse.get("ok")) {
                System.err.println("ERROR: Cant send message: error_code "
                        + jsonResponse.get("error_code") + " - " + jsonResponse.get("description"));
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(0);
        }
    }
}
