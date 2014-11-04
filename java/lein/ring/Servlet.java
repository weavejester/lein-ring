package lein.ring;

import clojure.lang.RT;
import clojure.lang.IFn;
import clojure.lang.Var;
import clojure.lang.Symbol;
import clojure.lang.Keyword;
import clojure.lang.AFn;

import lein.ring.Listener;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import javax.servlet.http.HttpServletRequest;

import java.util.Map;

public class Servlet extends GenericServlet {

    public IFn method;

    public Var REQUIRE;
    public Var SYMBOL;

    public Servlet() {;}

    public void init() throws ServletException {
        ServletConfig config = this.getServletConfig();
        initCljRuntime();
        setDestroy(config);
        doInit(config);
        initHandler(config);
    }

    private void initCljRuntime() {
        REQUIRE = RT.var("clojure.core", "require");
        SYMBOL = RT.var("clojure.core", "symbol");
    }

    private void doInit(ServletConfig config) {
        invoke(config.getInitParameter("init-ns-name"),
               config.getInitParameter("init-name"));
    }

    private void setDestroy(ServletConfig config) {
        Listener.setDestructor(
            fn(config.getInitParameter("destroy-ns-name"),
               config.getInitParameter("destroy-name")));
    }

    private void initHandler(ServletConfig config) {
        IFn handler = fn(config.getInitParameter("ns-name"),
                         config.getInitParameter("handler-name"));

        if (config.getInitParameter("path-info").equals("true")) {
            handler = contextMiddleware(handler);
        }

        method = createServiceMethod(handler);
    }

    private IFn fn(String ns, String fnName) {
        if (ns == "") {
            return null;
        }
        require(ns);
        return (IFn) RT.var(ns, fnName);
    }
    private IFn contextMiddleware(final IFn handler) {
        final Keyword SERVLET_REQUEST = keyword("servlet-request");
        final Keyword CONTEXT = keyword("context");
        final Keyword URI = keyword("uri");
        final Keyword PATH_INFO = keyword("path-info");
        return new AFn() {
            public Object invoke(Object r) {
                Map req = (Map) r;
                HttpServletRequest servletRequest =
                    (HttpServletRequest) req.get(SERVLET_REQUEST);
                String context = servletRequest.getContextPath();
                String uri = (String) req.get(URI);
                String pathInfo = uri.substring(context.length());
                if (pathInfo.isEmpty()) {
                    pathInfo = "/";
                }
                return handler.invoke(
                    RT.assoc(
                        RT.assoc(req, CONTEXT, context),
                        PATH_INFO, pathInfo));
            }
        };
    }

    private Keyword keyword(String s) {
        return Keyword.intern(null, s);
    }

    public void service(ServletRequest request, ServletResponse response) {
        method.invoke(this, request, response);
    }

    private void require(String namespace) {
        REQUIRE.invoke(SYMBOL.invoke(namespace));
    }

    private IFn createServiceMethod(IFn handler) {
        IFn makeServiceMethod =
            fn("ring.util.servlet", "make-service-method");
        return (IFn) makeServiceMethod.invoke(handler);
    }

    private void invoke(String namespace, String var) {
        IFn f = fn(namespace, var);
        if (f != null) {
            f.invoke();
        }
    }
}
