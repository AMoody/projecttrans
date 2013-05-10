/*
 * This is a generic project writer which should be extended to read
 * specific types of project
 */
package jprojecttranslator;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 *
 * @author arth
 */
public class jProjectWriter extends Observable implements Observer{
    protected boolean bRunning = false;
    protected database ourDatabase;
    protected List lBWFProcessors;
    protected String strSQL;
    protected Statement st;
    protected Connection conn;
    protected BWFProcessor tempBWFProc;
    protected File fDestFile;
    protected File fDestFolder;
    protected long lUsableDiskSpace;
    protected long lRequiredDiskSpace;
    public static DateTimeFormatter fmtSQL = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
    // This is a holder for the main class so we can access it's methods
    protected jProjectTranslator oProjectTranslator;
    
    /* This is used to test to see if the object is still running
     * 
     */
    public boolean getRunning() {
        return bRunning;
    } 
    /* This method receives updates from objects which are observed such as the BWFProcessor
     * 
     */
    public void update(Observable o, Object arg) {
        // One of the observed objects has changed
        if (o instanceof BWFProcessor) {
            // Simply pass this on to the jProjectTranslator
//            System.out.println("Project Writer notified of bytes written " + ((BWFProcessor)o).getLByteWriteCounter());
            setChanged();
            notifyObservers(o);
            
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
     * This asks the object to save a project
     */
    public boolean save (database setDatabase, List setBWFProcessors, File setDestFile, jProjectTranslator setParent) {
        oProjectTranslator = setParent;
        ourDatabase = setDatabase;
        try {
            conn = ourDatabase.getConnection();
            st = conn.createStatement();                
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + e.toString());
            return false;
        }
        lBWFProcessors = setBWFProcessors;
        fDestFile = setDestFile;
        fDestFolder = fDestFile.getParentFile();
        lUsableDiskSpace = fDestFolder.getUsableSpace();
        try {
            strSQL = "SELECT SUM(intIndicatedFileSize) FROM PUBLIC.SOURCE_INDEX;";
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(strSQL);
            rs.next();
            lRequiredDiskSpace = rs.getLong(1);
            if (lRequiredDiskSpace > lUsableDiskSpace) {
                oProjectTranslator.writeStringToPanel("Unable to write file, insufficient free space on disk");
                oProjectTranslator.writeStringToPanel("Disk space required is " + oProjectTranslator.humanReadableByteCount(lRequiredDiskSpace,false));
                oProjectTranslator.writeStringToPanel("Disk space available is " + oProjectTranslator.humanReadableByteCount(lUsableDiskSpace,false));
                return false;
            } 
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
            return false;
        }
        new jProjectWriter.SimpleThread("jProjectReader").start();
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
        System.out.println("Writer thread running");
        return true;
    } 
        /**
     * This is used to get text information which is shown in the Help/About dialogue box.
     * @return The information text.
     */
    public String getInfoText() {
        return "";
    }
}
