/*
 * This is a quick and dirty bit of code to get the XML project data from a VCS Startrack
 * project in to a standard ADL file for subsequent import to a craft editor.
 */

package jprojecttranslator;
import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.io.*;
import java.util.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.dom4j.io.SAXReader;
import org.dom4j.DocumentException;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.CodingErrorAction;
import org.joda.time.*;
import org.joda.time.format.*;
import java.lang.Math.*;



import java.util.*;

/**
 *
 * @author scobeam
 */
public class Main {
    /** <p>This is a static random number generator for this class, all objects share the generator.<br>
     * As this is public, other objects can also use it.</p>
     */
    public static Random randomNumber = new Random();
    /** This is a project file from VCS with project data stored as XML. */
    private static String strSourceProjectFile;
    /** These are the fields associated with the database */
    protected static Connection conn;
    private static String strSQL;
    /** This is the xml document object which is used for loading and saving to an xml file.*/
    static Document xmlDocument = DocumentHelper.createDocument();
    /** This is an xml formatter object to make the xml file human readable.*/
    static OutputFormat xmlFormat = OutputFormat.createPrettyPrint();
    // 2011-11-08T16:55:34+00:00
    public static DateTimeFormatter fmtADLXML = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZZ");
    public static DateTimeFormatter fmtVCSXML = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSS");
    public static DateTimeFormatter fmtSQL = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
    public static int intSampleRate = 48000;
    public static double dFrameRate = 25;
//    public static double dFrameRate = 29.97;
    protected static int intClipCounter = 1;
    /** This is the xml document object which is used for writing the xml file.*/
    static Document xmlDestDocument = DocumentHelper.createDocument();
    /** This is the audio offset for the file
     *
     */
    protected static long lAudioOffset = 0;
    protected static long lInitialAudioOffset = 0;
    private static Vector vBWFProcs = new Vector(2);
    protected static int intSoundFiles = 0;


    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // The first argument given on the command line must be the source project file.
        if ((args.length > 0) && (args[0].length()>0)) {
            strSourceProjectFile = args[0];
            if (!(new File(strSourceProjectFile)).exists()) {
                // Settings file does not exist!
                System.out.println("Source project file not found...");
                System.exit(0);
            }
        } else {
            System.out.println("You must specify a Source project file from the command line.");
            System.exit(0);
        }
        // Create a database
        Statement st = null;
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
                    "strType CHAR(4), strURI CHAR(256), strUMID CHAR(64), intLength BIGINT NOT NULL, strName CHAR(256), intFileOffset BIGINT NOT NULL, intTimeCodeOffset BIGINT NOT NULL," +
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


        // Create the webserver
        int intHTTPPort = 8080;
        try {

            HTTPD oWebServer = new HTTPD(intHTTPPort);
            System.out.println("HTTP server created, listening on port "+ intHTTPPort +".");
        } catch (IOException ioe) {
            System.out.println("Failed to start HTTP server on port "+ intHTTPPort +" , " + ioe.toString() + ".");
        }
        // Read XML data from file.
        File fSourceFile = new File(strSourceProjectFile);
        File fSourceFolder = fSourceFile.getParentFile();
        File fDestADLFile = new File(fSourceFile.toString() + ".adl" );
        if (fSourceFolder.getUsableSpace() < (fSourceFile.length() * 1.1)) {
            System.out.println("There is insufficient space on the disk to convert this file.");
            System.exit(0);
        }
        String strXMLData = getXMLHeader(fSourceFile);
        // Load the XML data in to an XML document
        if (loadSettingsFile(strXMLData)) {
            System.out.println("XML source data loaded");
        } else {
            System.out.println("Failed to load XML source data");
            System.exit(0);
        }
        /**
         * The XML data from the source file has been loaded.
         * Next step is to look at the embedded audio files.
         * We need to do this at this stage to get the sample rate, this information is not included
         * in the VCS XML file
         */
        BWFProcessor tempBWFProc = new BWFProcessor();
        tempBWFProc.setSrcFile(fSourceFile);
        tempBWFProc.setMultipart(true); //
        if (tempBWFProc.readFile(lInitialAudioOffset,0) && tempBWFProc.getSampleRate() > 0) {
            intSampleRate = tempBWFProc.getSampleRate();
            System.out.println("Sample rate set to " + intSampleRate);
        }

//        System.out.println(tempBWFProc.setBextTitle("test title"));
//        tempBWFProc.setBextOriginatorRef("ref number here");
//        System.out.println("New title is " + tempBWFProc.getBextTitle() + " and new origref is " + tempBWFProc.getBextOriginatorRef());
        /** The next thing to do is read in the xml data from the file to the database
         *
         */
        Element xmlRoot = xmlDocument.getRootElement();
//        System.out.println("Root element is " + xmlRoot.getName());
        Element xmlGenerator = xmlRoot.element("Generator");
        String strCreator = xmlGenerator.elementText("Name");
        String strCreatorVersion = xmlGenerator.elementText("Version") + "-" + xmlGenerator.elementText("Build");
        String strADLVersion = "01.01.00";
        try {
            strCreator = URLEncoder.encode(strCreator, "UTF-8");
            strCreatorVersion = URLEncoder.encode(strCreatorVersion, "UTF-8");
            strADLVersion = URLEncoder.encode(strADLVersion, "UTF-8");
        } catch(java.io.UnsupportedEncodingException e) {

        }
        Element xmlCreator = xmlRoot.element("Creator");
        DateTime dtsCreated = fmtVCSXML.withZone(DateTimeZone.UTC).parseDateTime(xmlCreator.elementText("SystemTime"));
        String strNotes = "ComputerName=" + xmlCreator.elementText("ComputerName") + ";UserName="+ xmlCreator.elementText("UserName");
        String strTitle = fSourceFile.getName();
        int mid= strTitle.lastIndexOf(".");
        strTitle = strTitle.substring(0,mid);
        try {
            strNotes = URLEncoder.encode(strNotes, "UTF-8");
            strTitle = URLEncoder.encode(strTitle, "UTF-8");
        } catch(java.io.UnsupportedEncodingException e) {

        }
        String strCreated = fmtSQL.print(dtsCreated);


        // strID CHAR(64), strUID CHAR(64), strADLVersion CHAR(64), strCreator CHAR(64), strCreatorVersion CHAR(64)
        try {
            strSQL = "INSERT INTO PUBLIC.VERSION (strID, strUID, strADLVersion, strCreator, strCreatorVersion) VALUES (" +
                " \' \',\' \',\'" + strADLVersion + "\', \'" + strCreator + "\', \'" + strCreatorVersion + "\') ;";
            int i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
            strSQL = "INSERT INTO PUBLIC.PROJECT (intIndex, strTitle, strNotes, dtsCreated, strOriginator, strClientData) VALUES (" +
                "1, \'"+strTitle+" \', \'"+strNotes+" \',\'"+strCreated+"\',\' \', \' \') ;";
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        }
        // Loop through the items on the take board
        Element xmlTakeBoard = xmlRoot.element("TakeBoard");
        Element xmlTake;
        int intCounter = 1;
        for (Iterator i = xmlTakeBoard.elementIterator("Take");i.hasNext();) {
            xmlTake = (Element)i.next();
            // parseTakeData(Element xmlTake, int intIndex, Statement st)
            parseTakeData(xmlTake, intCounter++, st, fSourceFolder);

        }
        Element xmlTracks = xmlRoot.element("Tracks");
        Element xmlTrack;
        intCounter = 1;
        for (Iterator i = xmlTracks.elementIterator("Track");i.hasNext();) {
            xmlTrack = (Element)i.next();
            // parseTakeData(Element xmlTake, int intIndex, Statement st)
            parseTrackData(xmlTrack, st, fSourceFolder);
            // parseTrackData(Element xmlTrack, Statement st, File fSourceFolder)

        }
        /** The xml data has been parsed and the wave files are loaded, now to check the title and UMIDs
         * Also need to chack that there are the same number of SOURCE_INDEX entries as there are sound files
         */
        int intSourceIndexCount = 0;
        int intSourceIndex = 0;
        long lFileOffset = 0;
        long lTimeCodeOffset = 0;
        String strUMID;
        String strName;



        try {
            strSQL = "SELECT intIndex, strUMID, strName, intFileOffset, intTimeCodeOffset FROM PUBLIC.SOURCE_INDEX ORDER BY intIndex;";
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(strSQL);
            while (rs.next()) {
                intSourceIndex = rs.getInt(1);
                strUMID = URLDecoder.decode(rs.getString(2), "UTF-8");
                strName = URLDecoder.decode(rs.getString(3), "UTF-8");
                lFileOffset = rs.getLong(4);
                lTimeCodeOffset = rs.getLong(5);
                tempBWFProc = new BWFProcessor();
                tempBWFProc.setSrcFile(fSourceFile);
                tempBWFProc.setMultipart(true);
                if (tempBWFProc.readFile(lFileOffset + lInitialAudioOffset,0)) {
                    if (tempBWFProc.getBextTitle().length() == 0) {
                        tempBWFProc.setBextTitle(strName);
                    }
                    if (tempBWFProc.getBextOriginatorRef().length() > 0) {
                        strUMID = URLEncoder.encode(tempBWFProc.getBextOriginatorRef(), "UTF-8");
                        strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET strUMID = \'" + strUMID + "\' WHERE intIndex = " + intSourceIndex + ";";
                        int i = st.executeUpdate(strSQL);
                        if (i == -1) {
                            System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                        }
                    } else {
                        tempBWFProc.setBextOriginatorRef(strUMID);
                    }
                    if (tempBWFProc.getBextTimeCodeOffset() > 0) {
                        lTimeCodeOffset = tempBWFProc.getBextTimeCodeOffset();
                        System.out.println("Timecode ref from source file is " +  lTimeCodeOffset);
                        strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET intTimeCodeOffset = " + lTimeCodeOffset + " WHERE intIndex = " + intSourceIndex + ";";
                        int i = st.executeUpdate(strSQL);
                        if (i == -1) {
                            System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                        }
                    }
                    
                    vBWFProcs.add(tempBWFProc);
                    intSoundFiles++;
                }
            }


        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("Error on decoding at " + strSQL + e.toString());
        }
        System.out.println(" " + intSoundFiles + " sound files have been read from source file");
        try {
            strSQL = "SELECT COUNT(*) FROM PUBLIC.SOURCE_INDEX ;";
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(strSQL);
            rs.next();
            intSourceIndexCount = rs.getInt(1);
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        }
        if (intSoundFiles != intSourceIndexCount) {
            System.out.println("Error " + intSoundFiles + " sound files in source file but " + intSourceIndexCount + " are referenced in the project.");
            System.exit(1);
        }
        /**
         * Next step is to create an ADL file and write the output.
         */
         writeADLFile(fDestADLFile, st);
         /** the next step is to extract the audio files from the source
          *
          */
        /** Now to write out the sound files
         * 
         */
         String strURI;
         File fDestFile;
         try {
            strSQL = "SELECT intIndex, strURI FROM PUBLIC.SOURCE_INDEX ;";
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(strSQL);
            while (rs.next()) {
                intSourceIndex = rs.getInt(1);
                strURI = URLDecoder.decode(rs.getString(2), "UTF-8");
                strURI = strURI.substring(21, strURI.length());
                fDestFile = new File(strURI);
                if (fDestFile.exists()) {
                    continue;
                }
                System.out.println("Starting audio file write on  " + intSourceIndex + " dest file " + strURI);
                ((BWFProcessor)vBWFProcs.get(intSourceIndex-1)).writeFile(fDestFile);
//                if (((BWFProcessor)vBWFProcs.get(intSourceIndex-1)).getBextTitle().length() == 0) {
//                    ((BWFProcessor)vBWFProcs.get(intSourceIndex-1)).setBextTitle(strName);
//                }
                
            }


        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("Error on decoding at " + strSQL + e.toString());
        }
        System.out.println("Processing finished, press CTRL-C to exit");

        /** Take care of shutdown conditions.
        *
        */
        Runtime.getRuntime().addShutdownHook(new Thread("Shutdown hook") {
            public void run() {
                try {

                } catch (Throwable t) {

                }
                System.out.println("Shutting down");
                Runtime.getRuntime().halt(0);
            }
        });
        while(true){
            int intSleepCounter = 100;
            while (intSleepCounter > 0) {
                // System.out.println("Main thread sleep counter " + intSleepCounter);
                try {
                    Thread.sleep(randomNumber.nextInt(200)  + 900 ); // Sleep for about 1s
                } catch (InterruptedException e) {
                    System.out.println("Sleep interrupted." );
                    return;
                }
                intSleepCounter--;
            }
        }




    }
        /**
         * This returns an html string which contains the contents of a table in the database
         * @param setTable This is the table
         * @return This is the html string to send to the browser
         */
    public static String getTableData(String setTable) {
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
    /**
     * This looks in the VCS project file and extracts the XML project data from the start of the file
     * Two types of XML data can be found at the start of the file
     * UTF16 which starts with
     * 0xFF 0xFE <?xml version="1.0" encoding="UTF-16"?><VcsAtsProject.......</VcsAtsProject> 0x00 0x0A 0x00 0x00 0x00 0x00 RIFF binary data
     * UTF8 which starts with
     * <?xml version="1.0" ?><VcsAtsProject.......</VcsAtsProject> 0x0A 0x00 RIFF binary data
     * @param setSourceFile Sets the source file to open
     * @return A string which contains XML data
     */
    public static String getXMLHeader(File setSourceFile) {
        ByteBuffer sourceBytes = ByteBuffer.allocate(262144);
        Pattern pXML = Pattern.compile("(<.*?VcsAtsProject>)",Pattern.DOTALL);
        FileInputStream inFile;
        String strSourceCharacters = "";
        try {
            inFile = new FileInputStream(setSourceFile);
            FileChannel inChannel = inFile.getChannel();
            inChannel.read(sourceBytes);
            sourceBytes.flip();
            
//            byte bFirst = sourceBytes.get(0);
//            System.out.println("0x" + Integer.toHexString(bFirst+0x800).substring(1));
//            byte bSecond = sourceBytes.get(1);
//            System.out.println("0x" + Integer.toHexString(bSecond+0x800).substring(1));
            // Start by trying to determine if this is UTF8 or UTF16
            if (sourceBytes.get(0) == (byte)0xFF && sourceBytes.get(1) == (byte)0xFE) {
                System.out.println("UTF16 has been detected");

                CharBuffer charSourceCharacters = Charset.forName("ISO-8859-1").newDecoder().decode(sourceBytes);
                strSourceCharacters = charSourceCharacters.toString();
                int intStart = strSourceCharacters.indexOf("<");
                int intEnd = strSourceCharacters.indexOf("V c s A t s P r o j e c t >");
                System.out.println("The first try at finding the end gives " + intEnd);
                intEnd = strSourceCharacters.indexOf(">", intEnd);
                int intDataStart = strSourceCharacters.indexOf("RIFF", intEnd);
                System.out.println("The text starts at "+ intStart + " and ends at " +  intEnd + " audio data starts at " + intDataStart);

                sourceBytes.position(intStart).limit(intEnd + 2);
                ByteBuffer sourceBytesSlice = sourceBytes.slice();
                java.nio.charset.CharsetDecoder decoder = Charset.forName("UTF-16LE").newDecoder();
                decoder.onMalformedInput(CodingErrorAction.IGNORE);
                CharBuffer charSourceCharactersUTF16 = decoder.decode(sourceBytesSlice);
                strSourceCharacters = charSourceCharactersUTF16.toString();
                lInitialAudioOffset = intDataStart;
//                System.out.println("The first characters are  \n"+ strSourceCharacters);
//                System.exit(0);
            } else {
                // Use a brute force method to decode the xml
                // If the original is UTF8 then that's OK, if not then some data could be lost.
                java.nio.charset.CharsetDecoder decoder = Charset.forName("ISO-8859-1").newDecoder();
                decoder.onMalformedInput(CodingErrorAction.IGNORE);
                CharBuffer charSourceCharacters = decoder.decode(sourceBytes);
                StringBuilder bufString = new StringBuilder();
                char charCharacter;
                for (int i=0, n=charSourceCharacters.length(); i<n; i++) {
                    charCharacter = charSourceCharacters.get();
                    if (charCharacter > 0 && charCharacter < 128) {
                        bufString.append(charCharacter);
                    }
                }
                strSourceCharacters = bufString.toString();
//                System.out.println("The text is "+ strSourceCharacters);
                int intStart = strSourceCharacters.indexOf("<");
                Matcher matcher = pXML.matcher(strSourceCharacters);

                if (matcher.find()) {
                    System.out.println("Matcher is true");
                    strSourceCharacters = matcher.group(0);
                    System.out.println(strSourceCharacters.length() + " bytes of XML data found in file... " + setSourceFile + "\n" + "\n");

                } else {
                    System.out.println("Failed to find XML data in file... \n" + setSourceFile.toString());
                }

                int intEnd = intStart + strSourceCharacters.length();
//                System.out.println("The first try at finding the end gives " + intEnd);
//                intEnd = strSourceCharacters.indexOf(">", intEnd);
                int intDataStart = intEnd + 2;
                System.out.println("The text starts at "+ intStart + " and ends at " +  intEnd + " audio data starts at " + intDataStart);
                lInitialAudioOffset = intDataStart;
//                strSourceCharacters = strSourceCharacters.substring(intStart, intEnd);
            }
//            sourceBytes.position(0);
            // Now locate the opening and closing locations in the data

//            sourceBytes.flip();

//            charSourceCharactersUTF16 = Charset.forName("UTF-16").newDecoder().decode(charSourceCharactersUTF16);
            
//            strSourceCharacters = strSourceCharacters.getBytes("UTF-8").toString();
//            System.out.println("The text is "+ strSourceCharacters);
//            if (intStart > 0 && intEnd > 0) {
//                strSourceCharacters = strSourceCharacters.substring(intStart, intEnd + 28);
//                System.out.println("The first characters are  \n"+ strSourceCharacters + " the search string was found at " +  intEnd);
//            }
            
            // The XML string starts with <?xml version="1.0" ?>
            // The xml string ends with </VcsAtsProject> then 0x0A and 0x00. The next bytes after this are RIFFnnnnWAVE etc
//            Matcher matcher = pXML.matcher(strSourceCharacters);
//            System.out.println("Source file opened "+ setSourceFile.toString() +"");
//
//

            inFile.close();
        } catch (java.io.FileNotFoundException e) {
            System.out.println("Source file not found " + setSourceFile.toString());
        } catch (java.io.IOException e) {
            System.out.println("Error while reading file in getXMLHeader " + setSourceFile.toString() + " " + e.toString());
            System.exit(0);
        }
//        System.out.println("The text is "+ strSourceCharacters);
        return strSourceCharacters;


    }
    /** This method will load the XML data in to a document held in memory.
     */
    private static boolean loadSettingsFile(String setXMLData) {
        try {
            SAXReader reader = new SAXReader();
            
            xmlDocument = reader.read(new StringReader(setXMLData));
        } catch (DocumentException de) {
            System.out.println("Exception while loading XML data " + de.toString());
            return false;
        }
        return true;
    }
    public static void parseTakeData(Element xmlTake, int intIndex, Statement st, File fSourceFolder) {
        
//        String strFileName = xmlTake.elementText("FileName");
        if (Integer.parseInt(xmlTake.elementText("AudioInProject")) == 0) {
            return;
        }
        String strURI = fSourceFolder.toString();
        strURI = strURI.replaceAll("\\\\", "/");
        strURI = "URL:file://localhost/" + strURI;
        String strName = xmlTake.elementText("TakeName");
        String strNameLowerCase = strName.toLowerCase();
        int intStart = strName.lastIndexOf("\\");
        int intEnd = strNameLowerCase.lastIndexOf(".wav");
        if (intEnd == -1) {
            intEnd = strName.length();
        }
        if (intStart > 0) {
            strName = strName.substring(intStart + 1, intEnd);
        }
        strName = strName.replaceAll("[\\/:*?\"<>|]","_");
//        System.out.println("String name " + strName + " length is " +  intStart);
        
        String strDatabaseTakeId = xmlTake.elementText("DatabaseTakeId");
        /** The take name may contain the filename including path if the file mode is local,
         * e.g. D:\Projects\arth_test_project_01\11223344.wav and this seems to be the same as the filename.
         * In FRAMEWORK2 filemode the name would be 11223344 in this example and the filename would be something like 359FA892.dat
         * When the audio and XML are embedded in one file the AudioInProject should be 1, if it's not we can't locate the file so need to give up
         */
        String strUMID = xmlTake.elementText("ExternalId");
        long lFileOffset = (Long.parseLong(xmlTake.elementText("FileOffset")));
        strUMID = strUMID.substring(32, 64);
//        System.out.println("UMID found " + strUMID);
        long lLength = Long.parseLong(xmlTake.elementText("Length")) * intSampleRate / 1000000000;
        try {
            if (strDatabaseTakeId != null) {
                strURI = strURI + "/" + strName + "_" + strDatabaseTakeId + ".wav";
            } else {
                strURI = strURI + "/" + strName + ".wav";
            }

            strName = URLEncoder.encode(strName, "UTF-8");
            strURI = URLEncoder.encode(strURI, "UTF-8");
            strSQL = "INSERT INTO PUBLIC.SOURCE_INDEX (intIndex, strType, strURI, strUMID, intLength, strName, intFileOffset, intTimeCodeOffset) VALUES (" +
                intIndex + ", \'F\',\'" + strURI + "\',\'" + strUMID + "\', " + lLength + ", \'" + strName + "\', " + lFileOffset + ", 0) ;";
            int i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }

        } catch(java.io.UnsupportedEncodingException e) {
            System.out.println("Exception " + e.toString());
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        }





    }
    public static void parseTrackData(Element xmlTrack, Statement st, File fSourceFolder) {
        int intTrackIndex = Integer.parseInt(xmlTrack.elementText("Number"));
        Element xmlAudioClipList = xmlTrack.element("AudioClipList");
        if (xmlAudioClipList == null) {
            return;
        }
//        System.out.println("AudioClipList " + xmlAudioClipList.asXML());
        Element xmlAudioClip;
//        int intCounter = 1;
        for (Iterator i = xmlAudioClipList.elementIterator("AudioClip");i.hasNext();) {
            xmlAudioClip = (Element)i.next();
//            System.out.println("AudioClip " + xmlAudioClip.asXML());
            // parseTakeData(Element xmlTake, int intIndex, Statement st)
            parseClipData(xmlAudioClip,intTrackIndex, st, fSourceFolder);

        }
    }

    public static void parseClipData(Element xmlClip, int intTrackIndex, Statement st, File fSourceFolder) {
        int intLeftTrack = (intTrackIndex*2)+1;
        int intRightTrack = (intTrackIndex*2)+2;
        String strSourceChannel = "1~2";
        String strDestChannel = ((intTrackIndex*2)+1) + "~" + ((intTrackIndex*2)+2);
        String strTrackMap = strSourceChannel + " " + strDestChannel;
        String strType = "Cut";
        String strRef = "I";
        long lDestIn = (Long.parseLong(xmlClip.elementText("Offset"))) * intSampleRate / 1000000000;
        long lDestOut;
        long lOriginalDestIn = lDestIn;
        Element xmlTakeReference;
        String strRemark = xmlClip.elementText("ClipName");
        int intStart = strRemark.lastIndexOf("\\");
        if (intStart > 0) {
            strRemark = strRemark.substring(intStart + 1, strRemark.length());
        }
        try {
            strRemark = URLEncoder.encode(strRemark, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("Error on while trying to encode string" );
            return;
        }
        for (Iterator i = xmlClip.elementIterator("TakeReference");i.hasNext();) {
            xmlTakeReference = (Element)i.next();
            long lSourceIn = (Long.parseLong(xmlTakeReference.elementText("Offset"))) * intSampleRate/1000000000;
            lDestOut = ((Long.parseLong(xmlTakeReference.elementText("Length"))) * intSampleRate/1000000000) + lDestIn;
            String strUMID = xmlTakeReference.elementText("ExternalId");
            strUMID = strUMID.substring(32, 64);
            // Need to find the source index from the database with this UMID.
            int intSourceIndex = 0;

            strSQL = "SELECT intIndex FROM PUBLIC.SOURCE_INDEX WHERE strUMID = \'" + strUMID + "\';";
            try {
                st = conn.createStatement();
                ResultSet rs = st.executeQuery(strSQL);
                rs.next();
                if (!(rs.wasNull()) &&  rs.getInt(1) > 0) {
                    intSourceIndex = rs.getInt(1);
                } else {
                    System.out.println("No source reference found for clip " + intClipCounter);
                    return;
                }
                strSQL = "INSERT INTO PUBLIC.EVENT_LIST (intIndex, strType, strRef, intSourceIndex, strTrackMap, intSourceIn, intDestIn, intDestOut, strRemark) VALUES (" +
                    intClipCounter++ + ", \'" + strType + "\',\'" + strRef + "\'," + intSourceIndex + ",\'" + strTrackMap + "\'," + lSourceIn + "," + lDestIn + "," + lDestOut + ",\'" + strRemark + "\') ;";
                int j = st.executeUpdate(strSQL);
                if (j == -1) {
                    System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                }
            } catch (java.sql.SQLException e) {
                System.out.println("Error on SQL " + strSQL + e.toString());
                return;
            } 
            lDestIn = lDestOut + 1;
        }
        Element xmlClipFadeMarkers = xmlClip.element("ClipFadeMarkers");
        if (xmlClipFadeMarkers == null) {
            return;
        }
        parseClipFadeMarkers(xmlClipFadeMarkers, intLeftTrack, lOriginalDestIn, st);

    }
    public static void parseClipFadeMarkers(Element xmlClipFadeMarkers, int intLeftTrack, long lClipStart, Statement st) {
        Element xmlFadeMarker;
        long lFadePoint;
        double dLevel;
        String strLevel;
        int intRightTrack = intLeftTrack + 1;
        for (Iterator i = xmlClipFadeMarkers.elementIterator("FadeMarker");i.hasNext();) {
            xmlFadeMarker = (Element)i.next();
            lFadePoint = (Long.parseLong(xmlFadeMarker.elementText("Position"))) * intSampleRate/1000000000;
            lFadePoint = lFadePoint + lClipStart;
            strLevel = xmlFadeMarker.elementText("Offset");
            dLevel = Double.valueOf(strLevel);
            dLevel = (double)Math.round(dLevel * 100) / 100;
            strLevel = String.format("%.2f", dLevel);

            try {
                strSQL = "INSERT INTO PUBLIC.FADER_LIST (intTrack, intTime, strLevel) VALUES (" +
                    intLeftTrack + ", " + lFadePoint + ",\'" + strLevel + "\') ;";
                int j = st.executeUpdate(strSQL);
                if (j == -1) {
                    System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                }
                strSQL = "INSERT INTO PUBLIC.FADER_LIST (intTrack, intTime, strLevel) VALUES (" +
                    intRightTrack + ", " + lFadePoint + ",\'" + strLevel + "\') ;";
                j = st.executeUpdate(strSQL);
                if (j == -1) {
                    System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                }

            } catch (java.sql.SQLException e) {
                System.out.println("Error on SQL " + strSQL + e.toString());
                return;
            }



        }
    }
    private static boolean writeADLFile(File setDestFile, Statement st) {
        String strADLText = "<ADL>\n";
        String str12Space = "            ";
        String str8Space = "        ";
        String str4Space = "    ";
        try {
            // Fill in the VERSION tags
            strSQL = "SELECT strADLVersion, strCreator, strCreatorVersion FROM PUBLIC.VERSION;";
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(strSQL);
            rs.next();
            if (!(rs.wasNull()) ) {
                strADLText = strADLText + str4Space + "<VERSION>\n";
                strADLText = strADLText + str8Space + "(VER_ADL_VERSION)  " + (URLDecoder.decode(rs.getString(1), "UTF-8")) + "\n";
                strADLText = strADLText + str8Space + "(VER_CREATOR)      \"" + (URLDecoder.decode(rs.getString(2), "UTF-8")) + "\"\n";
                strADLText = strADLText + str8Space + "(VER_CRTR)         " + (URLDecoder.decode(rs.getString(3), "UTF-8")) + "\n";
                strADLText = strADLText + str4Space + "</VERSION>\n";
            }
            // Fill in the PROJECT tags
            strSQL = "SELECT strTitle, strNotes, dtsCreated FROM PUBLIC.PROJECT;";
            st = conn.createStatement();
            rs = st.executeQuery(strSQL);
            rs.next();
            if (!(rs.wasNull()) ) {
                strADLText = strADLText + str4Space + "<PROJECT>\n";
                strADLText = strADLText + str8Space + "(PROJ_TITLE)       \"" + (URLDecoder.decode(rs.getString(1), "UTF-8")) + "\"\n";
                strADLText = strADLText + str8Space + "(PROJ_NOTES)       \"" + (URLDecoder.decode(rs.getString(2), "UTF-8")) + "\"\n";
                DateTime dtCreated = fmtSQL.parseDateTime(rs.getString(3).substring(0, 19)).withZone(DateTimeZone.UTC);
                strADLText = strADLText + str8Space + "(PROJ_CREATE_DATE) " + fmtADLXML.print(dtCreated) + "\n";
                strADLText = strADLText + str4Space + "</PROJECT>\n";

            }
            // Fill in the SEQUENCE tags
            strADLText = strADLText + str4Space + "<SEQUENCE>\n";
            strADLText = strADLText + str8Space + "(SEQ_SAMPLE_RATE)  S" + intSampleRate + "\n";
            String strFrameRate;
            if (dFrameRate%5 == 0) {
                strFrameRate = "" + (java.lang.Math.round(dFrameRate));
            } else {
                strFrameRate = "" + dFrameRate;
            }
            strADLText = strADLText + str8Space + "(SEQ_FRAME_RATE)   " + strFrameRate + "\n";
            strADLText = strADLText + str8Space + "(SEQ_DEST_START)   00.00.00.00/0000\n";
            strADLText = strADLText + str4Space + "</SEQUENCE>\n";
            // Fill in the SOURCE_INDEX tags
            strADLText = strADLText + str4Space + "<SOURCE_INDEX>\n";
            strSQL = "SELECT intIndex, strURI, strUMID, intLength, strName, intTimeCodeOffset FROM PUBLIC.SOURCE_INDEX ORDER BY intIndex;";
            String strIndex;
            String strSourceIndex;
            String strUMID;
            String strURI;
            String strName;
            String strSourceIn;
            String strDestIn;
            String strDestOut;
            String strRemark;
            String strTimeCodeOffset;
            st = conn.createStatement();
            rs = st.executeQuery(strSQL);
            while (rs.next()) {
                strIndex = String.format("%04d", rs.getInt(1));
                strURI = URLDecoder.decode(rs.getString(2), "UTF-8");
                strUMID = rs.getString(3);
                strTimeCodeOffset = getADLTimeString(rs.getLong(6), intSampleRate, dFrameRate);
                strName = URLDecoder.decode(rs.getString(5), "UTF-8");
                strADLText = strADLText + str8Space + "(Index) " + strIndex + "\n";
                strADLText = strADLText + str12Space + " (F) \"" + strURI + "\" \"" + strUMID + "\" " +  strTimeCodeOffset + " _ \"" + strName + "\" N\n";
            }
            strADLText = strADLText + str4Space + "</SOURCE_INDEX>\n";
            // Fill in the EVENT_LIST tags
            strADLText = strADLText + str4Space + "<EVENT_LIST>\n";
            strSQL = "SELECT EVENT_LIST.intIndex, intSourceIndex, strTrackMap, intSourceIn, intDestIn, intDestOut, "
                    + "strRemark, intTimeCodeOffset FROM PUBLIC.EVENT_LIST, PUBLIC.SOURCE_INDEX "
                    + "WHERE EVENT_LIST.intSourceIndex = SOURCE_INDEX.intIndex ORDER BY EVENT_LIST.intIndex;";
            st = conn.createStatement();
            rs = st.executeQuery(strSQL);
            while (rs.next()) {
                strIndex = String.format("%04d", rs.getInt(1));
                strSourceIndex = String.format("%04d", rs.getInt(2));
                strSourceIn = getADLTimeString(rs.getInt(4) + rs.getInt(8), intSampleRate, dFrameRate);
                strDestIn = getADLTimeString(rs.getInt(5), intSampleRate, dFrameRate);
                strDestOut = getADLTimeString(rs.getInt(6), intSampleRate, dFrameRate);
                strRemark = URLDecoder.decode(rs.getString(7), "UTF-8");
                strADLText = strADLText + str8Space + "(Entry) " + strIndex + "\n";
                strADLText = strADLText + str12Space + "(Cut) I " + strSourceIndex + "  " + rs.getString(3) + "  " + strSourceIn + "  " + strDestIn + "  " + strDestOut + "  R\n";
                if (strRemark.length() > 0) {
                    strADLText = strADLText + str12Space + "(Rem) NAME \"" + strRemark + "\"\n";
                }
            }

            strADLText = strADLText + str4Space + "</EVENT_LIST>\n";

            // Fill in the FADER_LIST tags
            strADLText = strADLText + str4Space + "<FADER_LIST>\n";
            strSQL = "SELECT intTrack, intTime, strLevel FROM PUBLIC.FADER_LIST ORDER BY intTrack, intTime;";
            st = conn.createStatement();
            rs = st.executeQuery(strSQL);
            while (rs.next()) {
                strDestIn = getADLTimeString(rs.getInt(2), intSampleRate, dFrameRate);
                strADLText = strADLText + str8Space + "(FP)  " + rs.getString(1) + "  " + strDestIn + "  " + rs.getString(3) + "\n";
            }
            strADLText = strADLText + str4Space + "</FADER_LIST>\n";
            strADLText = strADLText + "\n</ADL>";
            BufferedWriter bOutbuf = new BufferedWriter(new FileWriter(setDestFile, false));
            bOutbuf.write(strADLText);
            bOutbuf.close();
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
            return false;
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("Error on URL decode " + e.toString());
        } catch (java.io.IOException e) {
            System.out.println("File io error " + e.toString());
        }
        System.out.println("File written " + setDestFile.toString());
        return true;

    }
    /**
     * This method converts a long in samples in to a formatted string in hour, minutes,
     * seconds, frames and samples in the correct format for an AES31 ADL file.
     * If an invalid sample rate is supplied it defaults to 48000
     * If an invalid frame rate is supplied it defaults to 25
     * These are standard across most of the world.
     * @param lSamples This is a long with the sample count to be converted.
     * @param intSampleRate This is the sample rate, only 44100 and 48000 are valid, 48000 is the default.
     * @param fFrameRate This is a float with the frame rate, only 24, 25, 30 and 29.97 are valid numbers, 25 is the default.
     * @return Returns a formatted string representing the time in ADL format. See AES31 specs for more information.
     */
    public static String getADLTimeString (long lSamples, int intSampleRate, double dFrameRate) {
        String strSep1;
        String strSep2;
        if (intSampleRate == 44100) {
            strSep2 = "|";
        } else {
            strSep2 = "/";
        }
        if (dFrameRate%5 == 0 || dFrameRate == 24) {
            int intFrameRate;
            // Frame rate 24, 25 or 30, simple maths
            strSep1 = ".";
            intFrameRate = 25;
            if (dFrameRate == 30) {
                strSep1 = "|";
                intFrameRate = 30;
            } 
            if (dFrameRate == 24) {
                strSep1 = "=";
                intFrameRate = 24;
            }
            long lHours = lSamples/(60*60*intSampleRate);
            lSamples = lSamples%(60*60*intSampleRate);
            long lMinutes = lSamples/(60*intSampleRate);
            lSamples = lSamples%(60*intSampleRate);
            long lSeconds = lSamples/intSampleRate;
            lSamples = lSamples%intSampleRate;
            long lFrames = lSamples/(intSampleRate/intFrameRate);
            lSamples = lSamples%(intSampleRate/intFrameRate);
            return String.format("%02d", lHours) + strSep1 + String.format("%02d", lMinutes) + strSep1 + String.format("%02d", lSeconds) + strSep1 +
                    String.format("%02d", lFrames) + strSep2 + String.format("%04d", lSamples);
        }
        if (dFrameRate == 29.97) {
            strSep1 = ":";
            String strSep3 = ",";
            // Frame rate 29.97, hard maths, first of all calculate the frame number and the remainder of samples
            long lFrameNumber = (long)java.lang.Math.floor((lSamples/(intSampleRate/dFrameRate)));
            lSamples = (long)java.lang.Math.floor((lSamples%(intSampleRate/dFrameRate)));
            /**CONVERT A FRAME NUMBER TO DROP FRAME TIMECODE
            * Code by David Heidelberger, adapted from Andrew Duncan
            * Given an int called framenumber and a float called framerate
            * Framerate should be 29.97, 59.94, or 23.976, otherwise the calculations will be off.
            */
            int d;
            int m;

            int intDropFrames = (int)java.lang.Math.round(dFrameRate * .066666); //Number of frames to drop on the minute marks is the nearest integer to 6% of the framerate
            int intFramesPerHour = (int)java.lang.Math.round(dFrameRate*60*60); //Number of frames in an hour
            int intFramesPer24Hours = intFramesPerHour*24; //Number of frames in a day - timecode rolls over after 24 hours
            int intFramesPer10Minutes = (int)java.lang.Math.round(dFrameRate * 60 * 10); //Number of frames per ten minutes
            int intFramesPerMinute = (int)java.lang.Math.round((dFrameRate*60) -  intDropFrames); //Number of frames per minute is the round of the framerate * 60 minus the number of dropped frames
            //Negative time. Add 24 hours.
            if (lFrameNumber<0) {
                lFrameNumber=intFramesPer24Hours+lFrameNumber;
            }

            //If framenumber is greater than 24 hrs, next operation will rollover clock
            lFrameNumber = lFrameNumber % intFramesPer24Hours; //% is the modulus operator, which returns a remainder. a % b = the remainder of a/b

            d = (int)java.lang.Math.floor(lFrameNumber/intFramesPer10Minutes); //
            m = (int)(lFrameNumber % intFramesPer10Minutes);

            //In the original post, the next line read m>1, which only worked for 29.97. Jean-Baptiste Mardelle correctly pointed out that m should be compared to dropFrames.
            if (m>intDropFrames) {
                lFrameNumber=(long)(lFrameNumber + (intDropFrames*9*d) + intDropFrames*java.lang.Math.floor(((m-intDropFrames)/intFramesPerMinute)));
            } else {
                lFrameNumber = lFrameNumber + intDropFrames*9*d;
            }

            int frRound = (int)java.lang.Math.round(dFrameRate);
            int intFrames = (int)(lFrameNumber % frRound);
            int intSeconds = (int)(java.lang.Math.floor(lFrameNumber / frRound) % 60);
            int intMinutes = (int)(java.lang.Math.floor(java.lang.Math.floor(lFrameNumber / frRound) / 60) % 60);
            int intHours = (int)(java.lang.Math.floor(java.lang.Math.floor(java.lang.Math.floor(lFrameNumber / frRound) / 60) / 60));
            return String.format("%02d", intHours) + strSep1 + String.format("%02d", intMinutes) + strSep1 + String.format("%02d", intSeconds) + strSep3 +
                    String.format("%02d", intFrames) + strSep2 + String.format("%04d", lSamples);

        }
        return "";
    }

}
