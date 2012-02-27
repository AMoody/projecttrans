/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jprojecttranslator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
/**
 *
 * @author arth
 */
public class database {
    public Connection conn;
    private String strSQL;
    private Statement st;
    /** Default constructor
     * 
     */
    public database() {
        // Create a database
        st = null;
        try {
            Class.forName ("org.hsqldb.jdbcDriver");
            conn = DriverManager.getConnection("jdbc:hsqldb:mem:internal","sa","");
            st = conn.createStatement();
            ResultSet rs = null;
            strSQL = "SET LOGSIZE 5;";
            int i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL);
            }
            // Create the SOURCE_INDEX table
            strSQL = "CREATE TABLE PUBLIC.SOURCE_INDEX (intIndex INTEGER NOT NULL," +
                    "strType CHAR(4), strURI CHAR(256), strUMID CHAR(64), intLength BIGINT NOT NULL, strName CHAR(256), intFileOffset BIGINT NOT NULL, intTimeCodeOffset BIGINT NOT NULL, strSourceFile CHAR(256), strDestFileName CHAR(256)," +
                    "PRIMARY KEY (intIndex));";
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL);
            }
            // Create the EVENT_LIST table
            strSQL = "CREATE TABLE PUBLIC.EVENT_LIST (intIndex INTEGER NOT NULL," +
                    "strType CHAR(16), strRef CHAR(4), intSourceIndex INTEGER NOT NULL," +
                    "strTrackMap CHAR(16), intSourceIn BIGINT NOT NULL, intDestIn BIGINT NOT NULL, intDestOut BIGINT NOT NULL," +
                    "strRemark CHAR(512), strFadeType CHAR(16)," +
                    "PRIMARY KEY (intIndex));";
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL);
            }
            // Create the FADER_LIST table
            strSQL = "CREATE TABLE PUBLIC.FADER_LIST (intTrack INTEGER NOT NULL, intTime BIGINT NOT NULL, strLevel CHAR(16)" +
                    ");";
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL);
            }
            // Create the PROJECT table
            strSQL = "CREATE TABLE PUBLIC.PROJECT (intIndex INTEGER NOT NULL," +
                    "strTitle CHAR(256), strNotes CHAR(512), dtsCreated DATETIME, strOriginator CHAR(512), strClientData CHAR(512), " +
                    "PRIMARY KEY (intIndex));";
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL);
            }
            // Create the VERSION table
            strSQL = "CREATE TABLE PUBLIC.VERSION (strID CHAR(64), strUID CHAR(64), strADLVersion CHAR(64), strCreator CHAR(64), strCreatorVersion CHAR(64) " +
                    ");";
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL);
            }
            // Create the SYSTEM table
            strSQL = "CREATE TABLE PUBLIC.SYSTEM (intIndex INTEGER NOT NULL," +
                    "intSourceOffset INTEGER NOT NULL, intBitDepth INTEGER NOT NULL, strAudioCodec CHAR(64), intXFadeLength INTEGER NOT NULL, strGain CHAR(64) , " +
                    "PRIMARY KEY (intIndex));";
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL);
            }
        } catch (java.lang.ClassNotFoundException e) {
            System.out.println("Exception " + e.toString());
            System.exit(0);
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL);
            System.exit(0);
        }

    }
    /*
     * This retirns a statement so the database can be accessed
     */
    public Connection getConnection() {
        return conn;
    }
    /**
    * This returns an html string which contains the contents of a table in the database
    * @param setTable This is the table
    * @return This is the html string to send to the browser
    */
    public String getTableData(String setTable) {
        String strSQL = "";
        Statement st;
        try {
            String msg = "";
            strSQL = "SELECT * FROM PUBLIC."+setTable+";";
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(strSQL);
            ResultSetMetaData rsmd = rs.getMetaData();

            int intColumns = rsmd.getColumnCount();
            msg = "<table><tr>";
            for (int i=1;i<intColumns+1;i++) {
                msg = msg + "<th>" + rsmd.getColumnName(i)+ "</th>\n";
            }
            msg = msg + "</tr>";
            while (rs.next()) {
                msg = msg + "<tr>";
                for (int i=1;i<intColumns+1;i++) {
                    msg = msg + "<td>" + rs.getString(i)+ "</td>\n";
                }
                msg = msg + "</tr>";
//                msg = msg + "<td>" + rs.getString(1)+ "</td>\n";
            }
            msg = msg + "</table>";
            st.close();
            return msg;
        } catch (Exception e) {
            System.out.println("Error on SQL " + strSQL);
            return "";
        }
    }    
    
}
