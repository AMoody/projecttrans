/*
 * This is a generic project reader which should be extended to read
 * specific types of project
 */
package jprojecttranslator;
import java.util.Observable;
import java.util.*;
import java.io.*;
import java.sql.Connection;
import java.sql.Statement;
import javax.swing.filechooser.*;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
/**
 *
 * @author arth
 */
public class jProjectReader extends Observable {
    protected database ourDatabase;
    protected List lBWFProcessors;
    protected File fSourceFile;
    protected File fSourceFolder;
    protected boolean bLoaded = false;
    protected boolean bRunning = false;
    public static DateTimeFormatter fmtSQL = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
    protected String strSQL;
    protected Statement st;
    protected Connection conn;
    protected BWFProcessor tempBWFProc;
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
    public boolean load (database setDatabase, List setBWFProcessors, File setSourceFile, jProjectTranslator setParent) {
        oProjectTranslator = setParent;
        ourDatabase = setDatabase;
        try {
            conn = ourDatabase.getConnection();
            st = conn.createStatement();                
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + e.toString());
            return false;
        }
        clearDatabase();
        lBWFProcessors = setBWFProcessors;
        lBWFProcessors.clear();
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
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
            return false;
        }
        return true;
    }
    


}
