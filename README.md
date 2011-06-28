# Lein-Ring

Lein-Ring is a [Leiningen][1] plugin that automates common [Ring][2]
tasks.

It provides commands to start a development web server, and to turn a
Ring handler into a standard war file.

[1]: https://github.com/technomancy/leiningen
[2]: https://github.com/mmcgrana/ring 

## Usage

To use Lein-Ring, add it as a development dependency to your
`project.clj` file:

    :dev-dependencies [[lein-ring "0.4.4"]]

And then add a new `:ring` key that contains a map of configuration
options. At minimum there must be a `:handler` key that references
your Ring handler:

    :ring {:handler hello-world.core/handler}

When this is set, you can use Lein-Ring's commands.

### Starting a development web server

The following command will start a development web server, and opens a
web browser to the root page:

    lein ring server

The server monitors your source directory for file modifications, so any
altered files will automatically be reloaded.

By default, this command attempts to find a free port, starting at
3000, but you can specify your own port as an argument:

    lein ring server 4000

The server-headless command works like the server command, except that
it doesn't open a web browser:

    lein ring server-headless

    lein ring server-headless 4000

### Compiling a war

This next command will generate a war file from your handler:

    lein ring war

A servlet class and web.xml file will be generated automatically, and
your application packaged up in a war file.

Like the `lein jar` command, you can specify the filename being
generated as an additional option:

    lein ring war my-app.jar

Also provided is a `lein ring uberwar` command, which packages up all
the dependencies into the war:

    lein ring uberwar

Currently the following options are supported:

* `:servlet-class` - The servlet class name.
* `:servlet-name` -
  The name of the servlet (in web.xml). Defaults to the handler name.
* `:url-pattern` -
  The url pattern of the servlet mapping (in web.xml). Defaults to "/*".
* `:servlet-path-info?` -
  If true, a `:path-info` key is added to the request map. Defaults to true.
* `:init` -
  A hook to perform any one time initialization tasks. When generating
  a servlet, a servlet context listener is created, and the hook is
  called during its initialization. When running lein ring server,
  it's called before the server starts. This function should take no
  arguments.
* `:destroy` -
  A hook to perform shutdown tasks. It should also take no arguments.
* `:listener-class` -
  Class used for servlet init/destroy functions. Called listener
  because underneath it uses a ServletContextListener.

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
