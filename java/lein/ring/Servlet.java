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
    public static IFn MAKE_SERVICE_METHOD =
        fn("ring.util.servlet", "make-service-method");

    // TODO: destroy

    public Servlet() {;}

    public void init() throws ServletException {
        ServletConfig config = this.getServletConfig();

        // Handler
        handler = createServiceMethod(config.getInitParameter("ns-name"),
                                      config.getInitParameter("handler-name"));

        // Init
        invoke(config.getInitParameter("init-ns-name"),
               config.getInitParameter("init-name"));
    }

    public static IFn fn(String ns, String fnName) {
        require(ns);
        return (IFn) RT.var(ns, fnName);
    }

    public void service(ServletRequest request, ServletResponse response) {
        handler.invoke(this, request, response);
    }

    private static void require(String namespace) {
        REQUIRE.invoke(SYMBOL.invoke(namespace));
    }

    private IFn createServiceMethod(String namespace, String handler) {
        return (IFn) MAKE_SERVICE_METHOD.invoke(fn(namespace, handler));
    }

    private void invoke(String namespace, String var) {
        fn(namespace, var).invoke();
    }
}
