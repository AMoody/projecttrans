/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jprojecttranslator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Iterator;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 *
 * @author arth
 */
public class jProjectWriter_AES31 extends jProjectWriter {
    public static DateTimeFormatter fmtADLXML = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZZ");
    
    /*
     * This returns a FileFilter which this class can read
     */
    public javax.swing.filechooser.FileFilter getFileFilter() {
        javax.swing.filechooser.FileFilter filter = new FileNameExtensionFilter("AES31 (.adl)", "adl");
        return filter;
    } 
    protected boolean processProject() {
        System.out.println("AES31 writer thread running");
        writeURIs ();
        /**
        * Next step is to create an ADL file and write the output.
        */
        writeADLFile(fDestFile, st);
        oProjectTranslator.messagePanel.writeString("ADL file written");
        writeAudioFiles ();
        oProjectTranslator.messagePanel.writeString("Finished");
        System.out.println("AES31 writer thread finished");
        return true;
    }
    /* This method fills in the URI column in the SOURCE_INDEX table
     * 
     */
    protected boolean writeURIs () {
        String strURI = fDestFolder.toString();
        strURI = strURI.replaceAll("\\\\", "/");
        strURI = "URL:file://localhost/" + strURI;
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
                strName = strURI + "/" + strName;
                strName = URLEncoder.encode(strName, "UTF-8");
                strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET strURI = \'" + strName + "\' WHERE intIndex = " + intSourceIndex + ";";
                i = st.executeUpdate(strSQL);
                if (i == -1) {
                    System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                }
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("Error on decoding at " + strSQL + e.toString());
        }
        
        return true;
    }
    private boolean writeADLFile(File setDestFile, Statement st) {
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
            strADLText = strADLText + str8Space + "(SEQ_SAMPLE_RATE)  S" + jProjectTranslator.intSampleRate + "\n";
            String strFrameRate;
            if (jProjectTranslator.dFrameRate%5 == 0) {
                strFrameRate = "" + (java.lang.Math.round(jProjectTranslator.dFrameRate));
            } else {
                strFrameRate = "" + jProjectTranslator.dFrameRate;
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
                strTimeCodeOffset = getADLTimeString(rs.getLong(6), jProjectTranslator.intSampleRate, jProjectTranslator.dFrameRate);
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
                strSourceIn = getADLTimeString(rs.getInt(4) + rs.getInt(8), jProjectTranslator.intSampleRate, jProjectTranslator.dFrameRate);
                strDestIn = getADLTimeString(rs.getInt(5), jProjectTranslator.intSampleRate, jProjectTranslator.dFrameRate);
                strDestOut = getADLTimeString(rs.getInt(6), jProjectTranslator.intSampleRate, jProjectTranslator.dFrameRate);
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
                strDestIn = getADLTimeString(rs.getInt(2), jProjectTranslator.intSampleRate, jProjectTranslator.dFrameRate);
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
    protected boolean writeAudioFiles () {
        /** Now to write out the sound files
         * 
         */
        
        String strUMID;
        String strURI;
        File fDestFile;
        int intSourceIndex;
        BWFProcessor tempBWFProcessor;
        ResultSet rs;
         try {
             Iterator itr = lBWFProcessors.iterator();
             while(itr.hasNext()) {
                 tempBWFProcessor = (BWFProcessor)itr.next();
                 strUMID = URLEncoder.encode(tempBWFProcessor.getBextOriginatorRef(), "UTF-8");
                 strSQL = "SELECT strURI FROM PUBLIC.SOURCE_INDEX WHERE strUMID = \'" + strUMID + "\';";
                 st = conn.createStatement();
                 rs = st.executeQuery(strSQL);
                 rs.next();
                 strURI = URLDecoder.decode(rs.getString(1), "UTF-8");
                 strURI = strURI.substring(21, strURI.length());
                 fDestFile = new File(strURI);
                 if (fDestFile.exists()) {
                     continue;
                 }
                 System.out.println("Starting audio file write on dest file " + strURI);
                 oProjectTranslator.messagePanel.writeString("Writing audio file " + strURI);
        
                 tempBWFProcessor.addObserver(this);
                 tempBWFProcessor.writeFile(fDestFile);
                 tempBWFProcessor.deleteObserver(this);
             }
             
//             strSQL = "SELECT intIndex, strURI FROM PUBLIC.SOURCE_INDEX ;";
//            st = conn.createStatement();
//            ResultSet rs = st.executeQuery(strSQL);
//            while (rs.next()) {
//                intSourceIndex = rs.getInt(1);
//                strURI = URLDecoder.decode(rs.getString(2), "UTF-8");
//                strURI = strURI.substring(21, strURI.length());
//                fDestFile = new File(strURI);
//                if (fDestFile.exists()) {
//                    continue;
//                }
//                System.out.println("Starting audio file write on  " + intSourceIndex + " dest file " + strURI);
//                ((BWFProcessor)lBWFProcessors.get(intSourceIndex-1)).writeFile(fDestFile);
//            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
            return false;
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("Error on decoding at " + strSQL + e.toString());
            return false;
        }        
        return true;
    }
}
