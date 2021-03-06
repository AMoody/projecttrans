package jprojecttranslator;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import wavprocessor.WAVProcessor;



/**
 * Project reader for VCS Startrack projects.
 * VCS Startrack projects can be stored as an XML file with separate audio files which may or may not be stored on the file system.
 * They can also be stored as one large file where the audio files are added after the XML structure end to end.
 * Events on the edl timeline are read and gain information is translated in to automation data.
 * Source tracks are usually stereo and this is maintained through to the ADL file.
 * @author arth
 */
public class jProjectReader_VCS extends jProjectReader {
    protected static long lInitialAudioOffset = 0;
    private int intHiddenClipCounter = 1;
    /** This is the xml document object which is used for loading and saving to an xml file.*/
    static Document xmlDocument = DocumentHelper.createDocument();
    public static DateTimeFormatter fmtVCSXML = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSS");
    /* Constructor
     * 
     */
    public jProjectReader_VCS() {
         
    } 
    
    /*
     * This returns a FileFilter which this class can read
     */
    public javax.swing.filechooser.FileFilter getFileFilter() {
        javax.swing.filechooser.FileFilter filter = new FileNameExtensionFilter("VCS Startrack (.aus,.dat)", "aus", "dat");
        return filter;
    }
    protected boolean processProject() {
        intSoundFilesLoaded = 0;
        intHiddenClipCounter = 1;
        setChanged();
        notifyObservers();
        System.out.println("VCS reader thread running");
        // Need to delete old data
        String strXMLData = getXMLHeader(fSourceFile);
        // Load the XML data in to an XML document
        if (loadXMLData(strXMLData)) {
            System.out.println("XML source data loaded");
        } else {
            System.out.println("Failed to load XML source data");
            oProjectTranslator.writeStringToPanel(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectReader.FailReadXML"));
            return false;
        } 
        if (parseVCSXML(xmlDocument.getRootElement())) {
            System.out.println("XML parsed in to database");
            oProjectTranslator.writeStringToPanel(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectReader.ProjectLoaded"));
        } else {
            System.out.println("Failed to parse XML data");
            oProjectTranslator.writeStringToPanel(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectReader.FailReadXML"));
            return false;
        }
        setChanged();
        notifyObservers();
        oProjectTranslator.updateTable();
        return true;
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
    protected String getXMLHeader(File setSourceFile) {
        ByteBuffer sourceBytes = ByteBuffer.allocate(4194304);
        Pattern pXML = Pattern.compile("(<.*?VcsAtsProject>)",Pattern.DOTALL);
        FileInputStream inFile;
        String strSourceCharacters = "";
        try {
            inFile = new FileInputStream(setSourceFile);
            FileChannel inChannel = inFile.getChannel();
            inChannel.read(sourceBytes);
            sourceBytes.flip();
            // Start by trying to determine if this is UTF8 or UTF16
            if (sourceBytes.get(0) == (byte)0xFF && sourceBytes.get(1) == (byte)0xFE) {
                System.out.println("UTF16 has been detected");

                CharBuffer charSourceCharacters = Charset.forName("ISO-8859-1").newDecoder().decode(sourceBytes);
                strSourceCharacters = charSourceCharacters.toString();
                int intStart = strSourceCharacters.indexOf("<");
                int intEnd = strSourceCharacters.indexOf("V c s A t s P r o j e c t >");
                System.out.println("The first try at finding the end gives " + intEnd);
                intEnd = strSourceCharacters.indexOf(">", intEnd);
                int intDataStart = strSourceCharacters.indexOf("RIFF", intEnd); // -1 if not found
                if (intDataStart == -1 ||(strSourceCharacters.indexOf("riff", intEnd) > 1 && strSourceCharacters.indexOf("riff", intEnd) < intDataStart)) {
                    intDataStart = strSourceCharacters.indexOf("riff", intEnd);
                }
                if (intDataStart == -1 ||(strSourceCharacters.indexOf("RF64", intEnd) > 1 && strSourceCharacters.indexOf("RF64", intEnd) < intDataStart)) {
                    intDataStart = strSourceCharacters.indexOf("RF64", intEnd);
                }                
                // Add a test here to check that audio has been found.
                if (intDataStart == -1) {
                    intDataStart = intEnd + 6;
                }
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
                    if (charCharacter > 0 && charCharacter < 256) {
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
            }


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
    protected boolean loadXMLData(String setXMLData) {
        try {
            SAXReader reader = new SAXReader();
            
            xmlDocument = reader.read(new StringReader(setXMLData));
        } catch (DocumentException de) {
            System.out.println("Exception while loading XML data " + de.toString());
            return false;
        }
        return true;
    } 

    /**
     * This method parses the xml fields from the VCS project and adds the
     * information to the database.
     * @param setRoot   Pass in the xml root element.
     * @return          True if the XML was parsed successfully.
     */
    protected boolean parseVCSXML(Element setRoot) {
        Element xmlRoot = setRoot;
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
        DateTime dtsCreated = fmtVCSXML.withZone(DateTimeZone.getDefault()).parseDateTime(xmlCreator.elementText("SystemTime"));
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
            strSQL = "INSERT INTO PUBLIC.PROJECT (intIndex, strTitle, strNotes, dtsCreated, strOriginator, strClientData, intAudioOffset) VALUES (" +
                "1, \'"+strTitle+" \', \'"+strNotes+" \',\'"+strCreated+"\',\' \', \' \', "+lInitialAudioOffset+") ;";
            i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        }
        setChanged();
        notifyObservers();
        // Loop through the items on the take board
        Element xmlTakeBoard = xmlRoot.element("TakeBoard");
        Element xmlTake;
        int intCounter = 1;
        for (Iterator i = xmlTakeBoard.elementIterator("Take");i.hasNext();) {
            xmlTake = (Element)i.next();
            parseTakeData(xmlTake, intCounter++, st);

        }
        // Loop through the hidden takes.
        Element xmlHiddenTakes = xmlRoot.element("HiddenTakes");
        if (xmlHiddenTakes != null && xmlHiddenTakes.hasContent()) {
            for (Iterator i = xmlHiddenTakes.elementIterator("Take");i.hasNext();) {
                xmlTake = (Element)i.next();
                parseTakeData(xmlTake, intCounter++, st);
            }
        }
        // Update the names of the hidden takes
        Element xmlClipBoard = xmlRoot.element("ClipBoard");
        Element xmlAudioClipList, xmlAudioClip;
        if (xmlClipBoard != null && xmlClipBoard.hasContent()) {
            for (Iterator i = xmlClipBoard.elementIterator("AudioClipList");i.hasNext();) {
                xmlAudioClipList = (Element)i.next();
                for (Iterator j = xmlAudioClipList.elementIterator("AudioClip");j.hasNext();) {
                    xmlAudioClip = (Element)j.next();
                    parseClipboardClip(xmlAudioClip,st);
                }
            }
        }
        Element xmlTracks = xmlRoot.element("Tracks");
        Element xmlTrack;
        intCounter = 1;
        for (Iterator i = xmlTracks.elementIterator("Track");i.hasNext();) {
            xmlTrack = (Element)i.next();
            // parseTakeData(Element xmlTake, int intIndex, Statement st)
            parseTrackData(xmlTrack, st);
            // parseTrackData(Element xmlTrack, Statement st, File fSourceFolder)

        }
        /** The xml data has been parsed and the wave files are loaded, now to check the title and UMIDs
         * Also need to check that there are the same number of SOURCE_INDEX entries as there are sound files
         */
        int intSourceIndex = 0;
        long lFileOffset = 0;
        long lTimeCodeOffset = 0;
        String strUMID;
        String strName;
        // Find out how many sound files there should be
        try {
            strSQL = "SELECT COUNT(*) FROM PUBLIC.SOURCE_INDEX ;";
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(strSQL);
            rs.next();
            intSourceFileCount = rs.getInt(1);
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        }
        
        // Look for the sound files
        try {
            strSQL = "SELECT intIndex, strUMID, strName, intFileOffset, intTimeCodeOffset, strSourceFile, intVCSInProject, intFileSize, strType FROM PUBLIC.SOURCE_INDEX ORDER BY intIndex;";
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(strSQL);
            String strSourceFile, strDestFileExtension, strType;
            File fLocalSourceFile, fTempLocalSourceFile;
            int intVCSInProject, intChannels;
            long lIndicatedFileSize, lSampleRate, lSourceFileSize;
            double dDuration, dSourceSamples;
            while (rs.next()) {
                // Loop through the SOURCE_INDEX table and try to find out more about each file by reading data from the actual sound file (if we can find it)
                intSourceIndex = rs.getInt(1);
                strUMID = URLDecoder.decode(rs.getString(2), "UTF-8");
                strName = URLDecoder.decode(rs.getString(3), "UTF-8");
                lFileOffset = rs.getLong(4);
                lTimeCodeOffset = rs.getLong(5);
                strSourceFile = URLDecoder.decode(rs.getString(6), "UTF-8");
                fTempLocalSourceFile = new File(strSourceFile);
                intVCSInProject = rs.getInt(7);
                lSourceFileSize = rs.getLong(8);
                strType = rs.getString(9);
                // Try to find the file in the same folder as the EDL if it's not found already
                if (!fTempLocalSourceFile.exists()) {
                    // Might need to add the local path to find the file?
                    fTempLocalSourceFile = new File(fSourceFolder,strSourceFile);
                }
                if (!fTempLocalSourceFile.exists()) {
                    // Still not found, try changing the path?
                    fTempLocalSourceFile = new File(fSourceFolder,fTempLocalSourceFile.getName());
                } 
                if (!fTempLocalSourceFile.exists()) {
                    // Still not found, try changing the name?
                    fTempLocalSourceFile = new File(fSourceFolder,strName + ".wav");
                }                 
                    // fSourceFolder
                fLocalSourceFile = fTempLocalSourceFile;
                tempWAVProc = new WAVProcessor();
                tempWAVProc.setSrcFile(fLocalSourceFile);
                if (intVCSInProject == 1) {
                    tempWAVProc.setMultipart(true);
                } else {
                    tempWAVProc.setMultipart(false);
                }

                if (fLocalSourceFile.exists() && fLocalSourceFile.canRead() && tempWAVProc.readFile(lFileOffset + lInitialAudioOffset, lFileOffset + lInitialAudioOffset + lSourceFileSize)) {
                    strSourceFile = URLEncoder.encode(fLocalSourceFile.toString(), "UTF-8");
                    // Need to update the destination file extension, wav, rf64 or w64
                    if (jProjectTranslator.bAlwaysUseWavExt) {
                        strDestFileExtension = ".wav";
                    } else {
                        strDestFileExtension = "." + tempWAVProc.getFileType().toLowerCase();
                        if (strDestFileExtension.equalsIgnoreCase(".rf64")) {
                            strDestFileExtension = ".wav";
                        }
                        if (jProjectTranslator.intSave64BitFilesAs == 1 && tempWAVProc.getFileType().equalsIgnoreCase("W64")) {
                                strDestFileExtension = ".wav";
                        }
                        if (jProjectTranslator.intSave64BitFilesAs == 2 && tempWAVProc.getFileType().equalsIgnoreCase("RF64")) {
                                strDestFileExtension = ".w64";
                        }
                    }
                    lIndicatedFileSize = tempWAVProc.getIndicatedFileSize();
                    lSampleRate = tempWAVProc.getSampleRate();
                    dDuration =  tempWAVProc.getDuration();
                    intChannels = tempWAVProc.getNoOfChannels();
                    if (intChannels > 0){
                        dSourceSamples = tempWAVProc.getNoOfSamples()/intChannels;
                    } else {
                        dSourceSamples = 0;
                    }
                    String strFileType = tempWAVProc.getFileType();
                    if (!strFileType.equalsIgnoreCase("m4a")) {
                        strType = strFileType + " " + tempWAVProc.getBitsPerSample() + " bit ";
                        if (tempWAVProc.hasBextChunk()) {
                            strType = "B-" + strType;
                        } 
                        strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET intIndicatedFileSize = " + lIndicatedFileSize + ", intSampleRate =  " + lSampleRate + ", dDuration =  " + dDuration + ", intChannels = " + intChannels + " "
                            + ", intLength = " + dSourceSamples + ", strSourceFile = \'" + strSourceFile + "\', strDestFileName = concat(strDestFileName, \'"
                            + strDestFileExtension + "\'), strType = \'" + strType + "\' WHERE intIndex = " + intSourceIndex + ";";
                    } else {
                        strType = strFileType.toLowerCase();
                        strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET intIndicatedFileSize = " + lIndicatedFileSize + ", strDestFileName = concat(strDestFileName, \'"
                            + strDestFileExtension + "\'), strType = \'" + strType + "\' WHERE intIndex = " + intSourceIndex + ";";
                    }

                    
                    System.out.println("SoundFiles " + strSQL);
                    int i = st.executeUpdate(strSQL);
                    if (i == -1) {
                        System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                    }
                    
                    if (!strFileType.equalsIgnoreCase("m4a")) {
                        if (tempWAVProc.getBextTitle().length() == 0) {
                            tempWAVProc.setBextTitle(strName);
                        }
                        if (tempWAVProc.getBextOriginatorRef().length() > 0) {
                            strUMID = URLEncoder.encode(tempWAVProc.getBextOriginatorRef(), "UTF-8");
                            strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET strUMID = \'" + strUMID + "\' WHERE intIndex = " + intSourceIndex + ";";
                            i = st.executeUpdate(strSQL);
                            if (i == -1) {
                                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                            }
                        } else {
                            tempWAVProc.setBextOriginatorRef(strUMID);
                        }
                        // If there is a timecode offset and the file is the wrong sample rate for the project then this is going to get messed up later.
                        // At some stage the user will have to pass the file through an SRC, this might or might not deal with the timecode offset correctly.
                        // The safest option here is not to use it (set it to 0) if the sample rate is wrong.
                        // This means some data is lost but at least it should be possible to open the project with the audio playing correctly.
                        if (tempWAVProc.getBextTimeCodeOffset() > 0) {
                            if (jProjectTranslator.intPreferredSampleRate == lSampleRate) {
                                lTimeCodeOffset = tempWAVProc.getBextTimeCodeOffset();
                                System.out.println("Timecode ref from source file is " +  lTimeCodeOffset);
                                strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET intTimeCodeOffset = " + lTimeCodeOffset + " WHERE intIndex = " + intSourceIndex + ";";
                                i = st.executeUpdate(strSQL);
                                if (i == -1) {
                                    System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                                }
                            } else {
                                tempWAVProc.setBextTimeCodeOffset(0);
                                strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET intTimeCodeOffset = 0 WHERE intIndex = " + intSourceIndex + ";";
                                i = st.executeUpdate(strSQL);
                                if (i == -1) {
                                    System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                                }
                                System.out.println("Timecode ref in source file has been set to 0 because of a non-standard sample rate.");
                            }                        
                        }                        
                        lWAVProcessors.add(tempWAVProc);
                    }

                    
                    intSoundFilesLoaded++;
                    setChanged();
                    notifyObservers();
                } else {
                    if (strType.contentEquals("mp3")) {
                        strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET strDestFileName = concat(strDestFileName, \'.mp3\'), intIndicatedFileSize = IntFileSize WHERE intIndex = " + intSourceIndex + ";";
                        int i = st.executeUpdate(strSQL);
                        if (i == -1) {
                            System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                        }
                        intSoundFilesLoaded++;
                    }
                }


            }


        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("Error on decoding at " + strSQL + e.toString());
        }
        System.out.println(" " + intSoundFilesLoaded + " sound files have been read from source file");
        
        if (intSoundFilesLoaded != intSourceFileCount) {
            System.out.println("" + intSoundFilesLoaded + " sound files in source file but " + intSourceFileCount + " are referenced in the project.");
            // oProjectTranslator.writeStringToPanel("" + intSoundFilesLoaded + " sound files have been located but " + intSourceFileCount + " are referenced in the project.");
            java.text.MessageFormat message = new java.text.MessageFormat(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectReader.NotAllSoundFilesFound"));
            Object[] args = {intSoundFilesLoaded, intSourceFileCount};
            oProjectTranslator.writeStringToPanel(message.format(args));
            
        }
        if (intSoundFilesLoaded == intSourceFileCount && intSoundFilesLoaded > 1) {
            // oProjectTranslator.writeStringToPanel("All " + intSoundFilesLoaded + " sound files have been located.");
            java.text.MessageFormat message = new java.text.MessageFormat(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectReader.AllSoundFilesFound"));
            Object[] args = {intSoundFilesLoaded};
            oProjectTranslator.writeStringToPanel(message.format(args));
        }
        if (intSoundFilesLoaded == intSourceFileCount && intSoundFilesLoaded == 1) {
//            oProjectTranslator.writeStringToPanel("The one sound file in this project has been located.");
            oProjectTranslator.writeStringToPanel(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectReader.OneSoundFileFound"));
        }
        if (intSoundFilesLoaded == intSourceFileCount && intSoundFilesLoaded == 0) {
            oProjectTranslator.writeStringToPanel(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectReader.NoSoundFiles"));
        }
        /**
         * VCS sometimes has 0 length entries on the EDL but Sadie can't open these when there are also fade points.
         * To get round this zero length entries will be deleted from the event list table.
         */
        try {
            strSQL = "DELETE FROM PUBLIC.EVENT_LIST WHERE intDestIn = intDestOut;";
            int i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        }
        /**
         * VCS can have mixed sample rates in the EDL and this works fine, however other editors generally don't allow this so non-standard files have to be converted separately.
         * This sometimes causes rounding errors where and EDL has an entry which plays right up to the end of the audio file but the converted audio file does not contain enough samples.
         * This can be fixed by moving and EDL out points slightly earlier.
         */
        try {
            strSQL = "SELECT PUBLIC.EVENT_LIST.intIndex, intSourceIn, intDestIn, intDestOut, intLength, strRemark, intSampleRate FROM PUBLIC.SOURCE_INDEX, PUBLIC.EVENT_LIST WHERE PUBLIC.EVENT_LIST.intSourceIndex = PUBLIC.SOURCE_INDEX.intindex AND intSampleRate != " + jProjectTranslator.intPreferredSampleRate + " ORDER BY intIndex;";
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(strSQL);
            long lSourceOut, lDestOut, lSourceIn, lDestIn, lFileSamples, lDuration, lSafetyGap, lDestOutSeconds, lSampleRate;
            String strRemark;
            while (rs.next()) {
                lDestOut = rs.getLong("intDestOut");
                lDestIn = rs.getLong("intDestIn");
                lSourceIn = rs.getLong("intSourceIn");
                lDuration = lDestOut - lDestIn;
                lSourceOut = lSourceIn + lDuration;
                lFileSamples = rs.getLong("intLength");
                lSafetyGap = java.lang.Math.min(1000,lDuration);
                strRemark = URLDecoder.decode(rs.getString(6), "UTF-8");
                lSampleRate = rs.getLong("intSampleRate");
                // Change the number of samples in the file to the value it should be after it's been sample rate converted.
//                System.out.println("lFileSamples before adjustment " + lFileSamples );
                if (lSampleRate > 0) {
                    lFileSamples = (lFileSamples * jProjectTranslator.intPreferredSampleRate)/lSampleRate;
                }
//                System.out.println("lFileSamples after adjustment " + lFileSamples );
//                lSafetyGap = 10000000;
                // System.out.println("EDL out time " + lDestOut + " EDL index  " + rs.getInt(1) + " file length " + rs.getLong("intLength"));
                if (lSafetyGap + lSourceOut > lFileSamples  ) {
                    lDestOut = lDestOut + (lFileSamples - lSourceOut) - lSafetyGap;
                    lDestOutSeconds = lDestOut/jProjectTranslator.intPreferredSampleRate;
//                    System.out.println("lSafetyGap  " + lSafetyGap );
//                    System.out.println("lDestOut  " + lDestOut );
//                    System.out.println("lFileSamples  " + lFileSamples );
//                    System.out.println("lSourceOut  " + lSourceOut );
//                    System.out.println("lSampleRate  " + lSampleRate );
                    System.out.println("EDL out time too late on EDL index  " + rs.getInt(1) + " source out was " + lSourceOut + " dest out was " + rs.getLong("intDestOut") + " now " + lDestOut);
                    strSQL = "UPDATE PUBLIC.EVENT_LIST SET intDestOut = " + lDestOut + " WHERE intIndex = " + rs.getInt(1) + ";";
//                    oProjectTranslator.writeStringToPanel("Warning out time has been moved on clip \"" + strRemark + "\" near " + lDestOutSeconds +  " seconds in EDL.");
                    java.text.MessageFormat message = new java.text.MessageFormat(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectReader.WarningDestOutMoved"));
                    long minutes = lDestOutSeconds / (60);
                    long seconds = (lDestOutSeconds) % 60;
                    String str = String.format("%d:%02d", minutes, seconds);
                    Object[] args = {strRemark, str};
                    // Warning out time has been moved on clip \"{0}\" near {1} seconds in EDL.
                    oProjectTranslator.writeStringToPanel(message.format(args));
                    int j = st.executeUpdate(strSQL);
                    if (j == -1) {
                        System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                    }
                }
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        } catch (UnsupportedEncodingException e) {
            System.out.println("Error on URL decode " + e.toString());
        }
        
        return true;
    }
    /**
     * Parse the take data from the XMLTake element.
     * 
     * @param xmlTake       This is the xml element containing the data.
     * @param intIndex      Each take should have an index number.
     * @param st            This is used to access the database.
     */
    public void parseTakeData(Element xmlTake, int intIndex, Statement st) {
        
        String strFileName, strType;
        strType = "F";
        int intVCSInProject;
        String strName = xmlTake.elementText("TakeName");
        if (Integer.parseInt(xmlTake.elementText("AudioInProject")) == 0) {
            intVCSInProject = 0;
            // Need to add in some logic here to try and find the file.
            if (new File(strName).exists()) {
                strFileName = strName;
            } else {
                strFileName = xmlTake.elementText("FileName");
            }
            
        } else {
            strFileName = fSourceFile.toString();
            intVCSInProject = 1;
        }
//        String strURI = fSourceFolder.toString();
//        strURI = strURI.replaceAll("\\\\", "/");
//        strURI = "URL:file://localhost/" + strURI;
        String strURI = "";
        String strNameLowerCase = strName.toLowerCase();
        if (strNameLowerCase.contains("mp3")) {
            strType = "mp3";
        }
        int intStart = strName.lastIndexOf("\\");
        int intEnd = strNameLowerCase.lastIndexOf(".");
        if (intEnd == -1) {
            intEnd = strName.length();
        }
        if (intStart > 0) {
            strName = strName.substring(intStart + 1, intEnd);
        } else {
            strName = strName.substring(0, intEnd);
        }
        strName = strName.replaceAll("[\\/:*?\"<>|%&]","_");
//        System.out.println("String name " + strName + " length is " +  intStart);
        
        String strDatabaseTakeId = xmlTake.elementText("DatabaseTakeId");
        /** The take name may contain the filename including path if the file mode is local,
         * e.g. D:\Projects\arth_test_project_01\11223344.wav and this seems to be the same as the filename.
         * In FRAMEWORK2 filemode the name would be 11223344 in this example and the filename would be something like 359FA892.dat
         * When the audio and XML are embedded in one file the AudioInProject should be 1, if it's not we can't locate the file so need to give up
         */
        String strUMID = xmlTake.elementText("ExternalId");
        // In VCS the audio files can be stacked end to end in a .dat file, this shows the offset in bytes to the start of the file
        // from the end of the XML section
        long lFileOffset = (Long.parseLong(xmlTake.elementText("FileOffset")));
        // This is the indicated filesize from the XML metadata
        long lFileSize = (Long.parseLong(xmlTake.elementText("FileSize")));
        
        strUMID = strUMID.substring(32, 64);
//        System.out.println("UMID found " + strUMID);
        // In VCS the file length is given in ns, this is converted to audio samples for the database
        long lLength = Long.parseLong(xmlTake.elementText("Length")) * jProjectTranslator.intPreferredSampleRate / 1000000000;
        try {
            if (strDatabaseTakeId != null) {
                strURI = strName + "_" + strDatabaseTakeId;
            } else {
                strURI = strName + "_" + intIndex;
            }

            strName = URLEncoder.encode(strName, "UTF-8");
            strURI = URLEncoder.encode(strURI, "UTF-8");
            strFileName = URLEncoder.encode(strFileName, "UTF-8");
            strSQL = "INSERT INTO PUBLIC.SOURCE_INDEX (intIndex, strType, strDestFileName, strUMID, intLength, strName, intFileOffset, intTimeCodeOffset, strSourceFile, intCopied, intVCSInProject, intFileSize, intChannels) VALUES (" +
                intIndex + ", \'" + strType + "\',\'" + strURI + "\',\'" + strUMID + "\', " + lLength + ", \'" + strName + "\', " + lFileOffset + ", 0, \'" + strFileName + "\', 0, " + intVCSInProject + "," + lFileSize + ",2) ;";
            System.out.println(strSQL);
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
    /**
     * Parse track data.
     * @param xmlTrack      This is the xml element containing the data.
     * @param st            This is used to access the database.
     */
    public void parseTrackData(Element xmlTrack, Statement st) {
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
            parseClipData(xmlAudioClip,intTrackIndex, st);

        }
    }
    /**
     * The clip data contains information about an entry on an edl.
     * @param xmlClip             This is the xml element containing the data.
     * @param intTrackIndex       This is the track within the edl.
     * @param st                  This is used to access the database.
     */
    public void parseClipData(Element xmlClip, int intTrackIndex, Statement st) {
        int intLeftTrack = (intTrackIndex*2)+1;
        int intRightTrack = (intTrackIndex*2)+2;
        String strSourceChannel = "1~2";
        String strDestChannel = ((intTrackIndex*2)+1) + "~" + ((intTrackIndex*2)+2);
        String strTrackMap = strSourceChannel + " " + strDestChannel;
        String strType = "Cut";
        String strRef = "I";
        long lDestIn = (Long.parseLong(xmlClip.elementText("Offset"))) * jProjectTranslator.intPreferredSampleRate / 1000000000;
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
            long lSourceIn = (Long.parseLong(xmlTakeReference.elementText("Offset"))) * jProjectTranslator.intPreferredSampleRate/1000000000;
            lDestOut = ((Long.parseLong(xmlTakeReference.elementText("Length"))) * jProjectTranslator.intPreferredSampleRate/1000000000) + lDestIn;
            String strUMID = xmlTakeReference.elementText("ExternalId");
            strUMID = strUMID.substring(32, 64);
            // Need to find the source index from the database with this UMID.
            int intSourceIndex = 0;

            strSQL = "SELECT intIndex FROM PUBLIC.SOURCE_INDEX WHERE strUMID = \'" + strUMID + "\';";
            try {
                st = conn.createStatement();
                ResultSet rs = st.executeQuery(strSQL);
                intSourceIndex = 0;
                if (!(rs.wasNull()) && rs.next()) {
                    if (rs.getInt(1) > 0) {
                        intSourceIndex = rs.getInt(1);
                    }
                }
                
                if (intSourceIndex == 0) {
                    System.out.println("No source reference found for clip " + intClipCounter + " was looking for UMID " + strUMID);
                    return;
                }
                strSQL = "INSERT INTO PUBLIC.EVENT_LIST (intIndex, strType, strRef, intSourceIndex, strTrackMap, intSourceIn, intDestIn, intDestOut, strRemark) VALUES (" +
                    intClipCounter++ + ", \'" + strType + "\',\'" + strRef + "\'," + intSourceIndex + ",\'" + strTrackMap + "\'," + lSourceIn + "," + lDestIn + "," + lDestOut + ",\'" + strRemark + "\') ;";
                int j = st.executeUpdate(strSQL);
                if (j == -1) {
                    System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                }
            } catch (java.sql.SQLException e) {
                System.out.println("Error on SQL while parsing AudioClip " + strSQL + e.toString());
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
    /**
     * These are fade or level markers which are converted in to automation data.
     * VCS stores these as an offset from the start of the clip but AES31 stores them as an offset within the EDL.
     * @param xmlClipFadeMarkers        This is the xml element containing the data.
     * @param intLeftTrack              This is the track within the edl.
     * @param lClipStart                This is the offset for the start of the clip in the EDL in samples.
     * @param st                        This is used to access the database.
     */
    public void parseClipFadeMarkers(Element xmlClipFadeMarkers, int intLeftTrack, long lClipStart, Statement st) {
        Element xmlFadeMarker;
        long lFadePoint;
        double dLevel;
        String strLevel;
        int intRightTrack = intLeftTrack + 1;
        for (Iterator i = xmlClipFadeMarkers.elementIterator("FadeMarker");i.hasNext();) {
            xmlFadeMarker = (Element)i.next();
            lFadePoint = (Long.parseLong(xmlFadeMarker.elementText("Position"))) * jProjectTranslator.intPreferredSampleRate/1000000000;
            lFadePoint = lFadePoint + lClipStart;
            strLevel = xmlFadeMarker.elementText("Offset");
            dLevel = Double.valueOf(strLevel);
            dLevel = (double)Math.round(dLevel * 100) / 100;
            strLevel = String.format(Locale.UK,"%.2f", dLevel);

            try {
                // Need to prevent co-incident fade points
                strSQL = "SELECT COUNT(*) FROM PUBLIC.FADER_LIST WHERE intTrack = " + intLeftTrack + " AND intTime = " + lFadePoint + ";";
                st = conn.createStatement();
                ResultSet rs = st.executeQuery(strSQL);
                rs.next();
                if (rs.getInt(1) > 0) {
                    // Don't add another fade point, no need to check the other channel, Startrack is stereo only.
                    continue;
                }
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
    /**
     * Items on the clipboard refer to the 'hidden takes' which have been parsed in to the SOURCE_INDEX table.
     * We will look through the clipboard here to update the names in the SOURCE_INDEX table
     * The user created the clipname so this could contain illegal characters and more than one
     * clip could have the same name. Both of these potential problems should be fixed.
     * @param xmlClip The AudioClip element to be parsed
     * @param st To access the database
     */
    public void parseClipboardClip(Element xmlClip, Statement st) {
        Element xmlTakeReference;
        String strName = xmlClip.elementText("ClipName");
        int intStart = strName.lastIndexOf("\\");
        if (intStart > 0) {
            strName = strName.substring(intStart + 1, strName.length());
        }
        int intEnd = strName.lastIndexOf(".");
        if (intEnd == -1) {
            intEnd = strName.length();
        }
        strName = strName.substring(0, intEnd);
        strName = strName + "_" + intHiddenClipCounter++;
        strName = strName.replaceAll("[\\/:*?\"<>|%]","_");
        String strURI = strName;
        
        try {
            strName = URLEncoder.encode(strName, "UTF-8");
            strURI = URLEncoder.encode(strURI, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("Error on while trying to encode string" );
            return;
        }
        for (Iterator i = xmlClip.elementIterator("TakeReference");i.hasNext();) {
            xmlTakeReference = (Element)i.next();
            String strUMID = xmlTakeReference.elementText("ExternalId");
            strUMID = strUMID.substring(32, 64);
            // Need to find the source index from the database with this UMID.
            int intSourceIndex = 0;

            strSQL = "SELECT intIndex FROM PUBLIC.SOURCE_INDEX WHERE strUMID = \'" + strUMID + "\';";
            try {
                st = conn.createStatement();
                ResultSet rs = st.executeQuery(strSQL);
                intSourceIndex = 0;
                if (!(rs.wasNull()) && rs.next()) {
                    if (rs.getInt(1) > 0) {
                        intSourceIndex = rs.getInt(1);
                    }
                }
                if (intSourceIndex == 0) {
                    System.out.println("No source reference found for clipboard clip, was looking for UMID " + strUMID);
                    return;
                }
                strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET strName = \'" + strName + "\' , strDestFileName = \'" + strURI + "\' WHERE intIndex = " + intSourceIndex + ";";
                System.out.println("parseClipboardClip " + strSQL);
                int j = st.executeUpdate(strSQL);
                if (j == -1) {
                    System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                }
            } catch (java.sql.SQLException e) {
                System.out.println("Error on SQL while parsing AudioClip " + strSQL + e.toString());
                return;
            }
        }
    }
    public String getInfoText() {
        return "<b>VCS Startrack</b><br>"
                + "This importer can read both .dat files which contain an EDL and the associated audio "
                + "and .aus autosave files which contain only the EDL.<br>";
    }
}
