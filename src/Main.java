import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Main {
    public static void main(String[] args) throws TelegramApiException, IOException {
        var app = new FileInputStream("app.properties");
        var properties = new Properties();
        properties.load(app);

        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(new FamilyBudgetBot(
                properties.getProperty("bot.token"),
                properties.getProperty("bot.username"),
                properties.getProperty("bot.user_id_array")));
        }
}
