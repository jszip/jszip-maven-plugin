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

Q: When building, how do I define where my dependencies are mapped to?

A: The http://jszip.org/jszip-maven-plugin/unpack-mojo.html#mappings allows users to map individual dependencies whithin the Webapp.
Example: https://gist.github.com/4697135

Q: When mapping individual dependencies, do I have to map them all one by one?


A: No, you can use * to indicate wildcard matching and the groupId (as seen in the previouse question) is optional, if yu don't specify it the assumption is *
The following example maps all dependencies whith the goup Id 'org.jszop.redist:*' to 'scripts/libs'.
Example: https://gist.github.com/4697166

Licensing
---------

Please see the file called LICENSE.TXT

Trademarks
----------
Apache, Apache Maven, Maven and the Apache feather logo are trademarks of The Apache Software Foundation.
