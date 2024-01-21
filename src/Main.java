import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        int[] user_id = Arrays.stream(System.getenv("TELEGRAM_BUDGETIK_BOT_USER_ID").split(",")).mapToInt(Integer::parseInt).toArray();
        new TelegramBot(System.getenv("TELEGRAM_BUDGETIK_BOT_TOKEN"), user_id).start();
    }
}
