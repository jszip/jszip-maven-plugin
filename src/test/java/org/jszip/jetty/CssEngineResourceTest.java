package org.jszip.jetty;

import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.eclipse.jetty.util.resource.FileResource;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;
import org.jszip.pseudo.io.PseudoFileSystem;
import org.jszip.sass.SassEngine;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author stephenc
 * @since 01/02/2013 11:58
 */
public class CssEngineResourceTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private File file;

    public String loadResource(String name) throws IOException {
        InputStream stream = null;
        try {
            stream = getClass().getResourceAsStream(name);
            return IOUtil.toString(stream);
        } finally {
            IOUtil.close(stream);
        }
    }

    @Test
    public void smokes() throws IOException {
        final PseudoFileSystem fs = new PseudoFileSystem(new PseudoFileSystem.FileLayer("/virtual/", folder.getRoot()));
        file = new File(folder.getRoot(), "main.scss");
        FileUtils.fileWrite(file, "utf-8", loadResource("main.scss"));
        SassEngine engine = new SassEngine(fs, "utf-8");
        final CssEngineResource cssEngineResource = new CssEngineResource(fs, engine, "/virtual/main.scss");
        InputStream inputStream = cssEngineResource.getInputStream();
        try {
            assertThat(IOUtil.toString(inputStream), containsString("8px"));
        } finally {
            IOUtil.close(inputStream);
        }
        inputStream = cssEngineResource.getInputStream();
        try {
            assertThat(IOUtil.toString(inputStream), containsString("8px"));
        } finally {
            IOUtil.close(inputStream);
        }
        ResourceCollection root = new ResourceCollection(new VirtualDirectoryResource(cssEngineResource, ""));
        assertThat(root.getResource("/main.css"), is((Resource)cssEngineResource));

        root = new ResourceCollection(new VirtualDirectoryResource(cssEngineResource, "css"));
        assertThat(root.getResource("/css"), notNullValue());
        assertThat(root.getResource("/css/main.css"), is((Resource)cssEngineResource));

        assertThat(cssEngineResource.list(), is((String[])null));
        assertThat(cssEngineResource.exists(), is(true));
        assertThat(cssEngineResource.isDirectory(), is(false));
        assertThat(cssEngineResource.lastModified(), is(file.lastModified()));
//        assertThat(resource.length(), is(file.length()));
//        assertThat(resource.getURL(), is(file.toURI().toURL()));
//        assertThat(resource.getURI(), is(file.toURI()));
//        assertThat(resource.getFile(), is(file));
//        assertThat(resource.getName(), is(file.getAbsolutePath()));
        assertThat(cssEngineResource.addPath(""), is((Resource)cssEngineResource));

        Resource resource = new VirtualDirectoryResource(cssEngineResource, "main.css");
        assertThat(resource.list(), is(new String[]{"main.css"}));
        assertThat(resource.exists(), is(true));
        assertThat(resource.isDirectory(), is(true));
        assertThat(resource.lastModified(), is(file.lastModified()));
//        assertThat(resource.length(), is(file.length()));
//        assertThat(resource.getURL(), is(file.toURI().toURL()));
//        assertThat(resource.getURI(), is(file.toURI()));
//        assertThat(resource.getFile(), is(file));
//        assertThat(resource.getName(), is(file.getAbsolutePath()));
        assertThat(resource.addPath(""), is(resource));
        assertThat(resource.addPath("main.css"), is((Resource)cssEngineResource));
        assertThat(resource.getListHTML("about:foo", false), containsString("<A HREF=\"about:foo/main.css\">main.css&nbsp;</A></TD>"));

        Resource resource2 = new VirtualDirectoryResource(resource, "scss");
        assertThat(resource2.list(), is(new String[]{"scss/"}));
        assertThat(resource2.exists(), is(true));
        assertThat(resource2.isDirectory(), is(true));
        assertThat(resource2.lastModified(), is(file.lastModified()));
//        assertThat(resource.length(), is(file.length()));
//        assertThat(resource.getURL(), is(file.toURI().toURL()));
//        assertThat(resource.getURI(), is(file.toURI()));
//        assertThat(resource.getFile(), is(file));
//        assertThat(resource.getName(), is(file.getAbsolutePath()));
        assertThat(resource2.addPath(""), is(resource2));
        assertThat(resource2.addPath("scss"), is(resource));
        assertThat(resource2.addPath("scss/main.css"), is((Resource)cssEngineResource));
        assertThat(resource2.getListHTML("about:foo", false), containsString("<A HREF=\"about:foo/scss/\">scss/&nbsp;</A></TD>"));

    }

    @Test
    public void compat() throws IOException, URISyntaxException {
        final PseudoFileSystem fs = new PseudoFileSystem(new PseudoFileSystem.FileLayer("/virtual/", folder.getRoot()));
        final File file = new File(folder.getRoot(), "main.scss");
        FileUtils.fileWrite(file, "utf-8", loadResource("main.scss"));
        Resource resource = new FileResource(file.toURI().toURL());
        assertThat(resource.list(), is((String[])null));
        assertThat(resource.exists(), is(true));
        assertThat(resource.isDirectory(), is(false));
        assertThat(resource.lastModified(), is(file.lastModified()));
        assertThat(resource.length(), is(file.length()));
        assertThat(resource.getURL(), is(file.toURI().toURL()));
        assertThat(resource.getURI(), is(file.toURI()));
        assertThat(resource.getFile(), is(file));
        assertThat(resource.getName(), is(file.getAbsolutePath()));
        assertThat(resource.addPath(""), is(resource));
    }

    @Test
    public void compat2() throws IOException, URISyntaxException {
        final PseudoFileSystem fs = new PseudoFileSystem(new PseudoFileSystem.FileLayer("/virtual/", folder.getRoot()));
        final File file = folder.getRoot();
        FileUtils.fileWrite(new File(folder.getRoot(), "main.scss"), "utf-8", loadResource("main.scss"));
        Resource resource = new FileResource(file.toURI().toURL());
        assertThat(resource.list(), is(new String[]{"main.scss"}));
        assertThat(resource.exists(), is(true));
        assertThat(resource.isDirectory(), is(true));
        assertThat(resource.lastModified(), is(file.lastModified()));
        assertThat(resource.length(), is(file.length()));
        assertThat(resource.getURL(), is(file.toURI().toURL()));
        assertThat(resource.getURI(), is(file.toURI()));
        assertThat(resource.getFile(), is(file));
        assertThat(resource.getName(), is(file.getAbsolutePath()));
        assertThat(resource.addPath(""), is(resource));
        assertThat(resource.addPath("main.scss"), is(not(resource)));
        assertThat(resource.getListHTML("about:foo", false), containsString("<A HREF=\"about:foo/main.scss\">main.scss&nbsp;</A></TD>"));
    }

}
