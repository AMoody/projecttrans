package jprojecttranslator;
/*
 * BWFProcessor.java
 *
 * Created on 09 September 2005, 19:51
 */

/**
 *
 * @author  moodya71
 */
import java.io.*;
import java.util.*;
import java.nio.*;
import java.nio.channels.FileChannel;
public class BWFProcessor implements Runnable {
    private long lSourceFileStart = 0;
    private long lSourceFileEnd = 0;
    // This sets a flag to show that we're reading a miltipart files where wave files are concatenated
    private boolean bMultipart = false;
    private boolean bHasBextChunk = false;
    private static int objectCount = 0;
    private File srcFile;
    private File destFile;
    /** This is a vector which contains objects of type chunk. These are chunks from the start of the<br>
     * wave file up to but not including the data chunk.
     */
    public Vector startChunks = new Vector(5);
    /** This is a vector which contains objects of type chunk. These are chunks from the end of the<br>
     * wave file starting after (but not including) the data chunk.
     */
    public Vector endChunks = new Vector(3);
    private ByteBuffer sourceBytes = ByteBuffer.allocate(262144);
    private FileInputStream inFile = null;
    private FileOutputStream outFile = null;
    private FileChannel inChannel, outChannel;
    private String tempckID;
    private long tempckSIZE, dataChunkSize;
        /** <p>Error codes return are<br>
         * 0 All OK<br>
         * 1 Parsing error of source file, returned by running thread<br>
         * 2 Unable to open source file for some reason, returned only by constructor<br>
         * 3 Unable to open destination file, returned by constructor or running thread<br>
         * 4 The source file is still being written so can't start processing yet.<br></p>
         */    
    private int errorcode = 0;
        /** <p>Status codes return are<br>
         * 0 Not started yet<br>
         * 1 Parsing source file<br>
         * 2 Parsed source file<br>
         * 3 Copying file<br>
         * 4 Finished<br>
         * 5 Failed<br></p>
         */    
    private int status = 0;
    private long calculatedFileSize, indicatedFileSize;
    private long totalFileSize;
    private long dataChunkOffset;
    // Create the thread, we won't start it yet.
    private Thread ourThread; 
    private long bytesWritten;
    private long lastActivity;
    private String tempDestName, tempSourceName;
    private File tempDestFile, tempSourceFile;
    /** This value shows if a destination file should be written. This allows you
     * to use this class just for reading in the chinks of a file without creating a new one.
     */
    private boolean readOnly = false;
    private boolean bDebug = true;
    private Iterator chunkIterator;
    
    
    
    
    /** Creates a new instance of BWFProcessor */
    public BWFProcessor(File setSrcFile, File setDestFile) {
        objectCount++;

        
        srcFile = setSrcFile;
        if (!srcFile.exists() || !srcFile.canRead()){
            errorcode = 2;
            return;
        }
        destFile = setDestFile;
        if (destFile.exists()) {
            errorcode = 3;
            return;
        }
        // Need to generate a temporary name for the source file
        tempSourceName = srcFile.getName().substring(0,(srcFile.getName().length())-3) + "tmp";
        tempSourceFile = new File(srcFile.getParent(),tempSourceName);        
        /** Now to check that the source file is not being written to we will try to rename it.
         * If this works we will change the name back again. If it does not work we 
         * will return with an error code of 4 which indicates that the source file
         * can not be converted yet.
         */
        if (tempSourceFile.exists()) {
            // Temp file already exists, so does the source file.
            tempSourceFile.delete();
        }
        if (srcFile.renameTo(tempSourceFile)) {
            // We were able to rename the source file, good. Change the name back again
            tempSourceFile.renameTo(srcFile);
        } else {
            // We were unable to rename the source file so it is stil being written
            errorcode = 4;
            return;
        }
        
        // Need to generate a temporary name for the destination file
        tempDestName = destFile.getName().substring(0,(destFile.getName().length())-3) + "tmp";
        tempDestFile = new File(destFile.getParent(),tempDestName);
        totalFileSize = (int)srcFile.length();
        try {
            inFile = new FileInputStream(srcFile);
        } catch (Exception e) {
            errorcode = 2;
            System.out.println("Error, can not open input file");
            // e.printStackTrace();
            return;
        }
        inChannel = inFile.getChannel();
        try {
            outFile = new FileOutputStream(tempDestFile);
        } catch (Exception e) {
            errorcode = 3;
            System.out.println("Error, can not open output file");
            e.printStackTrace();
            return;
        }
        outChannel = outFile.getChannel();        
        // All OK so far, we just need to set the thread running.
        ourThread = new Thread(this,"BWF_processor");
        ourThread.setDaemon(true);
        ourThread.start();
        status = 1;
        lastActivity = System.currentTimeMillis()/1000;
    }
    /** Alternate constructor for use when a destination file is not written.*/
    public BWFProcessor(File setSrcFile) {
        objectCount++;
        readOnly = true;
        
        srcFile = setSrcFile;
        if (!srcFile.exists() || !srcFile.canRead()){
            errorcode = 2;
            return;
        }
        // Need to generate a temporary name for the source file
        tempSourceName = srcFile.getName().substring(0,(srcFile.getName().length())-3) + "tmp";
        tempSourceFile = new File(srcFile.getParent(),tempSourceName);        
        /** Now to check that the source file is not being written to we will try to rename it.
         * If this works we will change the name back again. If it does not work we 
         * will return with an error code of 4 which indicates that the source file
         * can not be converted yet.
         */
        if (tempSourceFile.exists()) {
            // Temp file already exists, so does the source file.
            tempSourceFile.delete();
        }
        if (srcFile.renameTo(tempSourceFile)) {
            // We were able to rename the source file, good. Change the name back again
            tempSourceFile.renameTo(srcFile);
        } else {
            // We were unable to rename the source file so it is stil being written
            errorcode = 4;
            return;
        }
        totalFileSize = (int)srcFile.length();
        try {
            inFile = new FileInputStream(srcFile);
        } catch (Exception e) {
            errorcode = 2;
            System.out.println("Error, can not open input file");
            // e.printStackTrace();
            return;
        }
        inChannel = inFile.getChannel();
        // All OK so far, we just need to set the thread running.
        ourThread = new Thread(this,"BWF_processor");
        ourThread.setDaemon(true);
        ourThread.start();
        lastActivity = System.currentTimeMillis()/1000;
    }
    public BWFProcessor() {

    }
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
        BWFProcessor ourProcessor = new BWFProcessor(new File("D:/audio/input.wav"), new File("D:/audio/output.wav"));
        while (ourProcessor.isAlive()) {
            try {
                Thread.sleep(1500); // Sleep for 1.5s
                System.out.println("Bytes written so far " + ourProcessor.getBytesWritten()); 
            } catch (InterruptedException e) {
                System.out.println("Sleep interrupted." );            
            }
        }
        System.out.println("Error code is " + ourProcessor.getErrorCode());
        
    }
    
    public static int getObjectCount(){
        return objectCount;
    }    
    
    protected void finalize() {
        objectCount--;
    }    
    
    public int getErrorCode(){
        return errorcode;
    }
    
    public boolean isAlive(){
        return ourThread.isAlive();
    }
    
    public long getBytesWritten(){
        return bytesWritten;
    }

    public long getIndicatedFileSize() {
        return indicatedFileSize;
    }
 
    public int getStatus(){
        return status;
    }
    public boolean setSrcFile(File setSrcFile){
        objectCount++;
        readOnly = true;
        srcFile = setSrcFile;
        if (srcFile.exists() && srcFile.canRead()){
            return true;
        }
        return false;
    }

    public String getSrcFile(){
        return srcFile.toString();
    }
    
    public String getDestFile(){
        return destFile.toString();
    }
    
    
    
    public long getLastActivity(){
        return lastActivity;
    }
    
    public void run() {
        readFile(0);
        // Now if the errorcode is still 0 we can write the data to the output file unless we're in readOnly mode.
        status = 2;
        if (!readOnly) {
            status = 3;
            writeFile();
        }
        try {
            inFile.close();
        } catch (Exception e) {
            status = 5;
        }
        status = 4;
            
        
    }
    public void setMultipart (boolean setMultipart) {
        bMultipart = setMultipart;
    }
    public boolean readFile(long setSourceFileStart) {
        lSourceFileStart = setSourceFileStart;
        try {
            totalFileSize = (int)srcFile.length();
            inFile = new FileInputStream(srcFile);
            inChannel = inFile.getChannel();
            inChannel.position(lSourceFileStart);
            inChannel.read(sourceBytes);
            // The buffer should be full

        } catch (Exception e) {
            e.printStackTrace();
        }
        // Check that RIFF and WAVE are present in the first 12 bytes
        sourceBytes.order(ByteOrder.LITTLE_ENDIAN);
        sourceBytes.flip();
        if (bDebug) {
            System.out.println("" + sourceBytes.remaining() + " bytes remaining.");
        }
        StringBuffer strTemp;
        // Check the first four characters are RIFF
        strTemp = new StringBuffer(4);
        for (int i=0; i<4; i++) {
            strTemp.append((char)sourceBytes.get());
        }
        // Position now four bytes from start of file. strTemp should be RIFF check this here
        if (!(strTemp.toString()).equalsIgnoreCase("RIFF")) {
            // First four characters are wrong
            errorcode = 1;
            status = 5;
        }
        indicatedFileSize = sourceBytes.getInt();
        lSourceFileEnd = lSourceFileStart + indicatedFileSize + 8;
        // Position now eight bytes from start of file and we have read the indicated file size of all the chunks.
        strTemp = new StringBuffer(4);
        for (int i=0; i<4; i++) {
            strTemp.append((char)sourceBytes.get());
        }
        // Position now twelve bytes from start of file. strTemp should be WAVE check this here
        if (!(strTemp.toString()).equalsIgnoreCase("WAVE")) {
            // Characters 8 - 11 are wrong
            errorcode = 1;
            status = 5;
        }
        if (bDebug && errorcode == 0) {
            System.out.println("Start of file looks OK, now looking at end chunks.");
        }
        extractChunks(sourceBytes,startChunks);
        if (errorcode!= 0) {
            System.out.println("Error, can not parse input file " + srcFile);
//            jMediaHarvest.writeErrorString("Error, can not parse input file " + srcFile);
            try {
                outFile.close();
                inFile.close();
                // Rename the source file so we don't pick it up again
                String newSourceName = srcFile.getName().substring(0,(srcFile.getName().length())-3) + "err";
                // System.out.println("Renaming source file to " + srcFile.getParent() + " / " + newSourceName);
                srcFile.renameTo(new File(srcFile.getParent(),newSourceName));
            } catch (Exception e) {
		status = 5;
            }
            return false;
        }
        /** All of the files at the start of the file have been read. there could be more chunks at the
         * end of the file and we have to take these in to account when recalculating the file length.
         * To check for chunks at the end we need to compare all the chunks we know about and see if they add up to
         * the current file size.
         * The file size is stored in bytes 4 to 7 as a little endian int and should add up to
         * allchunks(chunksize + 8) +4
         * The total file size is actually 8 bytes more than this due to the RIFF and filesize characters at the beginning of the file.
         */
        calculatedFileSize = dataChunkSize + 8 + 4;
        // Thats the data chunk added, now to add the other chucks.
        chunkIterator = startChunks.iterator();
        while (chunkIterator.hasNext()){
            calculatedFileSize = calculatedFileSize + 8 + ((chunk)chunkIterator.next()).getoriginalckSIZE();
        }
        // If calculatedFileSize is equal to indicatedFileSize then we have read all the chunks.
        System.out.println("Total file size is " + totalFileSize);
        System.out.println("Indicated file size is " + indicatedFileSize);
        System.out.println("Calculated file size is " + calculatedFileSize);
        if (indicatedFileSize != calculatedFileSize) {
            /** Either the file is corrupt or there are more chunks at the end.
             * To read the end of the file we need to calculate where the data chunk ends
             * This should be at calculatedFileSize + 8
             * We also need to know the actual length of the file so we can read the end of it
             * in to a new ByteBuffer, this should be size of srcFile.
             * But first we should check that the amount of data which seems to be at
             * the end of the file is a sensible amount.
             */
            if (totalFileSize-calculatedFileSize > 100000 && !bMultipart) {
                // This can't be right, file must be corrupt or it is a multipart file
                errorcode = 1;
                status = 5;
                System.out.println("Error, can not process input file " + srcFile);
//                jMediaHarvest.writeErrorString("Error, can not process input file " + srcFile);
                // e.printStackTrace();
                return false;
            }
            ByteBuffer extraSourceBytes;
            int intEndBytes;
            if (bMultipart) {
                intEndBytes = (int)(indicatedFileSize - calculatedFileSize);
            } else {
                intEndBytes = (int)(totalFileSize - calculatedFileSize - 8);
            }
            System.out.println("Allocating " + intEndBytes + " bytes for the chunks at the end of the file.");
            extraSourceBytes = ByteBuffer.allocate(intEndBytes);
            extraSourceBytes.order(ByteOrder.LITTLE_ENDIAN);
            try {
                inChannel.position(lSourceFileStart + calculatedFileSize + 8);
                inChannel.read(extraSourceBytes);
                // The buffer should be full

            } catch (Exception e) {
                errorcode = 1;
                status = 5;
                System.out.println("Error, can not read input file while reading chunks at end of file " + srcFile);
    //            jMediaHarvest.writeErrorString("Error, can not read input file while reading chunks at end of file  " + srcFile);
                // e.printStackTrace();
                return false;
            }
            // The buffer extraSourceBytes should now contain the chunks at the end of the file.
            extraSourceBytes.flip();
            extractChunks(extraSourceBytes,endChunks);
            // The extra chunks have now been read so the file size calculation should be OK.
            calculatedFileSize = dataChunkSize + 8 + 4;
            // Thats the data chunk added, now to add the other chucks.
            chunkIterator = startChunks.iterator();
            while (chunkIterator.hasNext()){
                calculatedFileSize = calculatedFileSize + 8 + ((chunk)chunkIterator.next()).getoriginalckSIZE();
            }
            chunkIterator = endChunks.iterator();
            while (chunkIterator.hasNext()){
                calculatedFileSize = calculatedFileSize + 8 + ((chunk)chunkIterator.next()).getoriginalckSIZE();
            }
            // If calculatedFileSize is equal to indicatedFileSize then we have read all the chunks.
            // System.out.println("Indicated file size is " + indicatedFileSize);
            // System.out.println("Calculated file size is " + calculatedFileSize);
            if (indicatedFileSize != calculatedFileSize) {
                // errorcode = 1;
                System.out.println("WARNING - Potential problem with file " + srcFile + " calculated and indicated sizes do not match, using calculated size.");
                lSourceFileEnd = lSourceFileStart + calculatedFileSize + 8;
//                jMediaHarvest.writeErrorString("Potential problem with file " + srcFile + " calculated and indicated sizes do not match.");

                // return;
            }
        }
        if ((totalFileSize -8) != calculatedFileSize && !bMultipart) {
            errorcode = 1;
            System.out.println("Error, can not process input file " + srcFile+ " calculated and total file sizes do not match.");
//            jMediaHarvest.writeErrorString("Error, can not process input file " + srcFile+ " calculated and total file sizes do not match.");
            try {
                inFile.close();
            } catch (Exception e) {
                status = 5;
            }

            return false;
        }
        try {
            inFile.close();
        } catch (java.io.IOException e) {

        }
        return true;
    }
    public void writeFile(File setDestFile) {
        try {
            destFile = setDestFile;
            tempDestName = destFile.getName().substring(0,(destFile.getName().length())-3) + "tmp";
            tempDestFile = new File(destFile.getParent(),tempDestName);
            if (tempDestFile.exists()) {
                tempDestFile.delete();
            }
            outFile = new FileOutputStream(tempDestFile);
            outChannel = outFile.getChannel();
            inFile = new FileInputStream(srcFile);
            inChannel = inFile.getChannel();
            writeFile();
        } catch (java.io.FileNotFoundException e) {
            System.out.println("Error while writing new file " + destFile.toString());
        }
        
    }

    private void writeFile(){
        // First update the first 12 bytes of the ByteBuffer sourceBytes with the new file size
        // So we need to calculate the new file size.        
        calculatedFileSize = dataChunkSize + 8 + 4;
        chunk tempChunk;
        // Thats the data chunk size added in, now to add up the other chucks.
        chunkIterator = startChunks.iterator();
        while (chunkIterator.hasNext()){
            tempChunk = ((chunk)chunkIterator.next());
            calculatedFileSize = calculatedFileSize + 8 + tempChunk.getckSIZE();
            System.out.println("Chunk " + tempChunk.getckID() + "  " + tempChunk.getckSIZE());
        }
        System.out.println("Chunk data  " + dataChunkSize);
        chunkIterator = endChunks.iterator();
        while (chunkIterator.hasNext()){
            tempChunk = ((chunk)chunkIterator.next());
            calculatedFileSize = calculatedFileSize + 8 + tempChunk.getckSIZE();
            System.out.println("Chunk " + tempChunk.getckID() + "  " + tempChunk.getckSIZE());
        }            
        sourceBytes.limit(12);
        // Limit set 
        sourceBytes.position(4);
        sourceBytes.putInt((int)calculatedFileSize);
        // Updated file size written
        sourceBytes.position(0);
        try {
            outChannel.write(sourceBytes);
            // Thats the first 12 bytes written, now we need to add the bext chunk
            chunkIterator = startChunks.iterator();  
            while (chunkIterator.hasNext()){
                  tempChunk = (chunk)chunkIterator.next();
                  // Is this the bext chunk?
                  if ((tempChunk.getckID()).equalsIgnoreCase("bext")){
                      // Yes we have found the bext chunk, this needs to go at the start of the file
                      outChannel.write(tempChunk.getBytes());
                      // Now remove it from the Vector so it does not get added again
                      startChunks.remove(tempChunk);
                      break;
                  }
            }            
            // Now write the data from the other start chunks
            chunkIterator = startChunks.iterator();
            while (chunkIterator.hasNext()){
                outChannel.write(  ((chunk)chunkIterator.next()).getBytes()  );
            }
            // The start chunks are written, now we need to add the remainder of the source file starting from the data chunk.
            inChannel.position(dataChunkOffset);
            bytesWritten = dataChunkOffset;
            long lEndByte;
            if (bMultipart) {
//                lEndByte = calculatedFileSize + 12 + lSourceFileStart;
                lEndByte = lSourceFileEnd;
            } else {
                lEndByte = inChannel.size();
            }
            System.out.println("Calculated file size is " + calculatedFileSize + " data chunk offset is " + dataChunkOffset);
            System.out.println("Writing output file starting at " + bytesWritten + " offset on input file and finishing at  " + lEndByte + " offset on input file.");
            while (bytesWritten<lEndByte) {
                
                try {
                    Thread.sleep(Main.randomNumber.nextInt(200) + 500 +(lEndByte/600000)); // Sleep for about 0.6 plus 1s per 600M file size
                } catch (InterruptedException e) {
                    System.out.println("Sleep interrupted." );            
                } 
                synchronized(Main.randomNumber) {
                    // There is only one of those objects so this code can only be excecuted by one thread at a time.

                    if (lEndByte-bytesWritten<5000000) {
                        bytesWritten += inChannel.transferTo(
                            bytesWritten,
                            lEndByte-bytesWritten,
                            outChannel);                    
                    } else {
                        bytesWritten += inChannel.transferTo(
                            bytesWritten,
                            5000000, 
                            outChannel);                    
                    }

                    lastActivity = System.currentTimeMillis()/1000;                    
                    
                }

                // No matter how large the file being copied the thread should pass this point regularly.
                
               
                // Good place for a yield or WDT?
            }
            outFile.close();
            // Add a cvt file with the same name as the source file so we don't pick it up again
            String newSourceName = srcFile.getName().substring(0,(srcFile.getName().length())-3) + "cvt";
            // System.out.println("Renaming source file to " + srcFile.getParent() + " / " + newSourceName);
            // srcFile.renameTo(new File(srcFile.getParent(),newSourceName));
            File cvtFile = new File(srcFile.getParent(),newSourceName);
            cvtFile.createNewFile();
            
            // Rename the dest file extension so that it will be picked up by the next process
            tempDestFile.renameTo(destFile);
            inFile.close();

        } catch (Exception e) {
            System.out.println("Error while writing new file.");
//            jMediaHarvest.writeErrorString("Error while writing new file." + destFile);
            errorcode = 3;
            e.printStackTrace();
        }         
    }
    private void extractChunks(ByteBuffer inputData, Vector chunkVector) {
        /** This will process the given Byte Buffer and add chunk objects to the given vector.
         * The values of errorcount and data chunk infomrtion will be updated if required.
         * When this method is called the pointer must be at the start of the ckID
         */
        while (inputData.hasRemaining()) {
            // We are going to read the next 8 bytes, need to check that there are 8 bytes available.
            if (bDebug) {
                System.out.println(inputData.remaining() + " bytes remaining.");
            }
            if (inputData.remaining() < 8) {
                // Not enough bytes left and data block not found!
                errorcode = 1;
                System.out.println("Unexpectedly reached the end of data, file could be corrupt!");  
                return;
            }
            StringBuffer strTemp = new StringBuffer(4);
            for (int i=0; i<4; i++) {
                strTemp.append((char)inputData.get());
            }
            // Position now four bytes in.
            tempckID = strTemp.toString();   
            tempckSIZE = inputData.getInt();
            if (tempckID.equalsIgnoreCase("bext")){
                bHasBextChunk = true;
            }

            if (bDebug) {
                System.out.println("Found chunk  " + tempckID + " size " + tempckSIZE + " at position " + (inputData.position() - 8));
            }
            // Position now eight bytes in
            if (tempckID.equalsIgnoreCase("data")) {
                // We have found the data chunk, don't want to load this in, just save the size.
                dataChunkSize = tempckSIZE;
                dataChunkOffset = lSourceFileStart + inputData.position() - 8; // This is the offset from the start of file
                // System.out.println("Data chunk size is   " + dataChunkSize);
                return;
            } else {
                try {

                    inputData.position(inputData.position() - 8);
                    // Position now back at the start of the chunk
                    inputData.limit(inputData.position() + (int)tempckSIZE + 8);
                    // Limit now set at end of current chunk
                } catch (IllegalArgumentException e) {
                    errorcode = 1;
                    System.out.println("Unexpectedly reached the end of data while extracting chunk " + tempckID + ", file could be corrupt!\nError code is " + e);
                    return;
                }
                if (tempckID.equalsIgnoreCase("bext")) {
                    chunkVector.add(new chunk(inputData.slice(),858));
                } else {
                    chunkVector.add(new chunk(inputData.slice()));
                }

                // Chunk created
                inputData.limit(inputData.capacity());
                // Limit set back to the end
                inputData.position(inputData.position() + (int)tempckSIZE + 8);
                // Position now set at start of next chunk                

            } 
        }
        
    }
    public int getSampleRate(){
        chunkIterator = startChunks.iterator();
        chunk tempChunk;
        while (chunkIterator.hasNext()){
              tempChunk = (chunk)chunkIterator.next();
              // Is this the bext chunk?
              if ((tempChunk.getckID()).equalsIgnoreCase("fmt ") || (tempChunk.getckID()).equalsIgnoreCase(" fmt")){
                  // Yes we have found the fmt chunk.
                  ByteBuffer byteData = tempChunk.getBytes();
                  byteData.order(ByteOrder.LITTLE_ENDIAN);
                  byteData.position(12);
                  return byteData.getInt();
//                  break;
              }
        }
        return 0;
    }
    public boolean hasBextChunk() {
        return bHasBextChunk;
    }
    public String getBextTitle() {
        if (!hasBextChunk()) {
            return "";
        }
        chunkIterator = startChunks.iterator();
        chunk tempChunk;
        while (chunkIterator.hasNext()){
              tempChunk = (chunk)chunkIterator.next();
              // Is this the bext chunk?
              if ((tempChunk.getckID()).equalsIgnoreCase("bext")){
                  // Yes we have found the bext chunk.
                  ByteBuffer byteData = tempChunk.getBytes();
                  byteData.order(ByteOrder.LITTLE_ENDIAN);
                  byteData.position(8);
                  StringBuilder strTemp = new StringBuilder(256);
                  byte tempByte;
                  for (int i=0; i<256; i++) {
                       tempByte = byteData.get();
                      if (tempByte > 0) {
                          strTemp.append((char)tempByte);
                      }
                  }
                  return strTemp.toString();
            }
        }
        return "";
        
    }
    public boolean setBextTitle(String setTitle) {
        chunk tempChunk = null;
        if (!hasBextChunk()) {
            tempChunk = createBextChunk();
            startChunks.add(tempChunk);
            bHasBextChunk = true;
        } else {
            chunkIterator = startChunks.iterator();
            while (chunkIterator.hasNext()){
                tempChunk = (chunk)chunkIterator.next();
                if ((tempChunk.getckID()).equalsIgnoreCase("bext")){
                    break;
                }
            }
        }
        if (setTitle.length() > 256) {
            setTitle = setTitle.substring(0, 256);
        }
        ByteBuffer byteData = ByteBuffer.allocate(256);
        byteData.put(setTitle.getBytes());
        byte nullByte = 0;
        while (byteData.hasRemaining()) {
            byteData.put(nullByte);
        }
        byteData.position(0);
        if (tempChunk instanceof chunk) {
            return tempChunk.setBytes(byteData, 0);
        } else {
            return false;
        }
    }
    public String getBextOriginatorRef() {
        if (!hasBextChunk()) {
            return "";
        }
        chunkIterator = startChunks.iterator();
        chunk tempChunk;
        while (chunkIterator.hasNext()){
              tempChunk = (chunk)chunkIterator.next();
              // Is this the bext chunk?
              if ((tempChunk.getckID()).equalsIgnoreCase("bext")){
                  // Yes we have found the bext chunk.
                  ByteBuffer byteData = tempChunk.getBytes();
                  byteData.order(ByteOrder.LITTLE_ENDIAN);
                  byteData.position(296);
                  StringBuilder strTemp = new StringBuilder(32);
                  byte tempByte;
                  for (int i=0; i<32; i++) {
                       tempByte = byteData.get();
                      if (tempByte > 0) {
                          strTemp.append((char)tempByte);
                      }
                  }
                  return strTemp.toString();
            }
        }
        return "";
        
    }
    public boolean setBextOriginatorRef(String setTitle) {
        chunk tempChunk = null;
        if (!hasBextChunk()) {
            tempChunk = createBextChunk();
            startChunks.add(tempChunk);
            bHasBextChunk = true;
        } else {
            chunkIterator = startChunks.iterator();
            while (chunkIterator.hasNext()){
                tempChunk = (chunk)chunkIterator.next();
                if ((tempChunk.getckID()).equalsIgnoreCase("bext")){
                    break;
                }
            }
        }
        if (setTitle.length() > 32) {
            setTitle = setTitle.substring(0, 32);
        }
        ByteBuffer byteData = ByteBuffer.allocate(32);
        byteData.put(setTitle.getBytes());
        byte nullByte = 0;
        while (byteData.hasRemaining()) {
            byteData.put(nullByte);
        }
        byteData.position(0);

        if (tempChunk instanceof chunk) {
            return tempChunk.setBytes(byteData, 288);
        } else {
            return false;
        }
    }
    public long getBextTimeCodeOffset() {
        if (!hasBextChunk()) {
            return 0;
        }
        chunkIterator = startChunks.iterator();
        chunk tempChunk;
        while (chunkIterator.hasNext()){
              tempChunk = (chunk)chunkIterator.next();
              // Is this the bext chunk?
              if ((tempChunk.getckID()).equalsIgnoreCase("bext")){
                  // Yes we have found the bext chunk.
                  ByteBuffer byteData = tempChunk.getBytes();
                  byteData.order(ByteOrder.LITTLE_ENDIAN);
                  byteData.position(346);
                  return byteData.getLong();
            }
        }
        return 0;

    }
    public chunk createBextChunk() {
        chunk tempChunk = null;
        ByteBuffer byteData = ByteBuffer.allocate(1032);
        byteData.order(ByteOrder.LITTLE_ENDIAN);
        byteData.put("bext".getBytes());
        byteData.putInt(1024);
        byteData.position(328);
        byteData.put("1970-01-0100-00-00".getBytes());
        byteData.putLong(0);
        byteData.putInt(0);
        byteData.position(0);
        tempChunk = new chunk(byteData);
        return tempChunk;
    }
}
