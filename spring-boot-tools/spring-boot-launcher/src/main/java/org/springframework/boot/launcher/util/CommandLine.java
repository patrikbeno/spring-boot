package org.springframework.boot.launcher.util;

import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class CommandLine {

    private Queue<String> args;
    private Properties props;
    private boolean stopped;

    static public CommandLine parse(Queue<String> args) {
        CommandLine cmdline = new CommandLine(args, new Properties());
        cmdline.parse();
        return cmdline;
    }

    private CommandLine(Queue<String> args, Properties props) {
        this.args = args;
        this.props = props;
    }

    public Properties properties() {
        return props;
    }

    public Queue<String> remainder() {
        return args;
    }

    public boolean isStopped() {
        return stopped;
    }

    private void parse() {

        Pattern pattern = Pattern.compile("(?:-D|--)([^=]+)(?:=?(.*))");

        while (!args.isEmpty()) {
            String s = args.peek();

            if (s.equals("--")) {
                args.remove();
                stopped = true;
                break;
            }

            Matcher m = pattern.matcher(s);
            if (!m.matches()) {
                break;
            }

            args.remove();
            String key = m.group(1);
            String value = m.group(2);
            props.setProperty(key, value != null ? value : "");
        }
    }

}
