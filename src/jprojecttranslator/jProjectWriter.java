/*
 * This is a generic project writer which should be extended to read
 * specific types of project
 */
package jprojecttranslator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.channels.FileChannel;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import wavprocessor.WAVProcessor;


/**
 *
 * @author arth
 */
public class jProjectWriter extends Observable implements Observer{
    protected boolean bRunning = false;
    protected database ourDatabase;
    protected List lWAVProcessors;
    protected String strSQL;
    protected Statement st;
    protected Connection conn;
    protected WAVProcessor tempWAVProc;
    protected File fDestFile;
    protected File fDestFolder;
    protected long lUsableDiskSpace;
    protected long lRequiredDiskSpace;
    private long lByteWriteCounter = 0;
    private long lastActivity;
    private String strCurrentUMID = "";
    public static DateTimeFormatter fmtSQL = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
    // This is a holder for the main class so we can access it's methods
    protected jProjectTranslator oProjectTranslator;
    
    /* This is used to test to see if the object is still running
     * 
     */
    public boolean getRunning() {
        return bRunning;
    } 
    /* This method receives updates from objects which are observed such as the WAVProcessor
     * 
     */
    public void update(Observable o, Object arg) {
        // One of the observed objects has changed
        if (o instanceof WAVProcessor) {
            // Simply pass this on to the jProjectTranslator
//            System.out.println("Project Writer notified of bytes written " + ((WAVProcessor)o).getLByteWriteCounter());
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
    public boolean save (database setDatabase, List setWAVProcessors, File setDestFile, jProjectTranslator setParent) {
        oProjectTranslator = setParent;
        ourDatabase = setDatabase;
        try {
            conn = ourDatabase.getConnection();
            st = conn.createStatement();                
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + e.toString());
            return false;
        }
        lWAVProcessors = setWAVProcessors;
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
                oProjectTranslator.writeStringToPanel(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectWriter.UnableToWriteFile"));
                oProjectTranslator.writeStringToPanel(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectWriter.DiskSpaceRequired") + oProjectTranslator.humanReadableByteCount(lRequiredDiskSpace,false));
                oProjectTranslator.writeStringToPanel(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectWriter.DiskSpaceAvailable") + oProjectTranslator.humanReadableByteCount(lUsableDiskSpace,false));
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
    /**
     * getLByteWriteCounter
     * @return A long showing the number of bytes which have been or are just about to be written to the destination file.
     */
    public long getLByteWriteCounter() {
        return lByteWriteCounter;
    }
    public String getCurrentUMID () {
        return strCurrentUMID;
    }
    /**
     * Find the time when the last file write occurred.
     * @return  The date/time in ms since the epoch.
     */
    public long getLastActivity(){
        return lastActivity;
    } 
    /* This method fills in the URI column in the SOURCE_INDEX table
     * 
     */
    protected boolean writeURIs () {

        int intSourceIndex;
        String strName;
        int i;
        try {
            strSQL = "SELECT intIndex, strDestFileName FROM PUBLIC.SOURCE_INDEX ORDER BY intIndex;";
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(strSQL);
            while (rs.next()) {
                intSourceIndex = rs.getInt(1);
                strName = URLDecoder.decode(rs.getString(2), "UTF-8");
                File fTemp = new File(fDestFolder.toString(),strName);
                String strTemp = fTemp.toString();
                strTemp = strTemp.replaceAll("\\\\", "/");
                if (!strTemp.startsWith("/")) {
                    strTemp = "/" + strTemp;
                }
                URI uriTemp = new URI("file","localhost",strTemp,null);
                // The new URI function will URL encode the string.
                String strURI = "URL:" + uriTemp.toString();
                // URL encode this string to keep bad characters from the database
                strURI = URLEncoder.encode(strURI, "UTF-8");
                strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET strURI = \'" + strURI + "\' WHERE intIndex = " + intSourceIndex + ";";
                i = st.executeUpdate(strSQL);
                if (i == -1) {
                    System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                }
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error caught on SQL " + strSQL + e.toString());
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("Error on decoding at " + strSQL + e.toString());
        } catch (java.net.URISyntaxException e) {
                    System.out.println("URI encoding exception  " + e.toString());
                }
        
        return true;
    } 
    protected boolean extractM4AFiles (File fSetDestFolder) {
        String strURI, strUMID;
        File fDestFolder = fSetDestFolder;
        strSQL = "SELECT intAudioOffset FROM PUBLIC.PROJECT;";
        try {
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(strSQL);
            rs.next();
            long lInitialAudioOffset = rs.getLong(1);
            strSQL = "SELECT intIndex, strName, intFileOffset, strSourceFile, intFileSize, strURI, strUMID FROM PUBLIC.SOURCE_INDEX WHERE intVCSInProject = 1 AND intCopied = 0 AND strType = \'m4a\' ORDER BY intIndex;";
            rs = st.executeQuery(strSQL);
            while (rs.next()) {
               // Get the raw URI string
               strURI = rs.getString(6);
               // It has been URL encoded to trap nasty characters from the database
               strURI = URLDecoder.decode(strURI, "UTF-8");
               strUMID = rs.getString(7);
               strUMID = URLDecoder.decode(strUMID, "UTF-8");
    //                System.out.println("First URI decode " + strURI);
               // Strip off the leading URL: if it exists
               if (strURI.startsWith("URL:")) {
                   strURI = strURI.substring(4, strURI.length());
               }
               // Make it in to a URI
               URI uriTemp = new URI(strURI);
               // Use the getPath() method
               strURI = URLDecoder.decode(uriTemp.getPath(), "UTF-8");
    //                System.out.println("Second URI decode " + strURI);
               // Decode the path and make it in to a file
               fDestFile = new File(strURI);  
//               String strNewFolder = fDestFile.getParent() + "/WRONG_FORMAT";
               String strFileName = fDestFile.getName();
//               File fNewFolder = new File(strNewFolder);
               if (fDestFolder.exists()) {
                   fDestFile = new File(fDestFolder,strFileName);
               } else {
                   if (fDestFolder.mkdir()) {
                       fDestFile = new File(fDestFolder,strFileName);
                   } else {
                       System.out.println("Failed to create subfolder for M4A files " + fDestFolder);
                   }
               } 
               File fSourceFile = new File(URLDecoder.decode(rs.getString(4)));

               extractCompressedFile(fSourceFile,fDestFile,lInitialAudioOffset + rs.getLong(3),lInitialAudioOffset + rs.getLong(3) + rs.getLong(5),strUMID);
               System.out.println("Writing M4A file to " + fDestFile);

            }            
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        } catch (UnsupportedEncodingException e) {
            System.out.println("UnsupportedEncodingException " + e.toString());
        } catch (URISyntaxException e) {
            Logger.getLogger("URISyntaxException " + e.toString());
        }
        return true;
    }    
    protected boolean extractMP3Files (File fSetDestFolder) {
        String strURI, strUMID;
        File fDestFolder = fSetDestFolder;
        strSQL = "SELECT intAudioOffset FROM PUBLIC.PROJECT;";
        try {
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(strSQL);
            rs.next();
            long lInitialAudioOffset = rs.getLong(1);
            strSQL = "SELECT intIndex, strName, intFileOffset, strSourceFile, intFileSize, strURI, strUMID FROM PUBLIC.SOURCE_INDEX WHERE intVCSInProject = 1 AND intCopied = 0 AND strType = \'mp3\' ORDER BY intIndex;";
            rs = st.executeQuery(strSQL);
            while (rs.next()) {
               // Get the raw URI string
               strURI = rs.getString(6);
               // It has been URL encoded to trap nasty characters from the database
               strURI = URLDecoder.decode(strURI, "UTF-8");
               strUMID = rs.getString(7);
               strUMID = URLDecoder.decode(strUMID, "UTF-8");
    //                System.out.println("First URI decode " + strURI);
               // Strip off the leading URL: if it exists
               if (strURI.startsWith("URL:")) {
                   strURI = strURI.substring(4, strURI.length());
               }
               // Make it in to a URI
               URI uriTemp = new URI(strURI);
               // Use the getPath() method
               strURI = URLDecoder.decode(uriTemp.getPath(), "UTF-8");
    //                System.out.println("Second URI decode " + strURI);
               // Decode the path and make it in to a file
               fDestFile = new File(strURI);  
//               String strNewFolder = fDestFile.getParent() + "/WRONG_FORMAT";
               String strFileName = fDestFile.getName();
//               File fNewFolder = new File(strNewFolder);
               if (fDestFolder.exists()) {
                   fDestFile = new File(fDestFolder,strFileName);
               } else {
                   if (fDestFolder.mkdir()) {
                       fDestFile = new File(fDestFolder,strFileName);
                   } else {
                       System.out.println("Failed to create subfolder for mp3 files " + fDestFolder);
                   }
               } 
               File fSourceFile = new File(URLDecoder.decode(rs.getString(4)));

               extractCompressedFile(fSourceFile,fDestFile,lInitialAudioOffset + rs.getLong(3),lInitialAudioOffset + rs.getLong(3) + rs.getLong(5),strUMID);
               System.out.println("Writing mp3 file to " + fDestFile);

            }            
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        } catch (UnsupportedEncodingException e) {
            System.out.println("UnsupportedEncodingException " + e.toString());
        } catch (URISyntaxException e) {
            Logger.getLogger("URISyntaxException " + e.toString());
        }
        return true;
    }
    protected boolean extractCompressedFile(File setSrcFile, File setDestFile, long setSourceFileStart, long setSourceFileEnd, String setUmid) {
        FileInputStream inFile = null;
        FileOutputStream outFile = null;
        FileChannel inChannel, outChannel;
        File srcFile = setSrcFile;
        File destFile = setDestFile;
        long lSourcePointer = setSourceFileStart;
        long lSourceEnd = setSourceFileEnd;
        strCurrentUMID = setUmid;
        String tempDestName = destFile.getName().substring(0,(destFile.getName().length())-3) + "tmp";
        File tempDestFile = new File(destFile.getParent(),tempDestName);
        lByteWriteCounter = 0;
        try {
            if (tempDestFile.exists()) {
                tempDestFile.delete();
            }
            if (destFile.exists()) {
                destFile.delete();
            }
            outFile = new FileOutputStream(tempDestFile);
            outChannel = outFile.getChannel();
            inFile = new FileInputStream(srcFile);
            inChannel = inFile.getChannel();
            synchronized(jProjectTranslator.randomNumber) {
                while (lSourcePointer < lSourceEnd) {
                    if (lSourceEnd - lSourcePointer < 5000000) {
                        lByteWriteCounter += (lSourceEnd - lSourcePointer);
                        lSourcePointer += inChannel.transferTo(lSourcePointer, (lSourceEnd - lSourcePointer), outChannel);
                    } else {
                        lSourcePointer += inChannel.transferTo(lSourcePointer, 5000000, outChannel);
                        lByteWriteCounter += 5000000;
                    }
                    try {
                        Thread.sleep(jProjectTranslator.randomNumber.nextInt(100) + 100); // Sleep for about 0.2s
                    } catch (InterruptedException e) {
                        System.out.println("Sleep interrupted." );            
                    }
                    lastActivity = System.currentTimeMillis()/1000;
                    setChanged();
                    notifyObservers();
                }                    
            }
            outFile.close();
            // Rename the dest file extension so that it will be picked up by the next process
            tempDestFile.renameTo(destFile);
            inFile.close();
            System.gc();
            
        } catch (java.io.FileNotFoundException e) {
            System.out.println("FileNotFoundException while writing new file " + destFile.toString());
        } catch (IOException e) {
            System.out.println("IOException while writing new file " + destFile.toString());
        }
        
        
        return true;
    }
}
