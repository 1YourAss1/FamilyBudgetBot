import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BudgetDB {
    private static final String URL = "jdbc:sqlite:budget.db";
    private Connection conn = null;

    public boolean connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(URL);
            return true;
        } catch (SQLException | ClassNotFoundException e) {
            System.err.println(e.getMessage());
        }
        return false;
    }

    public void close() {
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }

    public Map<String, String> insertExpense(Map<String, String> expense, int user_id, String rawText) {
        try {
            int amount = Integer.parseInt(expense.get("amount"));
            Map<String, String> category = getCategory(expense.get("category"));
            String sql = "INSERT INTO expense (amount, user_id, created, category_codename, raw_text) " +
                    "VALUES (?, ?, ?, ?, ?);";
            PreparedStatement preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setInt(1, amount);
            preparedStatement.setInt(2, user_id);
            preparedStatement.setString(3,getDataString(LocalDateTime.now()));

            preparedStatement.setString(4, category.get("codename"));
            preparedStatement.setString(5, rawText);
            preparedStatement.executeUpdate();
            expense.put("amount", String.valueOf(amount));
            expense.put("category", category.get("name"));
            return expense;
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
        return null;
    }

    private String getDataString(LocalDateTime date) {
        String pattern = "yyyy-MM-dd HH:mm:ss";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return date.format(formatter);
    }

    private Map<String, String> getCategory(String categoryFromText) {
        Map<String, String> result = new HashMap<>();
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM category;");
            while (rs.next()) {
                List<String> rowAliases = new ArrayList<>();
                rowAliases.add(rs.getString("name"));
                rowAliases.addAll(List.of(rs.getString("aliases").split(", ")));
                if (rowAliases.stream().anyMatch(categoryFromText::equals)) {
                    result.put("codename", rs.getString("codename"));
                    result.put("name", rs.getString("name"));
                    return result;
                }
            }
            rs.close();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
        result.put("codename", "other");
        result.put("name", "прочее");
        return result;
    }

    public String getAllCategories() {
        StringBuilder result = new StringBuilder();
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM category;");
            while (rs.next()) {
                result
                        .append("* ")
                        .append(rs.getString("name"))
                        .append(" (")
                        .append(rs.getString("aliases"))
                        .append((rs.getString("aliases").isEmpty()) ? "" : ", ")
                        .append(rs.getString("codename"))
                        .append(")")
                        .append("\n");
            }
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
        return result.toString();
    }

    public String getLastExpenses() {
        StringBuilder result = new StringBuilder();
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "SELECT e.id, e.amount, c.name " +
                    "FROM expense e LEFT JOIN category c " +
                    "ON c.codename=e.category_codename " +
                    "ORDER BY created DESC LIMIT 10;"
            );
            while (rs.next()) {
                result
                        .append("- ")
                        .append(rs.getString("amount"))
                        .append(" руб. на ")
                        .append(rs.getString("name"))
                        .append("\n");
            }
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
        return result.toString();
    }

    public String getTodayStatistic() {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT sum(amount) FROM expense WHERE date(created)=date('now', 'localtime');");
            if (rs.next()) {
                return String.valueOf(rs.getInt(1));
            }
            rs.close();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
        return "";
    }

    public String getMonthStatistic() {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT sum(amount) FROM expense WHERE date(created)>="+ LocalDate.now().withDayOfMonth(1) + ";");
            if (rs.next()) {
                return String.valueOf(rs.getInt(1));
            }
            rs.close();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
        return "";
    }

}


