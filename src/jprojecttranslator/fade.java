package jprojecttranslator;
import org.dom4j.Element;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import net.sourceforge.openforecast.DataSet;
import net.sourceforge.openforecast.DataPoint;
import net.sourceforge.openforecast.Observation;
import net.sourceforge.openforecast.Forecaster;
import net.sourceforge.openforecast.ForecastingModel;
import org.dom4j.DocumentHelper;
/**
 * This stores information about an audio fade.
 * A fade consists of a duration in samples and some curve data.
 * A fade can be an in fade or an out fade
 *
 * @author scobeam
 */
public class fade {
    // The fade length
    private long lLength; 
    // An AES31 format string for the fade shape
    private String strFade;
    // A tree map containing the fade as key value pairs. The key is the time in samples, the value is the gain between 1 and 0
    private TreeMap map;
    /**
     * Load a FadeIn or FadeOut from an Ardour project file.
     * Crossfades in Ardour consist of a FadeIn and FadeOut.
     * Ardour fades consist of a number of fade levels at certain time offsets in samples.
     * In the AES31 adl file we need three fade levels at 25% 50% and 75% across the fade.
     * We will use some numerical analysis from the openforecast package to find suitable values.
     * @param xmlFade   This is an xml Element containing fade values. The FadeIn or FadeOut element should be part of a crossfade.
     * @return          Returns true if the fade was parsed successfully.
     */
    public boolean loadArdourFade(Element xmlFade) {
        lLength = 0;
        long lTempLength = 0;
        strFade = "";
        String strKey, strValue;
        float fValue;
        map = new TreeMap();
        Element xmlPoint;
        for (Iterator i = xmlFade.elementIterator("point");i.hasNext();) {
            xmlPoint = (Element)i.next();
            strKey = xmlPoint.attributeValue("x");
            lTempLength = java.lang.Math.round(Float.parseFloat(strKey));
            if (lTempLength > lLength) {
                lLength = lTempLength;
            }
            strValue = xmlPoint.attributeValue("y");
            fValue = Float.parseFloat(strValue);
            // The map consists of keys which are the time in samples across the fade, and values which are a float between 1 and 0.
            map.put(lTempLength, fValue);
        }
        // Trap a special case of a linear fade
        if (map.size() == 2) {
            strFade = "LIN  _  _  _";
            System.out.println("Parsed fade is " + lLength + " " + strFade);
            return true;
        }
        if (map.size() < 2) {
            // Something has gone wrong
            return false;
        }
        // Use openforecast to predict the wanted fade levels for the ADL file from the given values in the ardour file
        DataSet observedData = new DataSet();
        DataPoint dp;
        Set s = map.entrySet();
        Iterator i = s.iterator();
        Map.Entry me;
        while (i.hasNext()) {
            me = (Map.Entry)i.next();
            lTempLength = Long.parseLong("" + me.getKey());
            fValue = Float.parseFloat("" + me.getValue());
            dp = new Observation( fValue );
            dp.setIndependentValue( "t", lTempLength );
            observedData.add( dp );
            
        }
//        System.out.println("Input data, observed values");
//        System.out.println( observedData );
        // Obtain a good forecasting model given this data set
        ForecastingModel forecaster = Forecaster.getBestForecast( observedData );
//        System.out.println("Forecast model type selected: " + forecaster.getForecastType());
//        System.out.println( forecaster.toString() );
        // Create additional data points for which forecast values are required
        DataSet requiredDataPoints = new DataSet();
        dp = new Observation( 0.0 );
        dp.setIndependentValue( "t", lLength/4 );
        requiredDataPoints.add( dp );
        dp = new Observation( 0.0 );
        dp.setIndependentValue( "t", lLength/2 );
        requiredDataPoints.add( dp );
        dp = new Observation( 0.0 );
        dp.setIndependentValue( "t", 3*lLength/4 );
        requiredDataPoints.add( dp );
        forecaster.forecast( requiredDataPoints );
//        System.out.println("Output data, forecast values");
//        System.out.println( requiredDataPoints );
        i = requiredDataPoints.iterator();
        double dLevel;
        String strLevel;
        strFade = "CURVE  "; 
        while (i.hasNext()) {
            dp = (DataPoint)i.next();
            dLevel = dp.getDependentValue();
            if (dLevel < 0) {
                return false;
            }
            dLevel = 20*Math.log10(dLevel);
            strLevel = String.format(Locale.UK,"%.2f", dLevel);
            strFade = strFade + strLevel + "  ";
            
        }
        System.out.println("Parsed fade is " + lLength + " " + strFade);
        return true;
        
    }
    /**
     * Load a fade from an Ardour project file.
     * Ardour fades consist of a number of fade levels at certain time offsets in samples.
     * In the AES31 adl file we need three fade levels at 25% 50% and 75% across the fade.
     * We will use some numerical analysis from the openforecast package to find suitable values.
     * @param xmlSource This is an xml Element containing fade values. The FadeIn or FadeOut element should be part of a region.
     * @return          Returns true if the fade was parsed successfully.
     */
    public boolean loadArdourElement(Element xmlSource) {
        lLength = 0;
        long lTempLength = 0;
        strFade = "";
        String strKey, strValue;
        float fValue;
        map = new TreeMap();
        if ((xmlSource.attributeValue("active")!= null && xmlSource.attributeValue("active").equalsIgnoreCase("no") )
                || (xmlSource.attributeValue("default")!= null &&xmlSource.attributeValue("default").equalsIgnoreCase("yes"))) {
            return false;
        }
        Element xmlAutomationList = xmlSource.element("AutomationList");
        String strEvents = xmlAutomationList.elementText("events");
        StringTokenizer st = new StringTokenizer(strEvents); 
        while(st.hasMoreTokens()) {
            strKey = st.nextToken();
            lTempLength = java.lang.Math.round(Float.parseFloat(strKey));
            if (lTempLength > lLength) {
                lLength = lTempLength;
            }
            strValue = st.nextToken();
            fValue = Float.parseFloat(strValue);
            // The map consists of keys which are the time in samples across the fade, and values which are a float between 1 and 0.
            map.put(lTempLength, fValue);
        }
        // Trap a special case of a linear fade
        if (map.size() == 2) {
            strFade = "LIN  _  _  _";
            System.out.println("Parsed fade is " + lLength + " " + strFade);
            return true;
        }
        if (map.size() < 2) {
            // Something has gone wrong
            return false;
        }
        // Use openforecast to predict the wanted fade levels for the ADL file from the given values in the ardour file
        DataSet observedData = new DataSet();
        DataPoint dp;
        Set s = map.entrySet();
        Iterator i = s.iterator();
        Map.Entry me;
        while (i.hasNext()) {
            me = (Map.Entry)i.next();
            lTempLength = Long.parseLong("" + me.getKey());
            fValue = Float.parseFloat("" + me.getValue());
            dp = new Observation( fValue );
            dp.setIndependentValue( "t", lTempLength );
            observedData.add( dp );
            
        }
//        System.out.println("Input data, observed values");
//        System.out.println( observedData );
        // Obtain a good forecasting model given this data set
        ForecastingModel forecaster = Forecaster.getBestForecast( observedData );
//        System.out.println("Forecast model type selected: " + forecaster.getForecastType());
//        System.out.println( forecaster.toString() );
        // Create additional data points for which forecast values are required
        DataSet requiredDataPoints = new DataSet();
        dp = new Observation( 0.0 );
        dp.setIndependentValue( "t", lLength/4 );
        requiredDataPoints.add( dp );
        dp = new Observation( 0.0 );
        dp.setIndependentValue( "t", lLength/2 );
        requiredDataPoints.add( dp );
        dp = new Observation( 0.0 );
        dp.setIndependentValue( "t", 3*lLength/4 );
        requiredDataPoints.add( dp );
        forecaster.forecast( requiredDataPoints );
//        System.out.println("Output data, forecast values");
//        System.out.println( requiredDataPoints );
        i = requiredDataPoints.iterator();
        double dLevel;
        String strLevel;
        strFade = "CURVE  "; 
        while (i.hasNext()) {
            dp = (DataPoint)i.next();
            dLevel = dp.getDependentValue();
            if (dLevel < 0) {
                return false;
            }
            dLevel = 20*Math.log10(dLevel);
            strLevel = String.format(Locale.UK,"%.2f", dLevel);
            strFade = strFade + strLevel + "  ";
            
        }
        System.out.println("Parsed fade is " + lLength + " " + strFade);
        return true;
        
    }
    
    public boolean loadAES31Fade (long lSetLength, String strFadeShape, String strDirection) {
        lLength = lSetLength;
        strFade = strFadeShape;
        map = new TreeMap();
        String strFadeType = "";
        float fValue0, fValue1, fValue2, fValue3, fValue4;
        Pattern pPattern = Pattern.compile("(CURVE|LIN)\\s*"
                + "([+-]?\\d+\\.?\\d*|_)\\s*"
                + "([+-]?\\d+\\.?\\d*|_)\\s*"
                + "([+-]?\\d+\\.?\\d*|_)");
        Matcher mMatcher = pPattern.matcher(strFadeShape);
        if (mMatcher.find()) {
            strFadeType = mMatcher.group(1);
            if (strFadeType.equalsIgnoreCase("CURVE")) {
                fValue1 = Float.parseFloat(mMatcher.group(2));
                fValue2 = Float.parseFloat(mMatcher.group(3));
                fValue3 = Float.parseFloat(mMatcher.group(4));
                fValue1 = (float)Math.pow(10,fValue1/20);
                fValue2 = (float)Math.pow(10,fValue2/20);
                fValue3 = (float)Math.pow(10,fValue3/20);
//                fValue1 = (float)Math.exp(fValue1/20);
//                fValue2 = (float)Math.exp(fValue2/20);
//                fValue3 = (float)Math.exp(fValue3/20);
                if (strDirection.equalsIgnoreCase("out")) {
                    fValue0 = 1;
                    fValue4 = 0;
                } else {
                    fValue0 = 0;
                    fValue4 = 1;
                }
                // The map consists of keys which are the time in samples across the fade, and values which are a float between 1 and 0.
                map.put((long)0, fValue0);
                map.put((long)java.lang.Math.round(lSetLength/4), fValue1);
                map.put((long)java.lang.Math.round(lSetLength/2), fValue2);
                map.put((long)java.lang.Math.round(3*lSetLength/4), fValue3);
                map.put((long)lSetLength, fValue4);
            } 
            if (strFadeType.equalsIgnoreCase("LIN")) {
                if (strDirection.equalsIgnoreCase("out")) {
                    fValue0 = 1;
                    fValue1 = 0;
                } else {
                    fValue0 = 0;
                    fValue1 = 1;
                }
                map.put((long)0, fValue0);
                map.put((long)lSetLength, fValue1);
            }
            
            
            
        } else {
            return false;
        }
        return true;
    }
    /**
     * @return Returns the length of the fade in samples.
     */
    public long getLength() {
        return lLength;
    }
    /**
     * @return Returns a string in ADL format describing the fade shape.
     */
    public String getFade() {
        return strFade;
    }
    
    public Element getArdourFade(int setID) {
        Element xmlFade;
        if (Float.parseFloat(map.get((long)0).toString()) == 1) {
            // It's a fade out
            xmlFade = DocumentHelper.createElement("FadeOut").addAttribute("active","yes");
        } else {
            // It's a fade in
            xmlFade = DocumentHelper.createElement("FadeIn").addAttribute("active","yes");
        }
        // <AutomationList id="108" default="1" min_yval="0" max_yval="2" max_xval="0" state="Off" style="Absolute">
        Element xmlEvents = xmlFade.addElement("AutomationList").addAttribute("id", "" + setID).addAttribute("default", "1").addAttribute("min_yval", "0")
                .addAttribute("max_yval", "2").addAttribute("max_xval", "0").addAttribute("state", "Off")
                .addAttribute("style", "Absolute").addElement("events");
        String strEvents = "";
        Set s = map.entrySet();
        Iterator i = s.iterator();
        Map.Entry me;
        while (i.hasNext()) {
            me = (Map.Entry)i.next();
            strEvents = strEvents + Long.parseLong("" + me.getKey()) + " " + Float.parseFloat("" + me.getValue()) + "\n";
           
        }
        xmlEvents.addText(strEvents);
         
        return xmlFade;
    }
}
