/*
 * Javolution - Java(TM) Solution for Real-Time and Embedded Systems
 * Copyright (C) 2005 - Javolution (http://javolution.org/)
 * All rights reserved.
 * 
 * Permission to use, copy, modify, and distribute this software is
 * freely granted, provided that this notice is preserved.
 */

package _templates.java.io;

import java.io.IOException;
import java.io.OutputStream;

import _templates.java.lang.UnsupportedOperationException;


public class FileOutputStream extends OutputStream {
    
    public FileOutputStream(String name) throws FileNotFoundException {
    }
    public FileOutputStream(File file) throws FileNotFoundException {
    }

    public void write(int arg0) throws IOException {
        throw new UnsupportedOperationException(
            "File operations not supported for J2ME build");
    }

}