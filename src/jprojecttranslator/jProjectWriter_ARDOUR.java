package jprojecttranslator;

import java.sql.Statement;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import java.io.*;
import java.net.URLDecoder;
import java.sql.ResultSet;
import org.dom4j.Element;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
/**
 * Project writer for ARDOUR projects.
 * This will write an ARDOUR file and a set subfolders, some of these will contain BWAV files.
 * The audio file format will not be changed.
 * @author arth
 */
public class jProjectWriter_ARDOUR extends jProjectWriter {
    /** This is the xml document object which is used for creating and saving to an xml file.*/
    static Document xmlDocument = DocumentHelper.createDocument();
    /** This is an xml formatter object to make the xml file human readable.*/
    static OutputFormat xmlFormat = OutputFormat.createPrettyPrint();
    /** Every item in an ardour project has to have a unique id number, this int is used to keep count. */
    int intIdCounter = 1;
    
    
        /*
     * This returns a FileFilter which this class can read
     */
    public javax.swing.filechooser.FileFilter getFileFilter() {
        javax.swing.filechooser.FileFilter filter = new FileNameExtensionFilter("Ardour (.ardour)", "ardour");
        return filter;
    }
    
    protected boolean processProject() {
        System.out.println("ARDOUR writer thread running");
        /** Ardour requires ID numbers for each element in the file, calculate these numbers first */
        // updateIDNumbers();
        /**
        * Next step is to create an ADL file and write the output.
        */
        writeARDOURFile(fDestFile, st);
        oProjectTranslator.writeStringToPanel("Ardour project file written");
        
        oProjectTranslator.writeStringToPanel("Finished");
        System.out.println("Ardour writer thread finished");
        return true;
    }
    
    
    private boolean writeARDOURFile(File setDestFile, Statement st) {
        ResultSet rs;
        xmlDocument.clearContent();
        xmlDocument.addElement("Session");
        Element xmlRoot = xmlDocument.getRootElement();
        xmlRoot.addAttribute("sample-rate", "" + jProjectTranslator.intProjectSampleRate);
        Element xmlConfig = xmlRoot.addElement("Config");
        fillConfigElement(xmlConfig);
        Element xmlSources = xmlRoot.addElement("Sources");
        fillSourcesElement(xmlSources);
        Element xmlDiskStreams = xmlRoot.addElement("DiskStreams");
        fillDiskStreamsElement(xmlDiskStreams);
        strSQL = "SELECT strTitle FROM PUBLIC.PROJECT;";
        try {
            st = conn.createStatement();
            rs = st.executeQuery(strSQL);
            rs.next();
            if (!(rs.wasNull()) ) {
                String strTitle = URLDecoder.decode(rs.getString(1), "UTF-8");
                xmlRoot.addAttribute("name", strTitle);
            }
            xmlRoot.addAttribute("id-counter","" + intIdCounter);
            
        } catch (java.sql.SQLException e) {
            System.out.println("Error on SQL " + strSQL + e.toString());
            return false;
        } catch (java.io.UnsupportedEncodingException e) {
            System.out.println("Error on URL decode " + e.toString());
        }

        saveXMLFile(setDestFile);
        return true;
    }
    
    private boolean saveXMLFile(File setDestFile) {
        try {
            xmlFormat.setNewlines(true);
            xmlFormat.setTrimText(false);
            XMLWriter writer = new XMLWriter(new FileWriter( setDestFile ), xmlFormat);
            writer.write( xmlDocument );
            writer.close();
        } catch ( IOException ioe ) {
            return false;
        }  
        return true;    //OK if we got this far
    } 
    
    private void fillConfigElement(Element xmlConfig) {
        xmlConfig.addElement("Option").addAttribute("name", "output-auto-connect").addAttribute("value", "2");
        xmlConfig.addElement("Option").addAttribute("name", "input-auto-connect").addAttribute("value", "1");
        xmlConfig.addElement("Option").addAttribute("name", "meter-falloff").addAttribute("value", "32");
        xmlConfig.addElement("end-marker-is-free").addAttribute("val", "yes");
    }
    
    private void fillSourcesElement(Element xmlSources){
        
    }
    
    private void fillDiskStreamsElement(Element xmlDiskStreams){
        String strID = "" + intIdCounter++;
        xmlDiskStreams.addElement("AudioDiskstream").addAttribute("flags", "Recordable").addAttribute("id", strID);
    }
    
}
