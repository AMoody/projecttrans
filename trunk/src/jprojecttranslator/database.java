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
            /** Create the TRACK table.
             * This is not used directly by the ADL but some project formats do include information about
             * the tracks in the EDL
             */
            strSQL = "CREATE TABLE PUBLIC.TRACKS (intIndex INTEGER NOT NULL," +
                    " strName CHAR(256), intChannels INTEGER, intChannelOffset INTEGER, strChannelMap CHAR(9)," +
                    "PRIMARY KEY (intIndex));";
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL);
            }
            /** Create the SOURCE_INDEX table
             * This contains information about the audio file sources in the project.
             * This information is used to generate the SOURCE_INDEX section of the ADL file.
             */
            strSQL = "CREATE TABLE PUBLIC.SOURCE_INDEX (intIndex INTEGER NOT NULL," +
                    "strType CHAR(4), strURI CHAR(256), strUMID CHAR(64), intLength BIGINT NOT NULL, strName CHAR(256),"
                    + " intFileOffset BIGINT NOT NULL, intTimeCodeOffset BIGINT NOT NULL, strSourceFile CHAR(256),"
                    + " strDestFileName CHAR(256), intCopied BIGINT DEFAULT 0, intIndicatedFileSize BIGINT DEFAULT 0,"
                    + " intSampleRate BIGINT DEFAULT 0, dDuration DOUBLE DEFAULT 0, intVCSInProject INT, intFileSize BIGINT," +
                    "PRIMARY KEY (intIndex));";
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL);
            }
            /** Create the EVENT_LIST table
             * This contains information about entries on the EDL tracks
             * intIndex This is the key for the table
             * strType  This always seems to be Cut
             * strRef   This always seems to be I
             * intSourceIndex   This refers to the entry in the SOUCE_INDEX table
             * strTrackMap      This is a string used in an ADL file, e.g. 1~2 3~4 means tracks 1 and 2 from the source file go to tracks 3 and 4 in the EDL. Could also be 23 23 etc for mono files.
             * intSourceIn      This is a long or bigint, it's the in point in the source file in samples.
             * intDestIn        This is a long or bigint, it's the in point in the EDL in samples.
             * intDestOut       This is a long or bigint, it's the out point in the EDL in samples.
             * strRemark        This is the name of the EDL entry
             * strInFade        This describes the in fade on the EDL, e.g. LIN _ _ _ or CURVE -2.70 -4.70 -13.23 etc
             * intInFade        This is the duration of the in fade in samples
             * strOutFade       This describes the out fade on the EDL, e.g. LIN _ _ _ or CURVE -2.70 -4.70 -13.23 etc
             * intOutFade       This is the duration of the out fade in samples
             * intRegionIndex   This is used by Ardour, every object has an index number which we need to keep
             * intLayer         This is used by Ardour, regions on an edl track can lie above or below each other
             * bOpaque          This is used by Ardour, if a region is opaque then regions underneath can not be heard unless the overlap is a crossfade
             * 
             */
            strSQL = "CREATE TABLE PUBLIC.EVENT_LIST (intIndex INTEGER NOT NULL," +
                    "strType CHAR(16), strRef CHAR(4), intSourceIndex INTEGER NOT NULL," +
                    "strTrackMap CHAR(16), intSourceIn BIGINT NOT NULL, intDestIn BIGINT NOT NULL, intDestOut BIGINT NOT NULL," +
                    "strRemark CHAR(512), strInFade CHAR(30), intInFade BIGINT,  strOutFade CHAR(30), intOutFade BIGINT, intRegionIndex INTEGER, "
                    + "intLayer INTEGER, bOpaque CHAR(1), " +
                    "PRIMARY KEY (intIndex));";
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL);
            }
            /** Create the FADER_LIST table
             * This contains information about the gain automation
             */
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
