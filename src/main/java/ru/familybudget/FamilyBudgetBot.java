package ru.familybudget;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
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
import java.io.*;
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
    private static final String CATEGORY = "category";
    private static final String AMOUNT = "amount";
    private final String botUsername;
    private final String[] userIdArray;
    private final BudgetDB budgetDB;

    FamilyBudgetBot(String botToken, String botUsername, String userIdArray) {
        super(botToken);
        this.botUsername = botUsername;
        this.userIdArray = userIdArray.split(",");

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
            String fromId = update.getMessage().getChatId().toString();
            if (Arrays.asList(userIdArray).contains(fromId)) {
                String text = update.getMessage().getText();
                logger.info("Handle message from " + fromId + ": " + text);
                switch (text.split(" ")[0]) {
                    case "/today" -> sendMessage(fromId, "Расходы сегодня: " + budgetDB.getTodaySum() + " руб.");
                    case "/month" -> sendMonthStatistic(fromId);
                    case "/categories" -> sendMessage(fromId, "Категории:\n" + budgetDB.getAllCategories());
                    case "/expenses" -> sendMessage(fromId, "Последние расходы:\n" + budgetDB.getLastExpenses());
                    case "/backup" -> sendBackup(fromId);
                    case "/excel" -> sendExcel(fromId);
                    case String s when s.matches("/del\\d+") -> {
                        budgetDB.deleteExpense(Integer.parseInt(s.split("/del")[1]));
                        sendMessage(fromId, "Запись о расходе удалена");
                    }
                    default -> writeData(fromId, text);
                }
            } else {
                sendMessage(fromId, "Доступ запрещен");
            }
        }
    }

    public void sendDailyReminder() {
        for (String user_id : userIdArray) {
            sendMessage(user_id, "Заполни расходы за сегодня \uD83D\uDCB8");
        }
    }

    public void sendMonthStatisticToEveryone() {
        for (String user_id : userIdArray) {
            sendMonthStatistic(user_id);
        }
    }

    private String getMonth() {
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("LLLL", Locale.forLanguageTag("ru"));
        return formatter.format(today).toUpperCase()
                + " (" + LocalDate.now().withDayOfMonth(1).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                + " - " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + ")";
    }

    private void writeData(String fromId, String text) {
        Map<String, String> expense = parseMessage(text);
        if (!expense.isEmpty()) {
            expense = budgetDB.insertExpense(expense, Integer.parseInt(fromId), text);
            if (expense != null) {
                for (String user_id : userIdArray) {
                    sendMessage(user_id,
                            "Добавлен расход: " + expense.get("amount") + " руб. на " + expense.get(CATEGORY)
                                    + "\nРасходы сегодня: " + budgetDB.getTodaySum() + " руб.");
                }
            }
        } else {
            sendMessage(fromId, "Неверный формат. Пример нужного формата: \n1000 продукты");
        }
    }

    private Map<String, String> parseMessage(String text) {
        Map<String, String> result = new HashMap<>();
        Matcher matcher = Pattern.compile("(\\d+)(.*)").matcher(text);
        if (matcher.find() && Integer.parseInt(matcher.group(1).trim()) > 0) {
            result.put("amount", matcher.group(1).trim());
            if (!matcher.group(2).trim().isEmpty()) {
                result.put(CATEGORY, matcher.group(2).trim());
            } else {
                result.put(CATEGORY, "");
            }
            return result;
        }
        return result;
    }

    private void sendMessage(String chatId, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setParseMode("HTML");
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    private void sendMonthStatistic(String chatId) {
        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(chatId);

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
        JFreeChart monthStatisticPieChart = ChartFactory.createPieChart3D(month, pieDataset, false, false, false);
        monthStatisticPieChart.setBackgroundPaint(new Color(232, 232, 255));

        TextTitle title = monthStatisticPieChart.getTitle();
        title.setPaint(Color.DARK_GRAY);
        title.setFont(new Font("Arial", Font.BOLD, 26));

        PiePlot3D plot = (PiePlot3D) monthStatisticPieChart.getPlot();
        plot.setForegroundAlpha(0.6f);
        plot.setLabelGenerator(new StandardPieSectionLabelGenerator("{0}: {1}%"));
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

    private void sendBackup(String chatId) {
        File backup = backupDB();
        if (backup != null) {
            sendDocument(chatId, backup);
        } else {
            sendMessage(chatId, "Ошибка создания резервной копии базы данных!");
        }

    }

    private File backupDB() {
        // Check backup folder
        File backupDir = new File("backup");
        if (!backupDir.exists() && !backupDir.mkdir()) return null;

        // Create database backup
        Path source = Paths.get("budget.db");
        Path target = Paths.get(backupDir.getPath()
                + File.separator
                + "budget_%s.db".formatted(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));
        try {
            Files.copy(source, target);
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return target.toFile();
    }

    private void sendExcel(String fromId) {
        try (Workbook workbook = new HSSFWorkbook()) {

            Map<String, JSONArray> allExpensesMap = budgetDB.getAllExpenses();
            if (!allExpensesMap.isEmpty()) {
                for (Map.Entry<String, JSONArray> mounthsExpenses : allExpensesMap.entrySet()) {
                    Sheet sheet = workbook.createSheet(mounthsExpenses.getKey());
                    Row rowSheet = sheet.createRow(0);
                    sheet.setColumnWidth(0, 15 * 256);
                    sheet.setColumnWidth(1, 10 * 256);
                    rowSheet.createCell(0).setCellValue("Категория");
                    rowSheet.createCell(1).setCellValue("Затраты");
                    JSONArray rows = mounthsExpenses.getValue();
                    String category;
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject rowJSON = rows.getJSONObject(i);
                        rowSheet = sheet.createRow(i+1);
                        category = rowJSON.getString(CATEGORY);
                        rowSheet.createCell(0).setCellValue(category.substring(0, 1).toUpperCase() + category.substring(1));
                        rowSheet.createCell(1).setCellValue(rowJSON.getFloat(AMOUNT));
                    }
                    sheet.setAutoFilter(new CellRangeAddress(0, rows.length(), 0, 1));
                    sheet.createFreezePane(0, 1);
                }

                File excel = new File("budget_%s.xlsx".formatted(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));
                workbook.write(new FileOutputStream(excel));
                sendDocument(fromId, excel);
            }
        } catch (Exception e) {

        }

    }

    private void sendDocument(String chatId, File document) {
        SendDocument sendDocument = new SendDocument();
        sendDocument.setChatId(chatId);
        sendDocument.setDocument(new InputFile(document));

        try {
            execute(sendDocument);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }

}
