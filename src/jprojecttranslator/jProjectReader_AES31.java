/*
 * 
 */
package jprojecttranslator;

import java.io.File;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.StringTokenizer;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import java.net.URI;
import java.net.URLDecoder;
import java.sql.ResultSet;
import java.sql.Statement;
import wavprocessor.WAVProcessor;

/**
 * This is a project reader for AES31 (.adl) files
 * @author scobeam
 */
public class jProjectReader_AES31 extends jProjectReader {
    /** This is the xml document object which is used for loading and saving to an xml file.*/
    static Document xmlDocument = DocumentHelper.createDocument();
    File fAudioFolder;
    DateTime dtsCreated;
    public static DateTimeFormatter fmtADLXML = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZZ");
    /**
     * This returns a FileFilter which shows the files this class can read
     * @return FileFilter
     */
    @Override
    public javax.swing.filechooser.FileFilter getFileFilter() {
        javax.swing.filechooser.FileFilter filter = new FileNameExtensionFilter("AES31 (.adl)", "adl");
        return filter;
    }
    /**
     * This loads up an AES31 project in to the database.
     * @return      True if the project was loaded.
     */
    @Override
    protected boolean processProject() {
        intSoundFilesLoaded = 0;
        if (!loadXMLData(fSourceFile)) {
            return false;
        }
        if (parseAES31XML(xmlDocument.getRootElement())) {
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
        System.out.println("AES31 project file loaded");
        return true;
    } 
    /**
     * This loads the AES31 .adl project file which is XML in to an internal xml Document.
     * @param setSourceFile     This is the source file.
     * @return                  Returns true if the file was loaded.
     */
    protected boolean loadXMLData(File setSourceFile) {
        // Initial setting for audio folder is a guess
        fAudioFolder = setSourceFile.getParentFile();
        try {
            SAXReader reader = new SAXReader();
            xmlDocument = reader.read(setSourceFile);
        } catch (DocumentException de) {
            System.out.println("Exception while loading XML data " + de.toString());
            return false;
        } catch (java.net.MalformedURLException e) {
            System.out.println("Exception while loading XML file " + e.toString());
            return false;
        }
        return true;
    }
    /**
     * This tries to parse the xml data from the .adl file in to the database
     * @param setRoot Pass in the root element of the XML data
     * @return true if parse is successful.
     */
    protected boolean parseAES31XML(Element setRoot) {
        Element xmlRoot = setRoot;
        Element xmlVersion = xmlRoot.element("VERSION");
        parseAES31Version(xmlVersion);
        Element xmlProject = xmlRoot.element("PROJECT");
        parseAES31Project(xmlProject);
        Element xmlSequence = xmlRoot.element("SEQUENCE");
        if (!parseAES31Sequence(xmlSequence)) {
            return false;
        }
        Element xmlIndex = xmlRoot.element("SOURCE_INDEX");
        parseAES31Source_Index(xmlIndex);
        Element xmlList = xmlRoot.element("EVENT_LIST");
        parseAES31Event_List(xmlList);
        Element xmlFades = xmlRoot.element("FADER_LIST");
        parseAES31Fader_List(xmlFades);
        // Need to look at the sound files now to get further information
        loadSoundFiles(st, fAudioFolder);
        return true;
        
    }
    /**
     * 
     * @param setVersion
     * @return 
     */
    protected boolean parseAES31Version(Element setVersion) {
        if (setVersion == null || !setVersion.hasContent()) {
            return false;
        }
        String strLine;
        strLine = setVersion.getText();
        String strADLID = "";
        String strADLUID = "";
        String strADLVersion = "";
        String strCreator = "";
        String strCreatorVersion = "";
        Matcher mMatcher;
        
        Pattern pADLID = Pattern.compile("\\(ADL_ID\\)\\s*\"(.+)\"");
        mMatcher = pADLID.matcher(strLine);
            if (mMatcher.find()) {
                strADLID = mMatcher.group(1);
                System.out.println("ADL ID found " + strADLID);
        }
        
        Pattern pADLUID = Pattern.compile("\\(ADL_UID\\)\\s*(.+)");
        mMatcher = pADLUID.matcher(strLine);
            if (mMatcher.find()) {
                strADLUID = mMatcher.group(1);
                System.out.println("ADL UID found " + strADLUID);
        }
            
        Pattern pADLVersion = Pattern.compile("\\(VER_ADL_VERSION\\)\\s*([\\d|\\.]+)");
        mMatcher = pADLVersion.matcher(strLine);
            if (mMatcher.find()) {
                strADLVersion = mMatcher.group(1);
                System.out.println("ADL version found " + strADLVersion);
        }
        Pattern pCreator = Pattern.compile("\\(VER_CREATOR\\)\\s*\"(.+)\"");
        mMatcher = pCreator.matcher(strLine);
            if (mMatcher.find()) {
                strCreator = mMatcher.group(1);
                System.out.println("ADL creator found " + strCreator);
        }
        Pattern pCrtr = Pattern.compile("\\(VER_CRTR\\)\\s*([\\d|\\.|-]+)");
        mMatcher = pCrtr.matcher(strLine);
        if (mMatcher.find()) {
            strCreatorVersion = mMatcher.group(1);
            System.out.println("ADL crtr found " + strCreatorVersion);
        }    
        try {
            strSQL = "INSERT INTO PUBLIC.VERSION (strID, strUID, strADLVersion, strCreator, strCreatorVersion) VALUES (" +
                " \'" + strADLID + " \',\' " + strADLUID + "\',\'" + strADLVersion + "\', \'" + strCreator + "\', \'" + strCreatorVersion + "\') ;";
            int i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
            
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        }
        
//        StringTokenizer stTokens = new StringTokenizer(setVersion.getText(), "\n");
//        while(stTokens.hasMoreTokens()) {
//            strLine = stTokens.nextToken();
//            System.out.println("Checking line " + strLine);
//            //
//            mMatcher = pADLVersion.matcher(strLine);
//            if (mMatcher.find()) {
//                strVersion = mMatcher.group(1);
//                System.out.println("ADL version found " + strVersion);
//            }
//            //
//            mMatcher = pCreator.matcher(strLine);
//            if (mMatcher.find()) {
//                strCreator = mMatcher.group(1);
//                System.out.println("ADL creator found " + strCreator);
//            }
//        }
        return true;
    }
    /**
     * 
     * @param setProject An xml element containing the project text to be parsed
     * @return True if the element is parsed successfully
     */
    protected boolean parseAES31Project(Element setProject) {
        if (setProject == null || !setProject.hasContent()) {
            return false;
        }
        String strLine = setProject.getText();
//        System.out.println("Project string is  " + strLine);
        String strTitle = "";
        String strNotes = "";
        String strCreated = "";
        String strOriginator = "";
        String strClientData = "";
        Matcher mMatcher;
        
        Pattern pPattern = Pattern.compile("\\(PROJ_TITLE\\)\\s*\"(.+)\"");
        mMatcher = pPattern.matcher(strLine);
        if (mMatcher.find()) {
            strTitle = mMatcher.group(1);
            System.out.println("ADL title found " + strTitle);
        }
        
        pPattern = Pattern.compile("\\(PROJ_ORIGINATOR\\)\\s*\"(.+)\"");
        mMatcher = pPattern.matcher(strLine);
        if (mMatcher.find()) {
            strOriginator = mMatcher.group(1);
            System.out.println("ADL originator found " + strTitle);
        }
        
        pPattern = Pattern.compile("\\(PROJ_CREATE_DATE\\)\\s*(.+)");
        mMatcher = pPattern.matcher(strLine);
        if (mMatcher.find()) {
            strCreated = mMatcher.group(1);
            System.out.println("ADL created date before parsing " + strCreated);
            DateTime dtsCreated = fmtADLXML.parseDateTime(mMatcher.group(1));
            strCreated = fmtSQL.print(dtsCreated);
            System.out.println("ADL created date ready for SQL " + strCreated);
        }
        
        pPattern = Pattern.compile("\\(PROJ_NOTES\\)\\s*\"(.+)\"");
        mMatcher = pPattern.matcher(strLine);
        if (mMatcher.find()) {
            strNotes = mMatcher.group(1);
            System.out.println("ADL notes found " + strTitle);
        }
        
        pPattern = Pattern.compile("\\(PROJ_CLIENT_DATA\\)\\s*\"(.+)\"");
        mMatcher = pPattern.matcher(strLine);
        if (mMatcher.find()) {
            strClientData = mMatcher.group(1);
            System.out.println("ADL client data found " + strTitle);
        }
        
        // DateTime dtsCreated = fmtADLXML.parseDateTime(xmlCreator.elementText("SystemTime"));
        // String strCreated = fmtSQL.print(dtsCreated);
        if (strCreated.length() == 0) {
            return false;
        }
        try {
            strOriginator = URLEncoder.encode(strOriginator, "UTF-8");
            strTitle = URLEncoder.encode(strTitle, "UTF-8");
            strNotes = URLEncoder.encode(strNotes, "UTF-8");
            strClientData = URLEncoder.encode(strClientData, "UTF-8");
            strSQL = "INSERT INTO PUBLIC.PROJECT (intIndex, strTitle, strNotes, dtsCreated, strOriginator, strClientData) VALUES (" +
                "1, \'"+strTitle+" \', \'"+strNotes+" \',\'"+strCreated+"\',\' " + strOriginator + "\', \' " + strClientData + "\') ;";
            int i = st.executeUpdate(strSQL);
            if (i == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        } catch(java.io.UnsupportedEncodingException e) {

        }
        return true;
    }
    /**
     * 
     * @param setSequence, an xml element containing the sequence to be parsed
     * @return True if the element is parsed successfully
     */
    protected boolean parseAES31Sequence(Element setSequence) {
        if (setSequence == null || !setSequence.hasContent()) {
            return false;
        }
        String strLine = setSequence.getText();
        Matcher mMatcher;
        
        Pattern pPattern = Pattern.compile("\\(SEQ_SAMPLE_RATE\\)\\s*S(\\d+)");
        mMatcher = pPattern.matcher(strLine);
        if (mMatcher.find()) {
            int intTempRate = Integer.parseInt(mMatcher.group(1));
            if (intTempRate == 44100 || intTempRate == 48000) {
                jProjectTranslator.intProjectSampleRate = intTempRate;
                System.out.println("SEQUENCE sample rate found " + jProjectTranslator.intProjectSampleRate);
            } else {
                oProjectTranslator.writeStringToPanel("The sample rate found in the AES31 project " + intTempRate + " is not supported. " );
                return false;
            }
            
            if (jProjectTranslator.intPreferredSampleRate != jProjectTranslator.intProjectSampleRate) {
                sampleRateChange();

            }
        }
        
        pPattern = Pattern.compile("\\(SEQ_FRAME_RATE\\)\\s*([\\d|\\.]+)");
        mMatcher = pPattern.matcher(strLine);
        if (mMatcher.find()) {
            double dTempRate = Double.parseDouble(mMatcher.group(1));
            if (dTempRate == 24 || dTempRate == 25 || dTempRate == 29.97 || dTempRate == 30) {
                jProjectTranslator.dProjectFrameRate = dTempRate;
                System.out.println("SEQUENCE frame rate found " + jProjectTranslator.dProjectFrameRate);
            } else {
                oProjectTranslator.writeStringToPanel("The frame rate found in the AES31 project " + dTempRate + " is not supported. " );
                return false;
            }
            
            if (jProjectTranslator.dPreferredFrameRate != jProjectTranslator.dProjectFrameRate) {
                frameRateChange();
            }
        }
        
        
        return true;
    }
    /**
     * This will parse the SOURCE_INDEX section of an adl file in to the database. It contains information about the sound files which are used.
     * @param setSource An Element containing the (Index) information.
     * @return true if the string was parsed successfully.
     */
    protected boolean parseAES31Source_Index(Element setSource) {
        if (setSource == null || !setSource.hasContent()) {
            return false;
        }
        String strLine = setSource.getText();
        String strName, strURI, strFileName, strUMID, strDestFileName;
        long lLength, lFileTCOffset;
        int intIndex;
        Matcher mMatcher;
        Pattern pPattern;
        // 0001 (F) "URL:file://localhost/d:/Projects/USER1657.wav" _  00.00.00.00/0000  _  "USER1657"  N            
        pPattern = Pattern.compile("\\s*(\\d\\d\\d\\d)"
                    + "\\s*\\(\\w\\)\\s*"
                    + "\"(.*?)\"\\s*"
                    + "(\".*?\"|_)\\s*"
                    + "(\\d\\d\\D\\d\\d\\D\\d\\d\\D\\d\\d\\D\\d\\d\\d\\d|_)\\s*"
                    + "(\\d\\d\\D\\d\\d\\D\\d\\d\\D\\d\\d\\D\\d\\d\\d\\d|_)\\s*"
                    + "(\".*?\"|_)\\s*"
                    + "(\\w)");
        mMatcher = pPattern.matcher(strLine);  
        while (mMatcher.find()) {
            System.out.println("SOURCE_INDEX entry found " + mMatcher.group(1) + " " + mMatcher.group(2) + " " + mMatcher.group(3) + " " + mMatcher.group(4) + " "
                        + "" + mMatcher.group(5) + " " + mMatcher.group(6) + " " + mMatcher.group(7));
            // 0001 (F) "URL:file://localhost/d:/Projects/USER1657.wav" _  00.00.00.00/0000  _  "USER1657"  N            
            try {
                // URL:file://localhost//home/scobeam/Music//offset_test_9216E5BD.wav
                // Strip off leading and trailing quotes
                strName = mMatcher.group(6);
                strName = strName.substring(1, strName.length()-1);
                strName = strName.replaceAll("[\\/:*?\"<>|%&]","_");
                strName = URLEncoder.encode(strName, "UTF-8");
                strUMID = mMatcher.group(3);
                if (strUMID.length() > 2) {
                    strUMID = strUMID.substring(1, strUMID.length()-1);
                } else {
                    strUMID = "";
                }
                
                strUMID = URLEncoder.encode(strUMID, "UTF-8");
                
                // Get the raw URI string
                strURI = mMatcher.group(2);
                // Strip off the leading URL: if it exists
                if (strURI.startsWith("URL:")) {
                    strURI = strURI.substring(4, strURI.length());
                }
                // The URI field in an AES31 adl file is not URL encoded so we might not be able to decode it with URI unless we URI encode it first, we want to use the getPath() method.
                strURI = URLEncoder.encode(strURI, "UTF-8");
                // Make it in to a URI
                URI uriTemp = new URI(strURI);
                // Use the getPath() method
                strURI = uriTemp.getPath();
                // Decode the path and make it in to a file
                File fTemp = new File(URLDecoder.decode(strURI, "UTF-8"));
                // The source file name is read from the URI
                strFileName = fTemp.getName();
                // This is encoded for insertion to the database
                strDestFileName = strFileName;
                strDestFileName = strDestFileName.replaceAll("[\\/:*?\"<>|%&]","_");
                strFileName = URLEncoder.encode(strFileName, "UTF-8");
                strDestFileName = URLEncoder.encode(strDestFileName, "UTF-8");
                lLength = getADLTimeLong(mMatcher.group(5));
                lFileTCOffset = getADLTimeLong(mMatcher.group(4));
                intIndex = Integer.parseInt(mMatcher.group(1));
                strSQL = "INSERT INTO PUBLIC.SOURCE_INDEX (intIndex, strType, strDestFileName, strUMID, intLength, strName, intFileOffset, intTimeCodeOffset, strSourceFile, intCopied, intVCSInProject, intFileSize) VALUES (" +
                    intIndex + ", \'F\',\'" + strDestFileName + "\',\'" + strUMID + "\', " + lLength + ", \'" + strName + "\', 0, " + lFileTCOffset + ", \'" + strFileName + "\', 0, 0, 0) ;";
                int i = st.executeUpdate(strSQL);
                if (i == -1) {
                    System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                }

            } catch(java.io.UnsupportedEncodingException e) {
                System.out.println("Exception " + e.toString());
            } catch (java.sql.SQLException e) {
                System.out.println("Error on SQL " + strSQL + e.toString());
            } catch (java.net.URISyntaxException e) {
                System.out.println("URI encoding exception  " + e.toString());
            }
        }
        return true;
    }
    /**
     * This will take a series of  AES31 events in a string and split them up to be processed.
     * @param setEvent An Element containing a series of AES31 events.
     * @return true if the parsing was successful.
     */
    protected boolean parseAES31Event_List(Element setEvent) {
        if (setEvent == null || !setEvent.hasContent()) {
            return false;
        }
        String strLine = setEvent.getText();
        String[] arrLines = strLine.split("(?=\\s*\\(Entry\\)\\s*\\d\\d\\d\\d)");
        for(int i =0; i < arrLines.length ; i++) {
//            System.out.println(arrLines[i]);
            parseAES31Event(arrLines[i]);
        }
        return true;

        
    }
    /**
     * This will parse the entries from a single 'EVENT' in to the database including (Cut), (Infade), (Outfade) and (Rem)
     * @param strEvent A string containing a single AES31 event.
     * @return true if the parsing was successful.
     */
    protected boolean parseAES31Event(String strEvent){
        Matcher mMatcher;
        Pattern pPattern;
        int intFinds = 0;
        int intIndex = 0, intSourceIndex = 0;
        long lSourceIn, lDestIn, lDestOut, lInFade, lOutFade;
        String strType, strRef, strTrackMap, strRemark, strInFade, strOutFade, strGain;
        pPattern = Pattern.compile("\\s*\\(Entry\\)\\s*(\\d\\d\\d\\d)");
        mMatcher = pPattern.matcher(strEvent);  
        if (mMatcher.find()) {
            intIndex = Integer.parseInt(mMatcher.group(1));
            intFinds++;
            System.out.println("EVENT_LIST entry found " + intIndex);
        }
        // (Cut) I 0002  1~2 1~2  00.00.03.15/1152  00.00.00.00/0000  00.00.10.06/0096  R
//        pPattern = Pattern.compile("\\(Cut\\)\\s*(.*?)\\s*(\\d{4})\s*(\\d~\\d|\\d)\\s*(\\d~\\d|\\d)\\s*(\\d{4}\\d{4}\\d{4}\\d{4})\\s*(\\d{4}\\d{4}\\d{4}\\d{4})\\s*(\\d{4}\\d{4}\\d{4}\\d{4})\\s*(\\w*)");
        // pPattern = Pattern.compile("\\(Cut\\)\\s*(.*?)\\s*(\\d{4})\\s*(\\d~\\d|\\d)\\s*(\\d~\\d|\\d)\\s*(\\d{4}\\d{4}\\d{4}\\d{4})\\s*(\\d{4}\\d{4}\\d{4}\\d{4})\\s*(\\d{4}\\d{4}\\d{4}\\d{4})\\s*(\\w*)");
        pPattern = Pattern.compile("\\(Cut\\)\\s*(.*?)\\s*(\\d{4})" // Cut index
                + "\\s*(\\d*~\\d*|\\d*)" // Source channel(s)
                + "\\s*(\\d*~\\d*|\\d*)" // Destination channel(s)
                + "\\s*(\\d\\d\\D\\d\\d\\D\\d\\d\\D\\d\\d\\D\\d\\d\\d\\d)" // Source in time
                + "\\s*(\\d\\d\\D\\d\\d\\D\\d\\d\\D\\d\\d\\D\\d\\d\\d\\d)" // Destination in time
                + "\\s*(\\d\\d\\D\\d\\d\\D\\d\\d\\D\\d\\d\\D\\d\\d\\d\\d)" // Destination out time
                + "");
        mMatcher = pPattern.matcher(strEvent);  
        if (mMatcher.find()) {
            intFinds++;
            strType = "Cut";
            strRef = mMatcher.group(1);
            intSourceIndex = Integer.parseInt(mMatcher.group(2));
            strTrackMap = mMatcher.group(3) + " " + mMatcher.group(4);
            lSourceIn = getADLTimeLong(mMatcher.group(5));
            lDestIn = getADLTimeLong(mMatcher.group(6));
            lDestOut = getADLTimeLong(mMatcher.group(7));
            System.out.println("EVENT_LIST (Cut) entry found 2 " + mMatcher.group(2) + " 3 " + mMatcher.group(3) 
                    + " 4 " + mMatcher.group(4) + " 5 " + mMatcher.group(5) + " 6 " + mMatcher.group(6) + " 7 " + mMatcher.group(7));
            
        } else {
            strType = "Cut";
            strRef = "";
            strTrackMap = "";
            lSourceIn = 0;
            lDestIn = 0;
            lDestOut = 0;
        }
        if (intFinds < 2) {
            return false;
        }
        pPattern = Pattern.compile("\\(Rem\\)\\s*NAME\\s*\"(.+)\"");
        mMatcher = pPattern.matcher(strEvent);  
        if (mMatcher.find()) {
            strRemark = mMatcher.group(1);
        } else {
            strRemark = "";
        }
        pPattern = Pattern.compile("\\(Infade\\)\\s*(\\d\\d\\D\\d\\d\\D\\d\\d\\D\\d\\d\\D\\d\\d\\d\\d)\\s*"
                + "((CURVE|LIN)\\s*"
                + "([+-]?\\d+\\.?\\d*|_)\\s*"
                + "([+-]?\\d+\\.?\\d*|_)\\s*"
                + "([+-]?\\d+\\.?\\d*|_))");
        mMatcher = pPattern.matcher(strEvent);  
        if (mMatcher.find()) {
            strInFade = mMatcher.group(2);
            lInFade = getADLTimeLong(mMatcher.group(1));
            System.out.println("In fade entry found 1 " + mMatcher.group(1) + " 2 " + mMatcher.group(2));
        } else {
            strInFade = "";
            lInFade = 0;
        }
        
        pPattern = Pattern.compile("\\(Outfade\\)\\s*(\\d\\d\\D\\d\\d\\D\\d\\d\\D\\d\\d\\D\\d\\d\\d\\d)\\s*"
                + "((CURVE|LIN)\\s*"
                + "([+-]?\\d+\\.?\\d*|_)\\s*"
                + "([+-]?\\d+\\.?\\d*|_)\\s*"
                + "([+-]?\\d+\\.?\\d*|_))");
        mMatcher = pPattern.matcher(strEvent);  
        if (mMatcher.find()) {
            strOutFade = mMatcher.group(2);
            lOutFade = getADLTimeLong(mMatcher.group(1));
            System.out.println("Out fade entry found 1 " + mMatcher.group(1) + " 2 " + mMatcher.group(2));
        } else {
            strOutFade = "";
            lOutFade = 0;
        }
        try {
            strRemark = URLEncoder.encode(strRemark, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("Error on while trying to encode string" );
            return false;
        }
        // Looking for optional gain entry which should look something like this...
        // (Gain) _ +5.00
        pPattern = Pattern.compile("\\(Gain\\)\\s*_\\s*([+-]?\\d+\\.?\\d*|_)");
        mMatcher = pPattern.matcher(strEvent);  
        if (mMatcher.find()) {
            strGain = mMatcher.group(1);
        } else {
            strGain = "";
        }
        try {
                strSQL = "INSERT INTO PUBLIC.EVENT_LIST (intIndex, strType, strRef, intSourceIndex, strTrackMap, intSourceIn, intDestIn, intDestOut, strRemark"
                        + ", strInFade, intInFade, strOutFade, intOutFade, intRegionIndex, intLayer, intTrackIndex, bOpaque, strGain) VALUES (" +
                    intIndex + ", \'" + strType + "\',\'" + strRef + "\'," + intSourceIndex + ",\'" + strTrackMap + ""
                        + "\'," + lSourceIn + "," + lDestIn + "," + lDestOut + ",\'" + strRemark + "\', "
                        + "\'" + strInFade + "\', " + lInFade + ", \'" + strOutFade + "\', " + lOutFade + ", " + intIndex + ""
                        + ", 0, 0, \'N\', \'" + strGain + "\') ;";
                int j = st.executeUpdate(strSQL);
                if (j == -1) {
                    System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                }

                
            } catch (java.sql.SQLException e) {
                System.out.println("Error on SQL " + strSQL + e.toString());
                return false;
            }        
        
        
        return true;
    }
    protected boolean parseAES31Fader_List(Element xmlFades) {
        if (xmlFades == null || !xmlFades.hasContent()) {
            return false;
        }
        String strFades = xmlFades.getText();
        Matcher mMatcher;
        Pattern pPattern;
        pPattern = Pattern.compile("\\(FP\\)\\s*(\\d+)\\s*(\\d\\d\\D\\d\\d\\D\\d\\d\\D\\d\\d\\D\\d\\d\\d\\d)\\s*([+-]?\\d+\\.?\\d*|_)");
        mMatcher = pPattern.matcher(strFades);
        int j;  
        while (mMatcher.find()) {
            
            try {
                strSQL = "INSERT INTO PUBLIC.FADER_LIST (intTrack, intTime, strLevel) VALUES (" +
                    mMatcher.group(1) + ", " + getADLTimeLong(mMatcher.group(2)) + ",\'" + mMatcher.group(3) + "\') ;";
                        j = st.executeUpdate(strSQL);
                        if (j == -1) {
                            System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                        }
            } catch (java.sql.SQLException e) {
                System.out.println("Error on SQL " + strSQL + e.toString());
            }
            System.out.println("FADER_LIST entry found " + mMatcher.group(1) + " " + mMatcher.group(2) + " " + mMatcher.group(3));
        }
        return true;
    }
    public static long getADLTimeLong (String strADLTime) {
        // 10.00.12.06/0256
        // \d\d\D\d\d\D\d\d\D\d\d\D\d\d\d\d
        long lSamples = 0;
        Matcher mMatcher;
        Pattern pPattern;
        pPattern = Pattern.compile("(\\d\\d)(\\D)(\\d\\d)(\\D)(\\d\\d)(\\D)(\\d\\d)(\\D)(\\d\\d\\d\\d)");
        mMatcher = pPattern.matcher(strADLTime); 
        if (!mMatcher.find()) {
            return -1;
        }
        int intHours = Integer.parseInt(mMatcher.group(1));
        String strSep1 = mMatcher.group(2);
        int intMinutes = Integer.parseInt(mMatcher.group(3));
        int intSeconds = Integer.parseInt(mMatcher.group(5));
        int intFrames = Integer.parseInt(mMatcher.group(7));
        String strSep2 = mMatcher.group(8);
        int intSamples = Integer.parseInt(mMatcher.group(9));
        String strSep3 = mMatcher.group(6);
        int intSampleRate = 0;
        if (strSep2.equalsIgnoreCase("|")) {
            intSampleRate = 44100;
        } 
        if (strSep2.equalsIgnoreCase("/")) {
            intSampleRate = 48000;
        }
        if (intSampleRate == 0) {
            return -1;
        }
        
        double dFrameRate = 0;
        if (strSep1.equalsIgnoreCase("|")) {
            dFrameRate = 30;
        }
        if (strSep1.equalsIgnoreCase("=")) {
            dFrameRate = 24;
        }
        if (strSep1.equalsIgnoreCase(".")) {
            dFrameRate = 25;
        }
        if (strSep1.equalsIgnoreCase(":")) {
            dFrameRate = 29.97;
        }
        if (dFrameRate == 0) {
            return -1;
        }
        if (dFrameRate%5 == 0 || dFrameRate == 24) {
            // Frame rate 24, 25 or 30, simple maths
            int intFrameRate = (int)dFrameRate;
            lSamples = intHours*60*60*intSampleRate;
            lSamples = lSamples + intMinutes*60*intSampleRate;
            lSamples = lSamples + intSeconds*intSampleRate;
            lSamples = lSamples + intFrames*intSampleRate/intFrameRate;
            lSamples = lSamples + intSamples;
            return lSamples;
        }
        if (dFrameRate == 29.97) {
            // Frame rate 29.97, hard maths, first of all calculate the frame number, convert to samples and then add remaining samples later
            //CONVERT DROP FRAME TIMECODE TO A FRAME NUMBER
            //Code by David Heidelberger, adapted from Andrew Duncan
            //Given ints called intHours, intMinutes, intSeconds, frames, and a double called framerate

            int intDropFrames = (int)java.lang.Math.round(dFrameRate*.066666); //Number of drop frames is 6% of framerate rounded to nearest integer
            int intFrameRate = (int)java.lang.Math.round(dFrameRate); //We don't need the exact framerate anymore, we just need it rounded to nearest integer

            int hourFrames = intFrameRate*60*60; //Number of frames per hour (non-drop)
            int minuteFrames = intFrameRate*60; //Number of frames per minute (non-drop)
            int totalMinutes = (60*intHours) + intMinutes; //Total number of minutes
            long lFrameNumber = ((hourFrames * intHours) + (minuteFrames * intMinutes) + (intFrameRate * intSeconds) + intFrames) - (intDropFrames * (totalMinutes - (totalMinutes / 10)));            
            lSamples = (long)java.lang.Math.round(lFrameNumber * intSampleRate / dFrameRate);
            lSamples = lSamples + intSamples;
            return lSamples;
            
        }
        return -1;

    } 
    /**
     * Read data from sound files which are referenced in the project if possible.
     * The sound files can contain useful information such as duration, USID etc.
     * Files which are written out as part of an AES31 project have to be BWAVs, i.e. they must have a bext chunk.
     * @param st                This allows the database to be updated.
     * @param fAudioFolder      This is the folder on the system where the files should be found.
     * @return                  Return the number of files found.
     */
    protected int loadSoundFiles(Statement st, File fAudioFolder) {
        try {
            strSQL = "SELECT intIndex, strName, strSourceFile, intLength, strDestFileName FROM PUBLIC.SOURCE_INDEX ORDER BY intIndex;";
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(strSQL);
            String strSourceFile, strName, strUMID, strDestFileName, strType;
            File fLocalSourceFile, fTempLocalSourceFile;
            long lIndicatedFileSize, lSampleRate, lSourceFileSize, lTimeCodeOffset;
            double dDuration, dSourceSamples;
            int intSourceIndex, intChannels;
            while (rs.next()) {
                // Loop through the SOURCE_INDEX table and try to find out more about each file by reading data from the actual sound file (if we can find it)
                intSourceIndex = rs.getInt(1);
                strName = URLDecoder.decode(rs.getString(2), "UTF-8");
                strSourceFile = URLDecoder.decode(rs.getString(3), "UTF-8");
                strDestFileName = URLDecoder.decode(rs.getString(5), "UTF-8");
                dSourceSamples = rs.getDouble(4);
                fTempLocalSourceFile = new File(fAudioFolder, strSourceFile);
                if (!fTempLocalSourceFile.exists()) {
                    File[] files = fAudioFolder.listFiles();
                    for (File file : files) {
                        if (file.isDirectory()) {
                            fTempLocalSourceFile = new File(file, strSourceFile);
                            if (fTempLocalSourceFile.exists()) {
                                break;
                            }
                        }
                    }
                }
                fLocalSourceFile = fTempLocalSourceFile;
                tempWAVProc = new WAVProcessor();
                tempWAVProc.setSrcFile(fLocalSourceFile);
                tempWAVProc.setMultipart(false);
                if (fLocalSourceFile.exists()) {
                    System.out.println("Source file " + fLocalSourceFile + " found");
                } else {
                    System.out.println("Source file " + fLocalSourceFile + " not found");
                }
                if (fLocalSourceFile.exists() && fLocalSourceFile.canRead() && tempWAVProc.readFile(0,fLocalSourceFile.length())) {
                    lIndicatedFileSize = tempWAVProc.getIndicatedFileSize();
                    lSampleRate = tempWAVProc.getSampleRate();
                    dDuration =  tempWAVProc.getDuration();
                    intChannels = tempWAVProc.getNoOfChannels();
                    if (dSourceSamples < 2) {
                        dSourceSamples = tempWAVProc.getNoOfSamples()/intChannels;
                    }
                    strType = tempWAVProc.getFileType() + " " + tempWAVProc.getBitsPerSample() + " bit ";
                    if (tempWAVProc.hasBextChunk()) {
                        strType = "B-" + strType;
                    }
                    // Need to update the destination file extension, wav, rf64 or w64 but avoid name clashes, e.g. you could have three different files, test.wav, test.rf64, test.w64
                    if (jProjectTranslator.intSave64BitFilesAs == 0){
                        // Keep original 64 bit format
                        if (tempWAVProc.getFileType().equalsIgnoreCase("rf64") && !strDestFileName.toLowerCase().endsWith(".wav")) {
                            // RF64 file without wav extension, need to add it.
                            strDestFileName = strDestFileName + ".wav";
                        }
                        if (tempWAVProc.getFileType().equalsIgnoreCase("w64") && jProjectTranslator.bAlwaysUseWavExt && !strDestFileName.toLowerCase().endsWith(".wav")) {
                            // W64 file doesn't end in .wav but user always wants to always have .wav at end
                            strDestFileName = strDestFileName + ".wav";
                        }
                    }
                    if (tempWAVProc.getFileType().equalsIgnoreCase("wav")) {
                        if (!strDestFileName.toLowerCase().endsWith(".wav")) {
                            strDestFileName = strDestFileName + ".wav";
                        }
                    }
                    // Always save as RF64
                    if (jProjectTranslator.intSave64BitFilesAs == 1) {
                            if (!strDestFileName.toLowerCase().endsWith(".wav")) {
                            strDestFileName = strDestFileName + ".wav";
                        }
                    }
                    // Always save w64
                    if (!jProjectTranslator.bAlwaysUseWavExt && jProjectTranslator.intSave64BitFilesAs == 2 && tempWAVProc.getFileType().equalsIgnoreCase("RF64") && !strDestFileName.toLowerCase().endsWith(".w64")) {
                        strDestFileName = strDestFileName + ".w64";
                    } 
                    if (jProjectTranslator.bAlwaysUseWavExt && jProjectTranslator.intSave64BitFilesAs == 2 && !strDestFileName.toLowerCase().endsWith(".wav")) {
                        strDestFileName = strDestFileName + ".wav";
                    }  
                    strDestFileName = URLEncoder.encode(strDestFileName, "UTF-8");
                    strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET intIndicatedFileSize = " + lIndicatedFileSize + ", intSampleRate =  " + lSampleRate + ", dDuration =  " + dDuration + ", intChannels = " + intChannels
                            + ", intLength = " + dSourceSamples + ", strDestFileName = \'"
                            + strDestFileName + "\', strType = \'" + strType + "\' WHERE intIndex = " + intSourceIndex + ";";
                    int i = st.executeUpdate(strSQL);
                    if (i == -1) {
                        System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                    }
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
                        strUMID = jProjectTranslator.getNewUSID();
                        tempWAVProc.setBextOriginatorRef(strUMID);
                        strUMID = URLEncoder.encode(strUMID, "UTF-8");
                        strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET strUMID = \'" + strUMID + "\' WHERE intIndex = " + intSourceIndex + ";";
                        i = st.executeUpdate(strSQL);
                        if (i == -1) {
                            System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                        }
                    }
                    if (tempWAVProc.getBextTimeCodeOffset() > 0) {
                        lTimeCodeOffset = tempWAVProc.getBextTimeCodeOffset();
                        System.out.println("Timecode ref from source file is " +  lTimeCodeOffset);
                        strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET intTimeCodeOffset = " + lTimeCodeOffset + " WHERE intIndex = " + intSourceIndex + ";";
                        i = st.executeUpdate(strSQL);
                        if (i == -1) {
                            System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                        }
                    }
                    
                    lWAVProcessors.add(tempWAVProc);
                    intSoundFilesLoaded++;
                    setChanged();
                    notifyObservers();
                }
            }    
                
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("Error on decoding at " + strSQL + e.toString());
        }
        System.out.println(" " + intSoundFilesLoaded + " sound files have been read from source file");
        return intSoundFilesLoaded;
    }  
    @Override
    public String getInfoText() {
        return "<b>AES31</b><br>"
                + "This importer will read an .adl (Audio Decision List) file which contains the EDL.<br>"
                + "If the required audio files have been found these will be read to acquire additional information.<br>"
                + "<br>";
    }
}
