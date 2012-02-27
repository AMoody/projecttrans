package jprojecttranslator;
/*
 * chunk.java
 *
 * Created on 09 September 2005, 17:26
 */

/**
 * This represents a chunk which would be part of a RIFF file.
 * The constructor takes a ByteBuffer object and starts at the beginning
 * The first four bytes are the ascii code for this chunk.
 * The next four bytes are a little endian representation of the total length of the data
 * The next bytes are the actual data, the format depends on the type of chunk.
 * @author  moodya71
 */

import java.nio.*;
public class chunk {
    private String ckID;
    private int ckSIZE;
    private int originalckSIZE;
    private ByteBuffer data;
    private int minSize = 0;
    
    /** Creates a new instance of chunk */
    public chunk(ByteBuffer inputData) {
        // The first four bytes are the ascii character code, not unicode
        StringBuilder strTemp = new StringBuilder(4);
        for (int i=0; i<4; i++) {
            strTemp.append((char)inputData.get());
        }
        ckID = strTemp.toString();
        // the next four byte are the size of the data in little endian format
        inputData.order(ByteOrder.LITTLE_ENDIAN);
        ckSIZE = inputData.getInt();
        originalckSIZE = ckSIZE;
        data = ByteBuffer.allocate(ckSIZE);
        while ((inputData.hasRemaining() && data.hasRemaining())) {
            data.put(inputData.get());
        }
        // System.out.println("chunk object created with ckID of  " + ckID + " and size is " + ckSIZE);
    }
    
    /** Alternate constructor allows the creation of a minimum size for the chunk*/
    public chunk(ByteBuffer inputData, int setMinSize){
        minSize = setMinSize;
        // The first four bytes are the ascii character code, not unicode
        StringBuilder strTemp = new StringBuilder(4);
        for (int i=0; i<4; i++) {
            strTemp.append((char)inputData.get());
        }
        ckID = strTemp.toString();
        // the next four byte are the size of the data in little endian format
        inputData.order(ByteOrder.LITTLE_ENDIAN);
        ckSIZE = inputData.getInt();
        originalckSIZE = ckSIZE;
        // Check to see if the minimum size requirement is met
        if (ckSIZE < minSize) {
            ckSIZE = minSize;
        }
        data = ByteBuffer.allocate(ckSIZE);
        while ((inputData.hasRemaining() && data.hasRemaining())) {
            data.put(inputData.get());
        }
        // Now fill the remainder of the chunk with nulls
        byte nullByte = 0;
        while (data.hasRemaining()) {
            data.put(nullByte);
        }
        // System.out.println("chunk object created with ckID of  " + ckID + " and size is " + ckSIZE);        
        
    }
    
    public String getckID(){
        return ckID;
    }
    
    public int getckSIZE(){
        return ckSIZE;
    }
    
    public int getoriginalckSIZE(){
        return originalckSIZE;
        
    }
    
    public ByteBuffer getBytes(){
        ByteBuffer byteData = ByteBuffer.allocate(ckSIZE + 8);
        byteData.position(0);
        data.position(0);
        byteData.order(ByteOrder.LITTLE_ENDIAN);
        // Add the ckID at the start
        byteData.put(ckID.getBytes());
        byteData.putInt(ckSIZE);
        while ((byteData.hasRemaining() && data.hasRemaining())) {
            byteData.put(data.get());
        }  
        byteData.flip();
        return byteData;
        
    }
    public boolean setBytes(ByteBuffer setBytes, int setPosition) {
        if (setBytes.capacity() + setPosition > data.capacity()) {
            return false;
        }
        data.position(setPosition);
        int i = 0;
        while ((setBytes.hasRemaining() && data.hasRemaining())) {
            data.put(setBytes.get());
            i++;
        }
        System.out.println("" + i + " bytes written to chunk at " + setPosition + " chunk data size is " + data.capacity());
        return true;
    }
    
}
