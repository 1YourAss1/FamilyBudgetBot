import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    public boolean insertExpense(Map<String, String> expense, int user_id, String rawText) {
        try {
            String sql = "INSERT INTO expense (amount, user_id, created, category_codename, raw_text) " +
                    "VALUES (?, ?, ?, ?, ?);";
            PreparedStatement preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setInt(1, Integer.parseInt(expense.get("amount")));
            preparedStatement.setInt(2, user_id);
            preparedStatement.setString(3,getDataString(LocalDateTime.now()));
            preparedStatement.setString(4, getCategory(expense.get("category")));
            preparedStatement.setString(5, rawText);
            preparedStatement.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
        return false;
    }

    private String getDataString(LocalDateTime date) {
        String pattern = "yyyy-MM-dd HH:mm:ss";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return date.format(formatter);
    }

    private String getCategory(String categoryFromText) {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM category;");
            while (rs.next()) {
                List<String> rowAliases = new ArrayList<>();
                rowAliases.add(rs.getString("name"));
                rowAliases.addAll(List.of(rs.getString("aliases").split(",")));
                if (rowAliases.stream().anyMatch(categoryFromText::equals)) {
                    return rs.getString("codename");
                }
            }
            rs.close();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
        return "other";
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
        return null;
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
        return null;
    }

}


