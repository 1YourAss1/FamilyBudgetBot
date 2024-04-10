import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.plot.PiePlot3D;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.general.DefaultPieDataset;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FamilyBudgetBot extends TelegramLongPollingBot {
    Logger logger = Logger.getLogger(FamilyBudgetBot.class.getName());
    private final String botUsername;
    private final String[] user_id_array;
    private final BudgetDB budgetDB;

    FamilyBudgetBot(String botToken, String botUsername, String user_id_array) {
        super(botToken);
        this.botUsername = botUsername;
        this.user_id_array = user_id_array.split(",");

        logger.info("Connect to Budget Database");
        budgetDB = new BudgetDB();
        if (budgetDB.connect()) {
            logger.info("Connected to database");
        } else {
            budgetDB.close();
            logger.severe("Error: cannot connect to database");
            System.exit(0);
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String from_id = update.getMessage().getChatId().toString();
            if (Arrays.asList(user_id_array).contains(from_id)) {
                String text = update.getMessage().getText();
                logger.info("Handle message from " + from_id + ": " + text);
                switch (text.split(" ")[0]) {
                    case "/today" -> sendMessage(from_id, "Расходы сегодня: " + budgetDB.getTodaySum() + " руб.");
                    case "/month" -> sendMonthStatistic(from_id);
                    case "/categories" -> sendMessage(from_id, "Категории:\n" + budgetDB.getAllCategories());
                    case "/expenses" -> sendMessage(from_id, "Последние расходы:\n" + budgetDB.getLastExpenses());
                    case "/backup" -> sendBackup(from_id);
                    case String s when s.matches("/del\\d+") -> {
                        budgetDB.deleteExpense(Integer.parseInt(s.split("/del")[1]));
                        sendMessage(from_id,"Запись о расходе удалена");
                    }
                    default -> writeData(from_id, text);
                }
            } else {
                sendMessage(from_id, "Доступ запрещен");
            }
        }
    }

    private String getMonth() {
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("LLLL", Locale.forLanguageTag("ru"));
        return formatter.format(today).toUpperCase()
                + " (" + LocalDate.now().withDayOfMonth(1).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                + " - " +LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) +")";
    }

    private void writeData(String from_id, String text) {
        Map<String, String> expense = parseMessage(text);
        if (!expense.isEmpty()) {
            expense = budgetDB.insertExpense(expense, Integer.parseInt(from_id), text);
            if (expense != null) {
                for (String user_id : user_id_array) {
                    sendMessage(user_id,
                            "Добавлен расход: " + expense.get("amount") + " руб. на " + expense.get("category")
                                    + "\nРасходы сегодня: " + budgetDB.getTodaySum() + " руб.");
                }
            }
        } else {
            sendMessage(from_id, "Неверный формат. Пример нужного формата: \n1000 продукты");
        }
    }

    private Map<String, String> parseMessage(String text) {
        Map<String, String> result = new HashMap<>();
        Matcher matcher = Pattern.compile("(\\d+)(.*)").matcher(text);
        if (matcher.find() && Integer.parseInt(matcher.group(1).trim()) > 0) {
            result.put("amount", matcher.group(1).trim());
            if (!matcher.group(2).trim().isEmpty()) {
                result.put("category", matcher.group(2).trim());
            } else {
                result.put("category", "");
            }
            return result;
        }
        return result;
    }

    private void sendMessage(String chat_id, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chat_id);
        sendMessage.setText(text);

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    private void sendMonthStatistic(String chat_id) {
        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(chat_id);

        JSONArray statisticJSON = budgetDB.getMonthStatistic();
        DefaultPieDataset pieDataset = new DefaultPieDataset();
        StringBuilder statistic = new StringBuilder();
        for (int i = 0; i < statisticJSON.length(); i++) {
            JSONObject row = statisticJSON.getJSONObject(i);
            pieDataset.setValue(row.getString("name").toUpperCase(), row.getFloat("percentage"));

            statistic
                    .append(row.getString("name").toUpperCase()).append(": ")
                    .append(String.format("%.2f%%", row.getFloat("percentage"))).append(" ")
                    .append(String.format("(%d руб.)", row.getInt("sum")))
                    .append("\n");
        }

        String month = getMonth();
        JFreeChart monthStatisticPieChart = ChartFactory.createPieChart3D(month, pieDataset,false, false, false);
        monthStatisticPieChart.setBackgroundPaint(new Color(232, 232, 255));

        TextTitle title = monthStatisticPieChart.getTitle();
        title.setPaint(Color.DARK_GRAY);
        title.setFont(new Font("Arial", Font.BOLD, 26));

        PiePlot3D plot = (PiePlot3D) monthStatisticPieChart.getPlot();
        plot.setForegroundAlpha(0.6f);
        plot.setLabelGenerator(new StandardPieSectionLabelGenerator("{0}: {1}%"));
//        plot.setLabelGenerator(null);
        plot.setLabelBackgroundPaint(null);
        plot.setBackgroundPaint(null);
        plot.setOutlineVisible(false);

        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(monthStatisticPieChart.createBufferedImage(600, 400), "png", os);
            sendPhoto.setPhoto(new InputFile(new ByteArrayInputStream(os.toByteArray()), "test.png"));
            sendPhoto.setCaption("Расходы за " + month + ": " + budgetDB.getMonthSum() + " руб." +
                    "\nСтатистика:\n" + statistic);
            execute(sendPhoto);
        } catch (IOException | TelegramApiException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }


    }

    private void sendBackup(String chat_id) {
        File backup = backupDB();
        if (backup != null) {
            sendDocument(chat_id, backup);
        } else {
            sendMessage(chat_id, "Ошибка создания резервной копии базы данных!");
        }

    }

    private File backupDB() {
        // Check backup folder
        File backupDir = new File("backup");
        if (!backupDir.exists()) {
            if (!backupDir.mkdir()) {
                return null;
            }
        }
        // Create database backup
        Path source = Paths.get("budget.db");
        Path target = Paths.get(backupDir.getPath()
                + File.separator
                + "budget.db_"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        try {
            Files.copy(source, target);
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return target.toFile();
    }

    private void sendDocument(String chat_id, File document) {
        SendDocument sendDocument = new SendDocument();
        sendDocument.setChatId(chat_id);
        sendDocument.setDocument(new InputFile(document));

        try {
            execute(sendDocument);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }

}
