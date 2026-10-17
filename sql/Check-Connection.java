import java.sql.*;
import java.util.Properties;

/** JDK 21 source launcher; SELECT only, never starts Spring runners or schedulers. */
class CheckConnection {
    public static void main(String[] args) throws Exception {
        String url = System.getenv("DB_URL");
        if (url == null || !url.startsWith("jdbc:sqlserver://")) {
            throw new IllegalArgumentException("Set DB_URL to the SQL Server JDBC URL first.");
        }
        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        Properties properties = new Properties();
        if (!"windows".equals(System.getenv("DB_AUTH"))) {
            for (String key : new String[]{"DB_USER", "DB_PASSWORD"}) {
                if (System.getenv(key) == null || System.getenv(key).isBlank()) {
                    throw new IllegalArgumentException("Missing " + key);
                }
            }
            properties.setProperty("user", System.getenv("DB_USER"));
            properties.setProperty("password", System.getenv("DB_PASSWORD"));
        }
        DriverManager.setLoginTimeout(5);
        try (Connection connection = DriverManager.getConnection(url, properties);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT CAST(SERVERPROPERTY('ServerName') AS nvarchar(128)), DB_NAME(), ORIGINAL_LOGIN()")) {
            while (rows.next()) {
                System.out.println("server=" + rows.getString(1) + "; database=" + rows.getString(2) + "; login=" + rows.getString(3));
            }
            System.out.println("JDBC=" + connection.getMetaData().getDriverVersion());
        }
    }
}
