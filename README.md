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

    :dev-dependencies [[lein-ring "0.3.2"]]

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
* `:context-path` - The context path of the servlet (in web.xml).
* `:servlet-path-info?` - 
  If true, a :path-info key is added to the request map. Defaults to true.

These keys should be placed under the `:ring` key in `project.clj`,
and are optional values. If not supplied, default values will be used instead.

A war file can also include additional resource files, such as images or
stylesheets. These should be placed in the directory specified by the
Leiningen `:resources-path` key, which defaults to "resources".
