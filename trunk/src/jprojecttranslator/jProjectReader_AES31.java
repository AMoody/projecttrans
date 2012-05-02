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

/**
 * This is a project reader for AES31 (.adl) files
 * @author scobeam
 */
public class jProjectReader_AES31 extends jProjectReader {
    /** This is the xml document object which is used for loading and saving to an xml file.*/
    static Document xmlDocument = DocumentHelper.createDocument();
    DateTime dtsCreated;
    public static DateTimeFormatter fmtADLXML = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZZ");
    /**
     * This returns a FileFilter which shows the files this class can read
     * @return FileFilter
     */
    public javax.swing.filechooser.FileFilter getFileFilter() {
        javax.swing.filechooser.FileFilter filter = new FileNameExtensionFilter("AES31 (.adl)", "adl");
        return filter;
    }
    /**
     * This loads up an AES31 project in to the database.
     * @return      True if the project was loaded.
     */
    protected boolean processProject() {
        intSoundFilesLoaded = 0;
        if (!loadXMLData(fSourceFile)) {
            return false;
        }
        if (parseAES31XML(xmlDocument.getRootElement())) {
            System.out.println("XML parsed in to database");
            oProjectTranslator.writeStringToPanel("Project loaded.");
        } else {
            System.out.println("Failed to parse XML data");
            oProjectTranslator.writeStringToPanel("Failed to parse XML source data correctly");
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
        return true;
        
    }
    protected boolean parseAES31Version(Element setVersion) {
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
        Pattern pCrtr = Pattern.compile("\\(VER_CRTR\\)\\s*([\\d|\\.]+)");
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
    protected boolean parseAES31Project(Element setProject) {
        String strLine = setProject.getText();
        System.out.println("Project string is  " + strLine);
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
    protected boolean parseAES31Sequence(Element setSequence) {
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
    protected boolean parseAES31Source_Index(Element setSource) {
        String strLine = setSource.getText();
        String strName, strURI, strFileName, strUMID;
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
                
                strName = URLEncoder.encode(mMatcher.group(6), "UTF-8");
                strURI = URLEncoder.encode(mMatcher.group(2), "UTF-8");
                strUMID = URLEncoder.encode(mMatcher.group(3), "UTF-8");
                strFileName = strURI;
                lLength = getADLTimeLong(mMatcher.group(5));
                lFileTCOffset = getADLTimeLong(mMatcher.group(4));
                intIndex = Integer.parseInt(mMatcher.group(1));
                strSQL = "INSERT INTO PUBLIC.SOURCE_INDEX (intIndex, strType, strDestFileName, strUMID, intLength, strName, intFileOffset, intTimeCodeOffset, strSourceFile, intCopied, intVCSInProject, intFileSize) VALUES (" +
                    intIndex + ", \'F\',\'" + strURI + "\',\'" + strUMID + "\', " + lLength + ", \'" + strName + "\', 0, " + lFileTCOffset + ", \'" + strFileName + "\', 0, 0, 0) ;";
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
        return true;
    }
    protected boolean parseAES31Event_List(Element setEvent) {
        String strLine = setEvent.getText();
        String[] arrLines = strLine.split("(?=\\s*\\(Entry\\)\\s*\\d\\d\\d\\d)");
        for(int i =0; i < arrLines.length ; i++) {
//            System.out.println(arrLines[i]);
            parseAES31Event(arrLines[i]);
        }
        return true;

        
    }
    protected boolean parseAES31Event(String strEvent){
        Matcher mMatcher;
        Pattern pPattern;
        int intFinds = 0;
        int intIndex = 0, intSourceIndex = 0;
        long lSourceIn, lDestIn, lDestOut, lInFade, lOutFade;
        String strType, strRef, strTrackMap, strRemark, strInFade, strOutFade;
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
                + "\\s*(\\d~\\d|\\d)" // Source channel(s)
                + "\\s*(\\d~\\d|\\d)" // Destination channel(s)
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
        
        try {
                strSQL = "INSERT INTO PUBLIC.EVENT_LIST (intIndex, strType, strRef, intSourceIndex, strTrackMap, intSourceIn, intDestIn, intDestOut, strRemark"
                        + ", strInFade, intInFade, strOutFade, intOutFade, intRegionIndex, intLayer, intTrackIndex, bOpaque) VALUES (" +
                    intIndex + ", \'" + strType + "\',\'" + strRef + "\'," + intSourceIndex + ",\'" + strTrackMap + ""
                        + "\'," + lSourceIn + "," + lDestIn + "," + lDestOut + ",\'" + strRemark + "\', "
                        + "\'" + strInFade + "\', " + lInFade + ", \'" + strOutFade + "\', " + lOutFade + ", " + intIndex + ""
                        + ", 0, 0, \'N\') ;";
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
}
