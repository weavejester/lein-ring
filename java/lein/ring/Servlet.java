package lein.ring;

import clojure.lang.RT;
import clojure.lang.IFn;
import clojure.lang.Var;
import clojure.lang.Symbol;


import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

public class Servlet extends GenericServlet {

    public IFn handler;

    public static Var REQUIRE = RT.var("clojure.core", "require");
    public static Var SYMBOL = RT.var("clojure.core", "symbol");
    public static Var RESOLVE = RT.var("clojure.core", "resolve");
    public static Var DEREF = RT.var("clojure.core", "deref");
    public static Symbol SERVLET_NS =  Symbol.create("ring.util.servlet");
    public static Var MAKE_SERVICE_METHOD =
        RT.var("ring.util.servlet", "make-service-method");

    static {
        REQUIRE.invoke(SERVLET_NS);
    }

    // TODO: destroy

    public Servlet() {;}

    public void init() throws ServletException {
        ServletConfig config = this.getServletConfig();

        // Handler
        require(config.getInitParameter("ns-name"));
        handler = createServiceMethod(config.getInitParameter("ns-name"),
                                      config.getInitParameter("handler-name"));

        // Init
        require(config.getInitParameter("init-ns-name"));
        invoke(config.getInitParameter("init-ns-name"),
               config.getInitParameter("init-name"));
    }

    public void service(ServletRequest request, ServletResponse response) {
        handler.invoke(this, request, response);
    }

    private void require(String namespace) {
        REQUIRE.invoke(SYMBOL.invoke(namespace));
    }

    private IFn createServiceMethod(String namespace, String handler) {
        return (IFn) MAKE_SERVICE_METHOD.invoke(DEREF.invoke(RT.var(namespace, handler)));
    }

    private void invoke(String namespace, String var) {
        RT.var(namespace, var).invoke();
    }

}
