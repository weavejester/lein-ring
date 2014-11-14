package lein.ring;

import clojure.lang.IFn;

import javax.servlet.ServletContextListener;
import javax.servlet.ServletContextEvent;

public class Listener implements ServletContextListener {

    public static IFn destructor;

    public Listener() {;}

    public static void setDestructor(IFn d) {
        destructor = d;
    }

    public void contextInitialized(ServletContextEvent sce) {;}

    public void contextDestroyed(ServletContextEvent sce) {
        if (destructor != null) {
            destructor.invoke();
        }
    }
}
