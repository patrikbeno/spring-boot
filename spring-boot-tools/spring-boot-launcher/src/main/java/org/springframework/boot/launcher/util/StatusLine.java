package org.springframework.boot.launcher.util;

import org.springframework.boot.launcher.MvnLauncherCfg;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.LinkedList;

import static org.springframework.boot.launcher.util.Log.out;


/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class StatusLine {

    static private class Message {

        String msg;
        Object[] args;

        private Message(String msg, Object[] args) {
            this.msg = msg;
            this.args = args;
        }

        @Override
        public String toString() {
            return String.format(msg, args);
        }
    }

    static private LinkedList<Message> status = new LinkedList<Message>();

    static synchronized public void push(String message, Object ... args) {
        status.add(new Message(message, args));
        refresh();
    }

    static synchronized public void pop() {
        resetLine();
        status.removeLast();
        refresh();
    }

    static synchronized public void update(String message, Object... args) {
        resetLine();
        status.removeLast(); status.add(new Message(message, args)); // this must not fail or the stack breaks
        refresh();
    }

    static synchronized public void clear() {
        resetLine();
        status.clear();
        refresh();
    }

    static void refresh() {
        if (MvnLauncherCfg.quiet.asBoolean()) { return; }
        synchronized (out()) {
            resetLine();
            print(new Formatter(out()));
            out().flush();
        }
    }

    static public void resetLine() {
        if (MvnLauncherCfg.quiet.asBoolean()) { return; }

        StringBuilder sb = new StringBuilder();
        Formatter f = new Formatter(sb);
        print(f);
        for (int i=0; i<sb.length(); i++) {
            sb.setCharAt(i, ' ');
        }
        out().append('\r').append(sb).append('\r');
        out().flush();
    }

    static private Formatter print(Formatter f) {
        f.format("\033[1;32m");
        for (Message m : new ArrayList<Message>(status)) {
            f.format("> %s ", m);
        }
        f.format("\033[0m");
        return f;
    }
    
}
