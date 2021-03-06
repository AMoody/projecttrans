/*
 * This is a generic project reader which should be extended to read
 * specific types of project
 */
package jprojecttranslator;
import java.util.Observable;
import java.util.*;
import java.io.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.swing.JOptionPane;
import javax.swing.filechooser.*;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import wavprocessor.WAVProcessor;
/**
 *
 * @author arth
 */
public class jProjectReader extends Observable {
    protected database ourDatabase;
    protected List lWAVProcessors;
    protected File fSourceFile;
    protected File fSourceFolder;
    protected boolean bLoaded = false;
    protected boolean bRunning = false;
    public static DateTimeFormatter fmtSQL = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
    protected String strSQL;
    protected Statement st;
    protected Connection conn;
    protected WAVProcessor tempWAVProc;
    protected int intSoundFilesLoaded = 0;
    protected static int intClipCounter = 1;
    protected int intSourceFileCount = 0;
    // This is a holder for the main class so we can access it's methods
    protected jProjectTranslator oProjectTranslator;
        

    
    /* This is used to test to see if the file has been loaded successfully
     * 
     */
    public boolean getLoaded() {
        return bLoaded;
    }
    /* This is used to test to see if the object is still running
     * 
     */
    public boolean getRunning() {
        return bRunning;
    }
    /* This gives a percentage of progress which is used to update the progress bar
     * 
     */
    public int getPercentProgress() {
        if (intSoundFilesLoaded < intSourceFileCount && intSourceFileCount > 0) {
            return 100*intSoundFilesLoaded/intSourceFileCount;

        } else {
            return 100;
        }
    }
    /**
     * Checks to see if this name already exists in the SOURCE_INDEX table
     * @param strName The name to be checked
     * @param st    A reference to the database in the form of an open statement
     */
    public static boolean getSourceNameExists (String strURI, Statement st) {
        String strSQL = "SELECT COUNT(*) FROM PUBLIC.SOURCE_INDEX WHERE strDestFileName = \'" + strURI +"\';";
        try {
            ResultSet rs = st.executeQuery(strSQL);
            rs.next();
            if (rs.getInt(1) > 0) {
                return true;
            } 
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        }
        return false;
    }
    /*
     * This returns a FileFilter which this class can read
     */
    public javax.swing.filechooser.FileFilter getFileFilter() {
        javax.swing.filechooser.FileFilter filter = new FileNameExtensionFilter("Text", "txt");
        return filter;
    }
    /*
     * This asks the object to load a project
     */
    public boolean load (database setDatabase, List setWAVProcessors, File setSourceFile, jProjectTranslator setParent) {
        oProjectTranslator = setParent;
        ourDatabase = setDatabase;
        try {
            conn = ourDatabase.getConnection();
            st = conn.createStatement();                
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + e.toString());
            return false;
        }
        // Reset these values back to the preferred values.
        jProjectTranslator.intProjectSampleRate = jProjectTranslator.intPreferredSampleRate;
        jProjectTranslator.dProjectFrameRate = jProjectTranslator.dPreferredFrameRate;
        jProjectTranslator.intProjectXfadeLength = jProjectTranslator.intPreferredXfadeLength;
        clearDatabase();
        lWAVProcessors = setWAVProcessors;
        lWAVProcessors.clear();
        fSourceFile = setSourceFile;
        fSourceFolder = fSourceFile.getParentFile();
        new SimpleThread("jProjectReader").start();
        return true;
    }
    class SimpleThread extends Thread {
        public SimpleThread(String str) {
            super(str);
        }
        public void run() {
            bRunning = true;
            processProject();
            // Do something
            bRunning = false;            
        }
    }
    protected boolean processProject() {
        System.out.println("Reader thread running");
        return true;
    }    
    protected boolean clearDatabase() {
        try {
            strSQL = "DELETE FROM PUBLIC.SOURCE_INDEX;";
            int i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
            strSQL = "DELETE FROM PUBLIC.EVENT_LIST;";
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
            strSQL = "DELETE FROM PUBLIC.FADER_LIST;";
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            } 
            strSQL = "DELETE FROM PUBLIC.FADER_LIST_R;";
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            } 
            strSQL = "DELETE FROM PUBLIC.FADER_LIST_T;";
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            } 
            strSQL = "DELETE FROM PUBLIC.PROJECT;";
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            } 
            strSQL = "DELETE FROM PUBLIC.VERSION;";
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }  
            strSQL = "DELETE FROM PUBLIC.SYSTEM;";
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            } 
            strSQL = "DELETE FROM PUBLIC.TRACKS;";
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            } 
            strSQL = "DELETE FROM PUBLIC.ARDOUR_SOURCES;";
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
            strSQL = "DELETE FROM PUBLIC.ARDOUR_TEMPO;";
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
            strSQL = "DELETE FROM PUBLIC.ARDOUR_TIME_SIGNATURE;";
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
            return false;
        }
        return true;
    }
    /**
     * This is used to get text information which is shown in the Help/About dialogue box.
     * @return The information text.
     */
    public String getInfoText() {
        return "";
    }
    public void sampleRateChange() {
        String msg = "The project which is loading is at sample rate " + jProjectTranslator.intProjectSampleRate + " but your preferred rate is "
                + jProjectTranslator.intPreferredSampleRate + "!\nDo you want to change your preferred sample rate?";  
        msg = java.text.MessageFormat.format( msg, new Object[] { msg } );  
        String title = "Warning";  
        int option = JOptionPane.showConfirmDialog( jProjectTranslator.ourWindow, msg, title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE );  
        if ( option == JOptionPane.YES_OPTION ) {  
             jProjectTranslator.intPreferredSampleRate = jProjectTranslator.intProjectSampleRate;
        }
    }
    public void frameRateChange() {
        String msg = "The project which is loading is at frame rate " + jProjectTranslator.dProjectFrameRate + " but your preferred rate is "
                + jProjectTranslator.dPreferredFrameRate + "!\nDo you want to change your preferred frame rate?";  
        msg = java.text.MessageFormat.format( msg, new Object[] { msg } );  
        String title = "Warning";  
        int option = JOptionPane.showConfirmDialog( jProjectTranslator.ourWindow, msg, title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE );  
        if ( option == JOptionPane.YES_OPTION ) {  
             jProjectTranslator.dPreferredFrameRate = jProjectTranslator.dProjectFrameRate;
        }
    }

}
