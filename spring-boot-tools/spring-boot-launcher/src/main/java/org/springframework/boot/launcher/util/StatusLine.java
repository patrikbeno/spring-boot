package org.springframework.boot.launcher.util;

import org.springframework.boot.launcher.MvnLauncherCfg;

import java.util.ArrayList;
import java.util.LinkedList;

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

    static public void push(String message, Object ... args) {
        status.add(new Message(message, args));
        refresh();
//        try { Thread.sleep(1000); } catch (Exception ignore) {}
    }

    static public void pop() {
        status.removeLast();
        refresh();
    }

    static public void update(String message, Object... args) {
        status.removeLast(); status.add(new Message(message, args)); // this must not fail or the stack breaks
        refresh();
    }

    static public void clear() {
        status.clear();
        refresh();
    }

    static void refresh() {
        if (MvnLauncherCfg.quiet.asBoolean()) { return; }
        if (!MvnLauncherCfg.statusLine.asBoolean()) { return; }
        synchronized (System.out) {
            resetLine();
            for (Message m : new ArrayList<Message>(status)) {
                System.out.printf("> %s ", m);
            }
        }
    }

    static public void resetLine() {
        if (MvnLauncherCfg.statusLine.asBoolean()) {
            System.err.print("\033[2K\r");
            System.err.flush();
        }
    }
}
