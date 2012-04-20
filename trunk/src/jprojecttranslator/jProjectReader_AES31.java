/*
 * 
 */
package jprojecttranslator;

import java.io.File;
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

/**
 * This is a project reader for AES31 (.adl) files
 * @author scobeam
 */
public class jProjectReader_AES31 extends jProjectReader {
    /** This is the xml document object which is used for loading and saving to an xml file.*/
    static Document xmlDocument = DocumentHelper.createDocument();
    DateTime dtsCreated;
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
        return true;
        
    }
    protected boolean parseAES31Version(Element setVersion) {
        StringTokenizer stTokens = new StringTokenizer(setVersion.getText(), "\n");
        String strLine;
        String strVersion = "";
        String strCreator = "";
        Pattern pADLVersion = Pattern.compile("\\(VER_ADL_VERSION\\)\\s*([\\d|\\.]+)");
        Matcher mMatcher;
        Pattern pCreator = Pattern.compile("\\(VER_CREATOR\\)\\s*\"(.+)\"");
        while(stTokens.hasMoreTokens()) {
            strLine = stTokens.nextToken();
            System.out.println("Checking line " + strLine);
            //
            mMatcher = pADLVersion.matcher(strLine);
            if (mMatcher.find()) {
                strVersion = mMatcher.group(1);
                System.out.println("ADL version found " + strVersion);
            }
            //
            mMatcher = pCreator.matcher(strLine);
            if (mMatcher.find()) {
                strCreator = mMatcher.group(1);
                System.out.println("ADL creator found " + strCreator);
            }
        }
        return true;
    }
    
}
