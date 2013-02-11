JSZip Maven Plugin
==================

What is it?
-----------

An Apache Maven plugin for generating jszip modules and using jszip modules from Maven based projects.

Documentation
-------------

The plugin documentation can be found at http://jszip.org/jszip-maven-plugin

FAQ's
-----

#####Q: When building, how do I define where my dependencies are mapped to?#####

A: The http://jszip.org/jszip-maven-plugin/unpack-mojo.html#mappings allows users to map individual dependencies whithin the Webapp.

__Example:__
```xml
<mappings>
  <mapping>
    <select>groupId:artifactId</select>
    <path>path/relative/to/webapp/root</path>
  </mapping>
</mappings>
```

#####Q: When mapping individual dependencies, do I have to map them all one by one?#####


A: No, you can use __'*'__ to indicate a wildcard matching and the _groupId_ (as seen in the previouse question) is optional, if you don't specify it, the assumption is __'*'__.

__Example:__

Maps all dependencies whith the _goupId_ __'org.jszop.redist'__ to __'scripts/libs'__.
```xml
<mapping>
  <mapping>
    <select>org.jszop.redist:*</select>
    <path>scripts/libs</path>
  </mapping>
</mapping>
```

Licensing
---------

Please see the file called LICENSE.TXT

Trademarks
----------
Apache, Apache Maven, Maven and the Apache feather logo are trademarks of The Apache Software Foundation.
