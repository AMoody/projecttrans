package jprojecttranslator;

import java.sql.Statement;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import wavprocessor.WAVProcessor;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
/**
 * Project writer for ROSEGARDEN projects.
 * This will update an existing ROSEGARDEN file replacing the tempo section with a new one.
 * The ROSEGARDEN file must already exist and a backup of the original file is created just in case something goes wrong.
 * @author arth
 */
public class jProjectWriter_ROSEGARDEN extends jProjectWriter {
    /** This is the xml document object which is used for creating and saving to an xml file.*/
    static Document xmlDocument = DocumentHelper.createDocument();
    /** This is an xml formatter object to make the xml file human readable.*/
    static OutputFormat xmlFormat = OutputFormat.createPrettyPrint();

    // This is the project name set by the file save as dialogue
    String strProjectName;
    /*
     * This returns a FileFilter which this class can read
     */
    @Override
    public javax.swing.filechooser.FileFilter getFileFilter() {
        javax.swing.filechooser.FileFilter filter = new FileNameExtensionFilter("Rosegarden (.rg)", "rg");
        return filter;
    }
    @Override
    protected boolean processProject() {
        System.out.println("ROSEGARDEN writer thread running");
       

        int intEnd = fDestFile.getName().lastIndexOf(".rg");
        strProjectName = fDestFile.getName().substring(0, intEnd);
        File fProjectFolder = fDestFile.getParentFile();
        System.out.println("fProjectFolder set to " + fProjectFolder);
        DateTime dtNow = new DateTime();
        DateTime dtUTCNow = dtNow.withZone(DateTimeZone.UTC);
        DateTimeFormatter fmtForBackupFile = DateTimeFormat.forPattern("yyyyMMddHHmmss");
        String strBackupDateTime = fmtForBackupFile.print(dtUTCNow);
        File fBackupFile = new File(fProjectFolder, strProjectName + "_" + strBackupDateTime + ".rg");
        try {
            Files.copy(fDestFile.toPath(), fBackupFile.toPath());
        } catch (IOException ex) {
            oProjectTranslator.writeStringToPanel(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectWriter.ROSEGARDENFileFailed"));
            System.out.println("Error on file copy to backup " + ex.toString());
            return false;
        }

        if (writeROSEGARDENFile(fDestFile, st)) {
            oProjectTranslator.writeStringToPanel(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectWriter.ROSEGARDENFileWritten"));
        } else {
            oProjectTranslator.writeStringToPanel(java.util.ResourceBundle.getBundle("jprojecttranslator/Bundle").getString("jProjectWriter.ROSEGARDENFileFailed"));
        }
        System.out.println("Rosegarden writer thread finished");
        return true;
    }
    
    /**
     * This creates the XML document and fills in all the elements. 
     * Then it saved the MXL file.
     * @param setDestFile The file and path where the file should be saved.
     * @param st
     * @return 
     */
    private boolean writeROSEGARDENFile(File setDestFile, Statement st) {
        ResultSet rs;
        // Load existing rosegarden file
        loadXMLData(setDestFile);
        Element xmlRoot = xmlDocument.getRootElement();
        Element xmlTemp;
        Element xmlComposition = xmlRoot.element("composition");
        // Remove existing tempo and time signature data
        List listXmlElements = xmlComposition.elements("tempo");
        Iterator iterator = listXmlElements.iterator();
        while (iterator.hasNext()) {
            ((Element)iterator.next()).detach();
        }
        listXmlElements = xmlComposition.elements("timesignature");
        iterator = listXmlElements.iterator();
        while (iterator.hasNext()) {
            ((Element)iterator.next()).detach();
        }    
        // Add extra tempo elements.
        createExtraTempoElements();
        // Add new tempo and time signature data
        fillTempoMapElement(xmlComposition);
        // Save
        
        return saveXMLFile(setDestFile);
    }
    /**
     * This saves the ardour XML file.
     * @param setDestFile This sets the path and name for the file.
     * @return true if the file is saved without errors.
     */
    private boolean saveXMLFile(File setDestFile) {
        try {
            FileOutputStream fos = new FileOutputStream(setDestFile);
            GZIPOutputStream gzipOS = new GZIPOutputStream(fos);
            xmlFormat.setNewlines(true);
            xmlFormat.setTrimText(false);
            XMLWriter writer = new XMLWriter(gzipOS, xmlFormat);
            writer.write( xmlDocument );
            writer.close();
        } catch ( IOException ioe ) {
            return false;
        } 

        return true;    //OK if we got this far
    } 

    /**
     * This loads the Rosegarden project file which is XML inside a GZIP file in to an internal xml Document.
     * @param setSourceFile     This is the source file.
     * @return                  Returns true if the file was loaded.
     */
    protected boolean loadXMLData(File setSourceFile) {
        try {
            FileInputStream fis = new FileInputStream(setSourceFile);
            GZIPInputStream gis = new GZIPInputStream(fis);
            SAXReader reader = new SAXReader();
            xmlDocument = reader.read(gis);
        } catch (DocumentException de) {
            System.out.println("DocumentException while loading XML data " + de.toString());
            return false;
        } catch (java.net.MalformedURLException e) {
            System.out.println("MalformedURLException while loading XML file " + e.toString());
            return false;
        } catch (FileNotFoundException ex) {
            System.out.println("FileNotFoundException while loading XML file " + ex.toString());
            return false;
        } catch (IOException ex) {
            System.out.println("IOException while loading XML file " + ex.toString());
            return false;
        }
        return true;
    }
    /**
     * This is used to fill the tempo map element with values from the database.
     * @param xmlTempoMap 
     */
    private void fillTempoMapElement(Element xmlComposition) {
        // RG.time = Ardour.pulse*3840
        // RG.tempo = Ardour.beats-per-minute*100000
        // RG.bpm = RG.tempo*0.0006
        // <tempo time="371520" bph="7200" tempo="12000000" target="0"/>
        long lTime, lTempo, lTarget, lBPH;
        try {
            strSQL = "SELECT dPulse, intFrame, dBeatsPerMinute, intNoteType, dAdjustedEndBeatsPerMinute FROM PUBLIC.ARDOUR_TEMPO ORDER BY intFrame;";
            ResultSet rs = st.executeQuery(strSQL);
            while (rs.next()) {
                lTime = Math.round(rs.getDouble(1) * 3840);
                lTempo = Math.round(rs.getDouble(3) * 100000);
                lBPH = Math.round(rs.getDouble(3) * 600);
                lTarget = Math.round(rs.getDouble(5) * 100000);
                if (lTempo == lTarget) {
                    xmlComposition.addElement("tempo").addAttribute("time","" + lTime).addAttribute("bph",""+lBPH).addAttribute("tempo",""+lTempo);
                } else {
                    xmlComposition.addElement("tempo").addAttribute("time","" + lTime).addAttribute("bph",""+lBPH).addAttribute("tempo",""+lTempo).addAttribute("target",""+lTarget);
                }
                
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        }
        // RG.time = Ardour.pulse*3840
        // RG.numerator = Ardour.divisions.per.bar
        // RG.denominator = Ardour.note-type
        // <timesignature time="103680" numerator="5" denominator="8"/>
        // PUBLIC.ARDOUR_TIME_SIGNATURE (intPulse, intFrame, strBBT, intBeat, intNoteType, intDivisionsPerBar)
        int intNumerator, intDenominator;
        try {
            strSQL = "SELECT dPulse, intNoteType, intDivisionsPerBar FROM PUBLIC.ARDOUR_TIME_SIGNATURE ORDER BY intFrame;";
            ResultSet rs = st.executeQuery(strSQL);
            while (rs.next()) {
                lTime = Math.round(rs.getDouble(1) * 3840);
                intNumerator = rs.getInt(3);
                intDenominator = rs.getInt(2);
                xmlComposition.addElement("timesignature").addAttribute("time","" + lTime).addAttribute("numerator",""+intNumerator).addAttribute("denominator",""+intDenominator);
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        }
}
    private void updateNextPulseValues() {
        try {
            strSQL = "SELECT dPulse, dEndBeatsPerMinute FROM PUBLIC.ARDOUR_TEMPO ORDER BY dPulse DESC;";
            double dThisPulse, dLastPulse, dEndBeatsPerMinute;
            dLastPulse = 0;
            ResultSet rs = st.executeQuery(strSQL);
            // Loop through the existing entries in reverse order
            while (rs.next()) {
                dThisPulse = rs.getDouble(1);
                if (dLastPulse == 0) {
                    dLastPulse = dThisPulse;
                }
                // "UPDATE PUBLIC.SOURCE_INDEX SET strUMID = \'" + strUMID + "\' WHERE intIndex = " + intSourceIndex + ";";
                strSQL = "UPDATE PUBLIC.ARDOUR_TEMPO SET dNextPulse = " + dLastPulse + " WHERE dPulse = " + dThisPulse + ";";
                int j = st.executeUpdate(strSQL);
                if (j == -1) {
                    System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                }
                dLastPulse = dThisPulse;

                
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        }        
    }
    private int getPulseGapCount() {
        int intCount = 0;
        try {
            strSQL = "SELECT COUNT(*) FROM PUBLIC.ARDOUR_TEMPO WHERE dBeatsPerMinute != dEndBeatsPerMinute AND (dNextPulse - dPulse) > 1;";
            
            ResultSet rs = st.executeQuery(strSQL);
            rs.next();
            intCount = rs.getInt(1);

        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        } 
        return intCount;
    }
    private void fillAdjustedEndBeatsPerMinuteColumn() {
        double dRosegardenBeats, dRosegardenSecondsPerBeat, dRosegardenRequiredInterval, dRosegardenBeatInterval, dBeatsPerMinute, dEndBeatsPerMinute;
        double dPulse, dNextPulse, dAdjustedEndBeatsPerMinute;
        long lFrame, lNextFrame;
        try {
            strSQL = "SELECT dPulse, intFrame, dNextPulse, dBeatsPerMinute, dEndBeatsPerMinute FROM PUBLIC.ARDOUR_TEMPO ORDER BY dPulse ASC;";
            ResultSet rs = st.executeQuery(strSQL);
            while (rs.next()) {
                dPulse = rs.getDouble(1);
                lFrame = rs.getInt(2);
                dNextPulse = rs.getDouble(3);
                dBeatsPerMinute = rs.getDouble(4);
                dEndBeatsPerMinute = rs.getDouble(5);
                if (dBeatsPerMinute == dEndBeatsPerMinute) {
                    // No need to calculate a fudge factor
                    dAdjustedEndBeatsPerMinute = dEndBeatsPerMinute;
                } else {
                    lNextFrame = Math.round((lFrame)+jProjectTranslator.intPreferredSampleRate*240*(dNextPulse-dPulse)*((Math.log(dEndBeatsPerMinute)-Math.log(dBeatsPerMinute))/(dEndBeatsPerMinute-dBeatsPerMinute)));
                    dRosegardenBeats = dPulse * 4;
                    dRosegardenSecondsPerBeat = 60/rs.getDouble(4);
                    dRosegardenRequiredInterval = ((double)(lNextFrame-lFrame)/jProjectTranslator.intPreferredSampleRate);
                    dRosegardenBeatInterval = 4 * (dNextPulse - dPulse);
                    System.out.println("dRosegardenBeats " + Double.toString(dRosegardenBeats));
                    System.out.println("dRosegardenSecondsPerBeat " + Double.toString(dRosegardenSecondsPerBeat));
                    System.out.println("dRosegardenRequiredInterval " + Double.toString(dRosegardenRequiredInterval));
                    System.out.println("dRosegardenBeatInterval " + Double.toString(dRosegardenBeatInterval));
                    dAdjustedEndBeatsPerMinute = 60/(dRosegardenSecondsPerBeat+2*(dRosegardenRequiredInterval-(dRosegardenBeatInterval*dRosegardenSecondsPerBeat))/dRosegardenBeatInterval);                    
                }
                strSQL = "UPDATE PUBLIC.ARDOUR_TEMPO SET dAdjustedEndBeatsPerMinute = " + dAdjustedEndBeatsPerMinute + " WHERE dPulse = " + dPulse + ";";
                int j = st.executeUpdate(strSQL);
                if (j == -1) {
                    System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                }                
            }
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        } 
        
    }
    private double getMusicalInterval (double dPulse) {
        try {
            strSQL = "SELECT intNoteType, intDivisionsPerBar FROM PUBLIC.ARDOUR_TIME_SIGNATURE WHERE dPulse <= " + dPulse + " ORDER BY dPulse DESC;";
            ResultSet rs = st.executeQuery(strSQL);
            rs.next();
            System.out.println("Time signature " + rs.getInt(2) + " / " + rs.getInt(1) + ".");
            return rs.getDouble(2)/rs.getDouble(1);
            
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
            return 0;
        }
    }
    private void createPulsePlaceholders() {
        double dPulse, dNextPulse, dBeatsPerMinute, dEndBeatsPerMinute, dProposedPulse, dProposedBeatsPerMinute, dInterval, dSecondsPerBeat, dMusicalInterval;
        int intCount;
        long lFrame, lProposedFrame;
        double dRosegardenRequiredInterval, dRosegardenBeatInterval;
        try {
            strSQL = "SELECT dPulse, intFrame, dNextPulse, dBeatsPerMinute, dEndBeatsPerMinute FROM PUBLIC.ARDOUR_TEMPO WHERE dBeatsPerMinute != dEndBeatsPerMinute AND (dNextPulse - dPulse) > 1 ORDER BY dPulse ASC;";
            ResultSet rs = st.executeQuery(strSQL);
            rs.next();
            dPulse = rs.getDouble(1);
            dMusicalInterval = getMusicalInterval(dPulse);
            if (dMusicalInterval == 0) {
                dMusicalInterval = 1;
            }
            dProposedPulse = dPulse + dMusicalInterval;
            lFrame = rs.getInt(2);
            dNextPulse = rs.getDouble(3);
            dBeatsPerMinute = rs.getDouble(4);
            dEndBeatsPerMinute = rs.getDouble(5);
            // Check to see if there's a time signature change within the next whole pulse, prefer to have tempo change at start of bars.
            strSQL = "SELECT COUNT(*) FROM PUBLIC.ARDOUR_TIME_SIGNATURE WHERE dPulse > " + dPulse + " AND dPulse < " + dProposedPulse + ";";
            System.out.println("SELECT COUNT(*) FROM PUBLIC.ARDOUR_TIME_SIGNATURE WHERE dPulse > " + dPulse + " AND dPulse < " + dProposedPulse + ";");
            rs = st.executeQuery(strSQL);
            rs.next();
            System.out.println(rs.getInt(1));
            if (rs.getInt(1) > 0) {
                strSQL = "SELECT dPulse FROM PUBLIC.ARDOUR_TIME_SIGNATURE WHERE dPulse > " + dPulse + " AND dPulse < " + dProposedPulse + ";";
                rs = st.executeQuery(strSQL);
                rs.next();
                dProposedPulse = rs.getDouble(1);
            }
            dProposedBeatsPerMinute = dBeatsPerMinute + (dEndBeatsPerMinute - dBeatsPerMinute)*(dProposedPulse-dPulse)/(dNextPulse-dPulse);
            lProposedFrame = Math.round((lFrame)+jProjectTranslator.intPreferredSampleRate*240*(dProposedPulse-dPulse)*((Math.log(dProposedBeatsPerMinute)-Math.log(dBeatsPerMinute))/(dProposedBeatsPerMinute-dBeatsPerMinute)));
            // =(intFrame)+jProjectTranslator.intPreferredSampleRate*240*(dProposedPulse-dPulse)*((LN(dProposedBeatsPerMinute)-LN(dBeatsPerMinute))/(dProposedBeatsPerMinute-dBeatsPerMinute))
            System.out.println("Values " + lProposedFrame + ", " + lFrame + ", " + jProjectTranslator.intPreferredSampleRate);
            
            // =(60/dBeatsPerMinute)+2*(((lProposedFrame-lFrame)/jProjectTranslator.intPreferredSampleRate)-((dProposedPulse-dPulse)*(60/dBeatsPerMinute)))/(dProposedPulse-dPulse)
            // =E17+2*(B18-((A18-A17)*E17))/(A18-A17)
            strSQL = "UPDATE PUBLIC.ARDOUR_TEMPO SET dEndBeatsPerMinute = " + dProposedBeatsPerMinute + " WHERE dPulse = " + dPulse + ";";
            int j = st.executeUpdate(strSQL);
            if (j == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
            strSQL = "INSERT INTO PUBLIC.ARDOUR_TEMPO (dPulse, intFrame, dBeatsPerMinute, dEndBeatsPerMinute) VALUES (" + dProposedPulse + ", " + lProposedFrame + ", " + dProposedBeatsPerMinute + ", " + dEndBeatsPerMinute + ");";
            j = st.executeUpdate(strSQL);
            if (j == -1) {
                System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
            }
            
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
        }        
    }
    /**
     * The arithmetic for tempo ramps in Rosegarden is not the same as Ardour (and most other DAWs) so we need to create 
     * extra tempo elements and adjust the end tempo rate to make the bars coincide
     */
    private void createExtraTempoElements() {
        updateNextPulseValues();
        while (getPulseGapCount() > 0){
            createPulsePlaceholders();
            updateNextPulseValues();
        }
        fillAdjustedEndBeatsPerMinuteColumn();
//        int intCount = getPulseGapCount();
//        System.out.println("getPulseGapCount returned " + intCount + ".");
//        createPulsePlaceholders();
        
        
        
    }
    
    /** This is used to get text information which is shown in the Help/About dialogue box.
     * @return The information text.
     */
    public String getInfoText() {
        return "<b>Rosegarden</b><br>"
                + "This exporter will update an existing Rosegarden project with latest tempo and time signature information.<br>"
                + "The Rosegarden file must already exist, a copy of the original file is made then a new file is created.<br>"
                + "The new file contains all the original data except for the tempo and time signature information.<br>"
                + "<br>";
    }
}
