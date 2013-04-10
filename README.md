# Lein-Ring

Lein-Ring is a [Leiningen][1] plugin that automates common [Ring][2]
tasks.

It provides commands to start a development web server, and to turn a
Ring handler into a standard war file.

[1]: https://github.com/technomancy/leiningen
[2]: https://github.com/mmcgrana/ring 


## Install

To use Lein-Ring, add it as a plugin to your `project.clj` file or
your global profile:

    :plugins [[lein-ring "0.8.3"]]

Or, if you are using a version of Leiningen prior to 1.7.0:

    :dev-dependencies [[lein-ring "0.8.3"]]

Then add a new `:ring` key to your `project.clj` file that contains a
map of configuration options. At minimum there must be a `:handler`
key that references your Ring handler:

    :ring {:handler hello-world.core/handler}

When this is set, you can use Lein-Ring's commands.

## General options

As well as the handler, you can specify several additional options via
your `project.clj` file:

* `:init` -
  A function to be called once before your handler starts. It should
  take no arguments. If you've compiled your Ring application into a
  war-file, this function will be called when your handler servlet is
  first initialized.

* `:destroy` -
  A function called before your handler exits or is unloaded. It
  should take no arguments. If your Ring application has been compiled
  into a war-file, then this will be called when your hander servlet
  is destroyed.

* `:adapter` -
  A map of options to be passed to the Ring adapter. This has no
  effect if you're deploying your application as a war-file.


## Environment variables

Lein-Ring pays attention to several environment variables, including:

* `PORT`    - the port the web server uses for HTTP
* `SSLPORT` - the port the web server uses for HTTPS

These will override any options specified in the `project.clj` file,
but won't override any options specified at the command line.


## Starting a web server

The following command will start a development web server, and opens a
web browser to the root page:

    lein ring server

If the `LEIN_NO_DEV` environment variable is **not** set, the server
will monitor your source directory for file modifications, and any
altered files will automatically be reloaded.

By default, this command attempts to find a free port, starting at
3000, but you can specify your own port as an argument:

    lein ring server 4000

The server-headless command works like the server command, except that
it doesn't open a web browser:

    lein ring server-headless

    lein ring server-headless 4000


## Web server options

The following options affect the behavior of the web server started by
`lein ring server`:

* `:port` - The server port or port range

* `:stacktraces?` -
  If true, display a stacktrace when an exception is thrown.
  Defaults to true in development mode, false in production.

* `:auto-reload?` -
  If true, automatically reload modified source files. Defaults to
  true in development mode, false in production.

* `:reload-paths` -
  A collection of directory paths that can trigger a reload. By
  default this takes all directories in the project classpath.

* `:auto-refresh?` -
  If true, automatically refresh the browser when source or resource
  files are modified. Defaults to false.

* `:nrepl` - 
  A map of `:start?` and (optionally) `:port` keys. If `:start?` is true, 
  open up an nREPL server on the given port. `:start?` defaults to false, 
  `:port` defaults to an arbitrary free port.

## Executable jar files

Lein-Ring can generate executable jar files for deployment purposes:

    lein ring uberjar

This generates a jar file with all dependencies. You can then copy the
file to your web server and execute it with:

    java -jar <project>-<version>-standalone.jar


## War files

### Compiling

Lein-Ring can generate war files that can be loaded onto legacy Java
web services such as Apache Tomcat:

    lein ring war

A servlet class and web.xml file will be generated automatically, and
your application packaged up in a war file.

Like the `lein jar` command, you can specify the filename being
generated as an additional option:

    lein ring war my-app.war

Also provided is a `lein ring uberwar` command, which packages up all
the dependencies into the war:

    lein ring uberwar

The following war-specific options are supported:

* `:war-exclusions` -
  A list of regular expressions for excluding files from the target
  war. Defaults to excluding hidden files.

* `:servlet-class` -
  The servlet class name.

* `:servlet-name` -
  The name of the servlet (in web.xml). Defaults to the handler name.

* `:url-pattern` -
  The url pattern of the servlet mapping (in web.xml). Defaults to "/*".

* `:servlet-path-info?` -
  If true, a `:path-info` key is added to the request map. Defaults to true.

* `:listener-class` -
  Class used for servlet init/destroy functions. Called listener
  because underneath it uses a ServletContextListener.

* `:web-xml` -
  web.xml file to use in place of auto-generated version (relative to project root).

These keys should be placed under the `:ring` key in `project.clj`,
and are optional values. If not supplied, default values will be used instead.

### Resources

A war file can also include additional resource files, such as images or
stylesheets. These should be placed in the directory specified by the
Leiningen `:resources-path` key, which defaults to "resources". These
resources will be placed on the classpath.

However, there is another sort of resource, one accessed through the
`ServletContext` object. These resources are usually not on the classpath,
and are instead placed in the root of the war file. If you happen to need this
functionality, you can place your files in the directory specified by the
`:war-resources-path` key, which defaules to "war-resources". It's recommended
that you only use WAR resources for compatibility with legacy Java interfaces;
under most circumstances, you should use the normal `:resources-path` instead.
