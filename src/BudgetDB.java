import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BudgetDB {
    private static final String URL = "jdbc:sqlite:budget.db";
    private Connection conn = null;

    public boolean connect() {
        try {
            conn = DriverManager.getConnection(URL);
            return true;
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
        return false;
    }

    public void close() {
        try {
            conn.close();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }

    public void insertExpense(Map<String, String> expense, int user_id, String rawText) {
        try {
            String sql = "INSERT INTO expense (amount, user_id, created, category_codename, raw_text) " +
                    "VALUES (?, ?, ?, ?, ?);";
            PreparedStatement preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setInt(1, Integer.parseInt(expense.get("amount")));
            preparedStatement.setInt(2, user_id);
            preparedStatement.setDate(3, new Date(System.currentTimeMillis()));
            preparedStatement.setString(4, getCategory(expense.get("category")));
            preparedStatement.setString(5, rawText);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
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

}


