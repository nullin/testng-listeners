package com.nullin.listener;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * A simple listener that starts up a server and prints out the current execution status.
 *
 * Uses jetty to start the server and handle the requests and uses a h2 in-memory data base
 * to store the results
 *
 * @author nullin
 */
public class SimpleExecStatusListener implements ITestListener {

    public final static int PORT = 10101;
    public final static String PORT_PROP = "execStatusPort";

    private String outputDir;

    public SimpleExecStatusListener() {
        try {
            Class.forName("org.h2.Driver");
        } catch (Exception e) {
            System.err.println("ERROR: Failed to load JDBC driver.");
            e.printStackTrace();
            return;
        }

        //setup database
        setupDb();
        //start server to serve up the results
        startServer();
    }

    /**
     * If user has specified a port, attempts to start server or fails. If no port is specified,
     * starts from {@link #PORT} port and tries the next 100 ports sequentially to try and start the
     * server on
     */
    private void startServer() {
        String userSpecifiedPort = System.getProperty(PORT_PROP);
        if (userSpecifiedPort != null) {
            try {
                startServer(Integer.valueOf(userSpecifiedPort));
            } catch (NumberFormatException ex) {
                System.err.println("ERROR: Failed to start server at port " + userSpecifiedPort);
                ex.printStackTrace();
            }
        } else {
            int port = PORT;
            while (port < PORT + 10) {
                if (startServer(port)) {
                    return; //done starting
                }
                port++; //try next port if we failed to start it above
            }
        }
    }

    /**
     * Starts the server on the specified port
     * @param port port to start the server on
     * @return true, if the server is successfully started, false otherwise
     */
    private boolean startServer(int port) {
        Server server = new Server(port);
        server.setHandler(new SimpleHandler());
        try {
            server.start();
            System.out.println("**********************************************************************");
            System.out.println("*");
            System.out.println("* Started Simple Exec Status Listener's server on port " + port);
            System.out.println("*");
            System.out.println("**********************************************************************");
            return true;
        } catch (Exception e) {
            System.err.println("ERROR: Failed to start server at port " + port);
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Setups the data base and creates the table used to store test data
     */
    private void setupDb() {
        Connection conn = getConnection();
        if (conn == null) {
            System.err.println("Failed to setup db");
            return;
        }
        executeStatement(conn, "CREATE TABLE results (id VARCHAR, classname VARCHAR, " +
                "methodname VARCHAR, exception VARCHAR, status INTEGER)");
    }

    /**
     * Executes the specified sql stmt
     * @param conn connection to database
     * @param query statement to execute
     */
    private void executeStatement(Connection conn, String query) {
        try {
            Statement stmt = conn.createStatement();
            stmt.execute(query);
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private Connection getConnection() {
        try {
            //memory only database which doesn't close on closing the last connection
            return DriverManager.getConnection("jdbc:h2:mem:resultDb;DB_CLOSE_DELAY=-1", "sa", "");
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void onTestStart(ITestResult result) {
        insert(result);
    }

    private void insert(ITestResult result) {
        String classname = result.getTestClass().getName();
        String methodname = result.getMethod().getMethodName();
        String id = classname + "#" + methodname;
        Object[] params = result.getParameters();
        if (params != null && params.length > 0) {
            id += "(" + params[0] + ")";
        }
        int status = result.getStatus();
        Throwable throwable = result.getThrowable();
        String exception = throwable == null ? null : throwable.toString();

        Connection conn = getConnection();
        if (conn == null) {
            return;
        }
        String sql = "INSERT INTO results(id, classname, methodname, exception, status) VALUES (?, ?, ?, ?, ?)";
        try {
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, id);
            stmt.setString(2, classname);
            stmt.setString(3, methodname);
            stmt.setString(4, exception);
            stmt.setInt(5, status);
            executeStatement(conn, stmt);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void update(ITestResult result) {
        String classname = result.getTestClass().getName();
        String methodname = result.getMethod().getMethodName();
        String id = classname + "#" + methodname;
        Object[] params = result.getParameters();
        if (params != null && params.length > 0) {
            id += "(" + params[0] + ")";
        }
        int status = result.getStatus();
        Throwable throwable = result.getThrowable();
        String exception = throwable == null ? null : throwable.toString();

        Connection conn = getConnection();
        if (conn == null) {
            return;
        }
        String sql = "UPDATE results SET status = ?, exception = ? WHERE id = ?";
        try {
            PreparedStatement stmt = conn.prepareStatement(sql);

            stmt.setInt(1, status);
            stmt.setString(2, exception);
            stmt.setString(3, id);
            executeStatement(conn, stmt);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void executeStatement(Connection conn, PreparedStatement stmt) {
        try {
            stmt.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private String getCurrentResults(Connection c) {
        StringBuffer buffer = new StringBuffer("<table>" +
                "<tr><th>ID</th><th>ClassName</th><th>MethodName</th><th>Exception (if any)</th><th>Status</th></tr>");
        try {
            PreparedStatement ps = c.prepareStatement("SELECT * FROM results");
            ResultSet rs = ps.executeQuery();
            if (rs != null) {
                while (rs.next()) {
                    String id = rs.getString(1);
                    String classname = rs.getString(2);
                    String methodName = rs.getString(3);
                    String exception = rs.getString(4);
                    int status = rs.getInt(5);
                    //System.out.println(String.format("%s,%s,%s,%s,%s", id, classname, methodName, exception, status));
                    String color = getColor(status);
                    buffer.append(String.format("<tr bgcolor=\"%s\">" +
                                    "<td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>",
                            color, id, classname, methodName, exception == null ? "" : exception, getStatus(status)));
                  }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                c.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        buffer.append("</table>");
        return buffer.toString();
    }

    /**
     * @param status TestNG specific status code
     * @return color code for the test status
     */
    private String getColor(int status) {
        switch (status) {
            case ITestResult.SUCCESS:
                return "#58E063";
            case ITestResult.FAILURE:
                return "#E05E5E";
            case ITestResult.SKIP:
                return "#E8E66F";
            case ITestResult.STARTED:
                return "#A9D2E8";
            case ITestResult.SUCCESS_PERCENTAGE_FAILURE:
                return "#EB9BAD";
            default:
                return "#CCCCCC";
        }
    }

    /**
     * @param status TestNG specific status code
     * @return string representation of the test status
     */
    private String getStatus(int status) {
        switch (status) {
            case ITestResult.SUCCESS:
                return "Pass";
            case ITestResult.FAILURE:
                return "Fail";
            case ITestResult.SKIP:
                return "Skip";
            case ITestResult.STARTED:
                return "Started";
            case ITestResult.SUCCESS_PERCENTAGE_FAILURE:
                return "Success w/ % Fail";
            default:
                return "Unknown";
        }
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        update(result);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        update(result);
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        update(result);
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
        //nothing here
    }

    @Override
    public void onStart(ITestContext context) {
        //logged for distinguishing tests if different instances of this listener are up and running
        outputDir = context.getOutputDirectory();
    }

    @Override
    public void onFinish(ITestContext context) {
        //nothing here
    }

    class SimpleHandler extends AbstractHandler {

        /**
         * Simply gets the current result status and writes it out as HTML
         *
         * @param s
         * @param baseRequest
         * @param request
         * @param response
         * @throws IOException
         * @throws ServletException
         */
        @Override
        public void handle(String s, Request baseRequest, HttpServletRequest request,
                           HttpServletResponse response) throws IOException, ServletException {
            response.setContentType("text/html;charset=utf-8");
            response.setStatus(HttpServletResponse.SC_OK);
            baseRequest.setHandled(true);
            StringBuffer sb = new StringBuffer(getCss());
            sb.append("<h1>TestNG Execution Status</h1>");
            sb.append(String.format("<h3>%s</h3>", outputDir));
            sb.append(getCurrentResults(getConnection()));
            response.getWriter().println(sb.toString());
        }
    }

    private String getCss() {
        return new StringBuffer()
                .append("<style>")
                .append("table {")
                .append("   width:100%;")
                .append("}")
                .append("table,th,tr,td {")
                .append("   border:1px solid grey;")
                .append("}")
                .append("td,th {")
                .append("   padding: 2px 5px;")
                .append("}")
                .append("body {")
                .append("   font-family: \"Tahoma\", Times, serif;")
                .append("   font-size: 80%;")
                .append("}")
                .append("h1 {")
                .append("   font-size: 2em;")
                .append("}")
                .append("th {")
                .append("   font-size: 1em;")
                .append("   font-style: bold;")
                .append("}")
                .append("</style>").toString();
    }
}
