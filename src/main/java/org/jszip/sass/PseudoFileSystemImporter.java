package org.jszip.sass;

import org.codehaus.plexus.util.IOUtil;
import org.jszip.pseudo.io.PseudoFile;
import org.jszip.pseudo.io.PseudoFileInputStream;
import org.jszip.pseudo.io.PseudoFileSystem;
import org.mozilla.javascript.Context;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

/**
 * @author stephenc
 * @since 31/01/2013 12:01
 */
public class PseudoFileSystemImporter {

    private final PseudoFileSystem fs;
    private final String encoding;

    public PseudoFileSystemImporter(PseudoFileSystem fs, String encoding) {
        this.fs = fs;
        this.encoding = encoding;
    }

    /**
     * Find a Sass file, if it exists.
     * <p/>
     * This is the primary entry point of the Importer.
     * It corresponds directly to an `@import` statement in Sass.
     * It should do three basic things:
     * <p/>
     * * Determine if the URI is in this importer's format.
     * If not, return nil.
     * * Determine if the file indicated by the URI actually exists and is readable.
     * If not, return nil.
     * * Read the file and place the contents in a {Sass::Engine}.
     * Return that engine.
     * <p/>
     * If this importer's format allows for file extensions,
     * it should treat them the same way as the default {Filesystem} importer.
     * If the URI explicitly has a `.sass` or `.scss` filename,
     * the importer should look for that exact file
     * and import it as the syntax indicated.
     * If it doesn't exist, the importer should return nil.
     * <p/>
     * If the URI doesn't have either of these extensions,
     * the importer should look for files with the extensions.
     * If no such files exist, it should return nil.
     * <p/>
     * The {Sass::Engine} to be returned should be passed `options`,
     * with a few modifications. `:syntax` should be set appropriately,
     * `:filename` should be set to `uri`,
     * and `:importer` should be set to this importer.
     *
     * @param uri [String] The URI to import.
     * @return the contents of the uri.
     */
    public String find(String uri) throws IOException {
        Context.enter();
        try {
            fs.installInContext();
            final PseudoFile file = fs.getPseudoFile(uri);
            if (file.isFile()) {
                InputStream is = null;
                try {
                    is = new PseudoFileInputStream(file);
                    return IOUtil.toString(is, encoding);
                } finally {
                    IOUtil.close(is);
                }
            }
            return null;
        } finally {
            fs.removeFromContext();
            Context.exit();
        }
    }

    /**
     * Returns the time the given Sass file was last modified.
     * <p/>
     * If the given file has been deleted or the time can't be accessed
     * for some other reason, this should return nil.
     *
     * @param uri [String] The URI of the file to check.
     *            Comes from a `:filename` option set on an engine returned by this importer.
     * @return [Time, nil]
     */
    public Date mtime(String uri) {
        Context.enter();
        try {
            fs.installInContext();
            final PseudoFile file = fs.getPseudoFile(uri);
            if (file.isFile()) {
                return new Date(file.lastModified());
            }
            return null;
        } finally {
            fs.removeFromContext();
            Context.exit();
        }
    }

}
