package org.jszip.pseudo.io;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * @author stephenc
 * @since 30/01/2013 00:43
 */
public class PseudoFileReader extends InputStreamReader {

    public PseudoFileReader(PseudoFile file) throws IOException {
        super(new PseudoFileInputStream(file));
    }

    public PseudoFileReader(String filename) throws IOException {
        super(new PseudoFileInputStream(filename));
    }

}
