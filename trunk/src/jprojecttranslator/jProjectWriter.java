/*
 * This is a generic project writer which should be extended to read
 * specific types of project
 */
package jprojecttranslator;

import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Observable;
import java.util.Vector;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 *
 * @author arth
 */
public class jProjectWriter extends Observable {
    protected boolean bRunning = false;
    protected database ourDatabase;
    protected List lBWFProcessors;
    protected String strSQL;
    protected Statement st;
    protected Connection conn;
    protected BWFProcessor tempBWFProc;
    protected File fDestFile;
    protected File fDestFolder;
    public static DateTimeFormatter fmtSQL = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
    
    
    /* This is used to test to see if the object is still running
     * 
     */
    public boolean getRunning() {
        return bRunning;
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
    public boolean save (database setDatabase, List setBWFProcessors, File setDestFile) {
        
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
}
