/*
 * jProjectTranslator is used to translate audio editing projects from one format to another.
 * Internally the metadata is stored as AES31
 * Three import formats are supported, AES31, Ardour and VCS Startrack
 * Two output formats are supported, AES31 and Ardour
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
import javax.swing.JOptionPane;
/**
 *
 * @author arth
 */
public class jProjectTranslator extends javax.swing.JFrame implements Observer {
    /** We need to retain a reference to the window when we create it so we can
     *access the objects it contains. This is it.*/
    static jProjectTranslator ourWindow;
    /** This is a source file to be loaded. */
    private static String strSourceProjectFile;
    /** This is the ini file which stores the users own settings in their home folder.*/
    static JIniFile usersIniFile;
    static database ourDatabase;
    private static List listReaders = new ArrayList();
    private static List listWriters = new ArrayList();
    private static List listBWFProcessors = new ArrayList();
    private static javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
    public static int intSampleRate = 48000;
    public static double dFrameRate = 25;
//    public static double dFrameRate = 29.97;
    /* This stores the last used folder. */
    private static File fPath;
    // This is the message panel which is used to display information and ask questions
    public jMessagePanel messagePanel;
    
    /**
     * Creates new form jProjectTranslator
     */
    public jProjectTranslator() {
        initComponents();
        messagePanel = new jMessagePanel(this);
        jPanel1.add(messagePanel);
        
    }
    public void update(Observable o, Object arg) {
//        System.out.println("jProjectTranslator notified of change o " + o.getClass().toString() + " arg " + arg.getClass().toString());
        // One of the observed objects has changed
        String strSQL = "";
        if (o instanceof jProjectReader) {
            
        }
        if (o instanceof jProjectWriter) {
            if (arg instanceof BWFProcessor) {
                // A file is being written, update the progress bar
                
                try {
                    Connection conn = ourDatabase.getConnection();
                    Statement st = conn.createStatement();
                    BWFProcessor tempBWFProcessor = (BWFProcessor)arg;
                    String strUMID = URLEncoder.encode(tempBWFProcessor.getBextOriginatorRef(), "UTF-8");
                    strSQL = "UPDATE PUBLIC.SOURCE_INDEX SET intCopied = " + tempBWFProcessor.getLByteWriteCounter() +  " WHERE strUMID = \'" + strUMID + "\';";
//                    System.out.println("SQL " + strSQL);
                    int i = st.executeUpdate(strSQL);
                    if (i == -1) {
                        System.out.println("Error on SQL " + strSQL + st.getWarnings().toString());
                    }
                    strSQL = "SELECT SUM(intCopied), SUM(intIndicatedFileSize) FROM PUBLIC.SOURCE_INDEX;";
                    st = conn.createStatement();
                    ResultSet rs = st.executeQuery(strSQL);
                    rs.next();
                    long lPercent, lCopied, lIndicated;
                    lCopied = rs.getLong(1);
                    lIndicated = rs.getLong(2);
                    if (lCopied < lIndicated && lIndicated > 0) {
                        lPercent =  100*lCopied/lIndicated;
                        
                    } else {
                        lPercent =  100;
                    }
                    jProgressBar1.setValue((int)lPercent);
                    System.out.println("Bytes written " + lCopied + " from a total target of " + lIndicated + " " + lPercent + " %");
                } catch (java.sql.SQLException e) {
                    System.out.println("Error on SQL " + strSQL + e.toString());
                    
                } catch (java.io.UnsupportedEncodingException e) {
                    System.out.println("Error on decoding at " + strSQL + e.toString());
                    
                } 
                
            }
            
        }
    }
    /**
     * This method is called from within the constructor to initialise the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jToolBar1 = new javax.swing.JToolBar();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        jButton3 = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jTextField1 = new javax.swing.JTextField();
        jTextField2 = new javax.swing.JTextField();
        jProgressBar1 = new javax.swing.JProgressBar();
        jPanel1 = new javax.swing.JPanel();
        jMenuBar1 = new javax.swing.JMenuBar();
        jMenu1 = new javax.swing.JMenu();
        jMenuItem1 = new javax.swing.JMenuItem();
        jMenuItem2 = new javax.swing.JMenuItem();
        jMenuItem4 = new javax.swing.JMenuItem();
        jMenuItem3 = new javax.swing.JMenuItem();
        jMenu2 = new javax.swing.JMenu();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Project Translator");

        jToolBar1.setRollover(true);

        jButton1.setText("Open");
        jButton1.setFocusable(false);
        jButton1.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jButton1.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuOpen(evt);
            }
        });
        jToolBar1.add(jButton1);

        jButton2.setText("Save As");
        jButton2.setFocusable(false);
        jButton2.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jButton2.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuSave(evt);
            }
        });
        jToolBar1.add(jButton2);

        jButton3.setText("Exit");
        jButton3.setFocusable(false);
        jButton3.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jButton3.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitForm(evt);
            }
        });
        jToolBar1.add(jButton3);

        jLabel1.setText("Project name");

        jLabel2.setText("Project creation date");

        jTextField1.setEditable(false);
        jTextField1.setMinimumSize(new java.awt.Dimension(200, 31));
        jTextField1.setPreferredSize(new java.awt.Dimension(200, 31));

        jTextField2.setEditable(false);
        jTextField2.setMinimumSize(new java.awt.Dimension(200, 31));
        jTextField2.setPreferredSize(new java.awt.Dimension(200, 31));

        jProgressBar1.setStringPainted(true);

        jPanel1.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        jPanel1.setPreferredSize(new java.awt.Dimension(100, 100));

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 98, Short.MAX_VALUE)
        );

        jMenu1.setText("File");

        jMenuItem1.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItem1.setText("Open");
        jMenuItem1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuOpen(evt);
            }
        });
        jMenu1.add(jMenuItem1);

        jMenuItem2.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItem2.setText("Save As");
        jMenuItem2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuSave(evt);
            }
        });
        jMenu1.add(jMenuItem2);

        jMenuItem4.setText("Preferences");
        jMenuItem4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuPreferences(evt);
            }
        });
        jMenu1.add(jMenuItem4);

        jMenuItem3.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Q, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItem3.setText("Exit");
        jMenuItem3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitForm(evt);
            }
        });
        jMenu1.add(jMenuItem3);

        jMenuBar1.add(jMenu1);

        jMenu2.setText("Help");
        jMenuBar1.add(jMenu2);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jToolBar1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jProgressBar1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel1)
                            .addComponent(jLabel2))
                        .addGap(56, 56, 56)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jTextField2, javax.swing.GroupLayout.PREFERRED_SIZE, 188, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, 188, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, 381, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jToolBar1, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(jTextField2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jProgressBar1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 32, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void exitForm(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitForm
    usersIniFile.WriteInteger("General","WindowWidth",ourWindow.getWidth());
    usersIniFile.WriteInteger("General","WindowHeight",ourWindow.getHeight());
    if (ourWindow.getLocationOnScreen().x > 0)
    usersIniFile.WriteInteger("General","WindowX",ourWindow.getLocationOnScreen().x);
    if (ourWindow.getLocationOnScreen().y > 0)
    usersIniFile.WriteInteger("General","WindowY",ourWindow.getLocationOnScreen().y);
    usersIniFile.WriteInteger("General", "SampleRate", intSampleRate);
    usersIniFile.WriteString("General", "FrameRate", String.format("%.2f", dFrameRate));
    usersIniFile.WriteString("General", "CurrentPath", fPath.toString());
    usersIniFile.UpdateFile();
    System.exit(0);
    }//GEN-LAST:event_exitForm

    private void menuOpen(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuOpen
        // File open 
        fc.setAcceptAllFileFilterUsed(false);
        fc.resetChoosableFileFilters();
        fc.setSelectedFile(new File(fPath,"Project"));
        Iterator itr = listReaders.iterator(); 
        jProjectReader tempProjectReader;
        while(itr.hasNext()) {
            // fc.addChoosableFileFilter( ((javax.swing.filechooser.FileFilter)(itr.next())) );
            tempProjectReader = (jProjectReader)(itr.next());
            fc.addChoosableFileFilter( tempProjectReader.getFileFilter() );
        }
        if (!(fc.showOpenDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION)) {
            return;
        }
        if (fc.getSelectedFile().exists()) {
            fPath = fc.getSelectedFile().getParentFile();
        }
        System.out.println("The chosen file was " + fc.getSelectedFile());
        System.out.println("The chosen file filter was " + fc.getFileFilter());
        itr = listReaders.iterator();
        while(itr.hasNext()) {
            tempProjectReader = (jProjectReader)(itr.next());
            if (fc.getFileFilter().getDescription().equalsIgnoreCase(tempProjectReader.getFileFilter().getDescription())) {
                System.out.println("A suitable file reader was found");
                tempProjectReader.load(ourDatabase, listBWFProcessors, fc.getSelectedFile());
            }
        }
    }//GEN-LAST:event_menuOpen

    private void menuSave(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuSave
        // Save the project
        fc.setAcceptAllFileFilterUsed(false);
        fc.resetChoosableFileFilters();
        String strOpenedFileName = fc.getSelectedFile().toString();
        int intDot = strOpenedFileName.lastIndexOf(".");
        if (intDot > 0) {
            String strSaveFilename = strOpenedFileName.substring(0, intDot);
            fc.setSelectedFile(new File(strSaveFilename));
        }
        Iterator itr = listWriters.iterator(); 
        jProjectWriter tempProjectWriter;
        while(itr.hasNext()) {
            // fc.addChoosableFileFilter( ((javax.swing.filechooser.FileFilter)(itr.next())) );
            tempProjectWriter = (jProjectWriter)(itr.next());
            fc.addChoosableFileFilter( tempProjectWriter.getFileFilter() );
        }
        if (!(fc.showSaveDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION)) {
            return;
        }
        File file = fc.getSelectedFile();
        String path = file.getAbsolutePath();
        String extension = ((javax.swing.filechooser.FileNameExtensionFilter)fc.getFileFilter()).getExtensions()[0];
        if(!path.endsWith(extension)) {
            file = new File(path + "." + extension);
        }
        if ( file.exists() ) {  
            String msg = "The file " + file.getName().toString() + " already exists!\nDo you want to replace it?";  
            msg = java.text.MessageFormat.format( msg, new Object[] { file.getName() } );  
            String title = "Warning";  
            int option = JOptionPane.showConfirmDialog( this, msg, title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE );  
            if ( option == JOptionPane.NO_OPTION ) {  
                return; 
            }
        }
        System.out.println("The chosen file was " + file);
        System.out.println("The chosen file filter was " + fc.getFileFilter());
        itr = listWriters.iterator();
        while(itr.hasNext()) {
            tempProjectWriter = (jProjectWriter)(itr.next());
            if (fc.getFileFilter().getDescription().equalsIgnoreCase(tempProjectWriter.getFileFilter().getDescription())) {
                System.out.println("A suitable file writer was found");
                if (fc.getSelectedFile().getParentFile().exists()) {
                    fPath = fc.getSelectedFile().getParentFile();
                }
                tempProjectWriter.addObserver(this);
                tempProjectWriter.save(ourDatabase, listBWFProcessors, file, this);
                // tempProjectWriter.deleteObserver(this);
            }
        }
    }//GEN-LAST:event_menuSave

    private void menuPreferences(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuPreferences
        jPreferences dlgPref = new jPreferences(this,true);
        dlgPref.setDropDowns(Integer.toString(intSampleRate) , String.format("%.2f", dFrameRate));
        dlgPref.setVisible(true);
        
    }//GEN-LAST:event_menuPreferences

    /**
     @param args A filename reference to be loaded automatically, the file type will be guessed from the extension
     */
    public static void main(String args[]) {
        /*
         * Set the Nimbus look and feel
         */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /*
         * If Nimbus (introduced in Java SE 6) is not available, stay with the
         * default look and feel. For details see
         * http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(jProjectTranslator.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(jProjectTranslator.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(jProjectTranslator.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(jProjectTranslator.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>
        // The first argument given on the command line could be the source project file.
        if ((args.length > 0) && (args[0].length()>0)) {
            strSourceProjectFile = args[0];
            if (!(new File(strSourceProjectFile)).exists()) {
                // File does not exist!
                System.out.println("Source project file not found...");
                System.exit(0);
            }
        }
        // This is the ini file which stores the users own settings in their home folder
        usersIniFile = new JIniFile(System.getProperty("user.home") + "/jProjectTranslator.ini");
        /* Create a local database in memory
         * 
         */
        ourDatabase = new database();
        
        
        /* Create a web server for debugging if required.
         * This allows the database table to be viewed in a web browser
         */
        int intHTTPPort = usersIniFile.ReadInteger("General","intHTTPPort",0);
        intSampleRate = usersIniFile.ReadInteger("General","SampleRate",48000);
        dFrameRate = Double.parseDouble(usersIniFile.ReadString("General","FrameRate","25"));
        fPath = new File( usersIniFile.ReadString("General","CurrentPath",System.getProperty("user.home")) );
        if (intHTTPPort > 0) {
            try {
                HTTPD oWebServer = new HTTPD(intHTTPPort);
                System.out.println("HTTP server created, browse to http://localhost:"+ intHTTPPort +"/debug.html ");
            } catch (IOException ioe) {
                System.out.println("Failed to start HTTP server on port "+ intHTTPPort +" , " + ioe.toString() + ".");
            }
        }
        /*
         * Add the available project readers and writers here so we can filter the source and dest file types
         */
        listReaders.add(new jProjectReader_VCS());
        listWriters.add(new jProjectWriter_AES31());
      
        
        /*
         * Create and display the form
         */
        java.awt.EventQueue.invokeLater(new Runnable() {

            public void run() {
                ourWindow = new jProjectTranslator();
                java.awt.Dimension screenSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
                int intWindowWidth, intWindowHeight, intWindowX, intWindowY;
                intWindowWidth = usersIniFile.ReadInteger("General","WindowWidth",600);
                intWindowHeight = usersIniFile.ReadInteger("General","WindowHeight",500);
                intWindowX = usersIniFile.ReadInteger("General","WindowX",(screenSize.width-intWindowWidth)/2);
                intWindowY = usersIniFile.ReadInteger("General","WindowY",(screenSize.height-intWindowHeight)/2);
                ourWindow.setSize(new java.awt.Dimension(intWindowWidth, intWindowHeight));
                ourWindow.setLocation(intWindowX,intWindowY);
                ourWindow.setVisible(true);
            }
        });
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenu jMenu2;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JMenuItem jMenuItem1;
    private javax.swing.JMenuItem jMenuItem2;
    private javax.swing.JMenuItem jMenuItem3;
    private javax.swing.JMenuItem jMenuItem4;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JProgressBar jProgressBar1;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JTextField jTextField2;
    private javax.swing.JToolBar jToolBar1;
    // End of variables declaration//GEN-END:variables
}