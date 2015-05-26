package org.springframework.boot.launcher.util;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * @author <a href="mailto:patrikbeno@gmail.com">Patrik Beno</a>
 */
public class CommandLineTest {

    @Test
    public void single() {
        List<String> s = Arrays.asList("--key=value");
        CommandLine cmdline = CommandLine.parse(new LinkedList<String>(s));
        Assert.assertEquals(cmdline.properties().getProperty("key"), "value");
        Assert.assertTrue(!cmdline.isStopped());
        Assert.assertTrue(cmdline.remainder().isEmpty());
    }

    @Test
    public void valueEmpty() {
        List<String> s = Arrays.asList("--option=");
        CommandLine cmdline = CommandLine.parse(new LinkedList<String>(s));
        Assert.assertEquals(cmdline.properties().getProperty("option"), "");
    }

    @Test
    public void valueNone() {
        List<String> s = Arrays.asList("--option");
        CommandLine cmdline = CommandLine.parse(new LinkedList<String>(s));
        Assert.assertEquals(cmdline.properties().getProperty("option"), "");
    }

    @Test
    public void multiple() {
        List<String> s = Arrays.asList("--key1=value1", "--key2=value2");
        CommandLine cmdline = CommandLine.parse(new LinkedList<String>(s));
        Assert.assertEquals(cmdline.properties().getProperty("key1"), "value1");
        Assert.assertEquals(cmdline.properties().getProperty("key2"), "value2");
    }

    @Test
    public void stop() {
        List<String> s = Arrays.asList("--key1=value1", "--key2=value2", "--", "--key3=value3");
        CommandLine cmdline = CommandLine.parse(new LinkedList<String>(s));
        Assert.assertTrue(cmdline.isStopped());
        Assert.assertEquals(cmdline.properties().getProperty("key1"), "value1");
        Assert.assertEquals(cmdline.properties().getProperty("key2"), "value2");
        Assert.assertEquals(cmdline.remainder().size(), 1);
        Assert.assertEquals(cmdline.remainder().peek(), "--key3=value3");
    }

    @Test
    public void noMoreOptions() {
        List<String> s = Arrays.asList("--key1=value1", "--key2=value2", "NoOption");
        CommandLine cmdline = CommandLine.parse(new LinkedList<String>(s));
        Assert.assertEquals(cmdline.properties().getProperty("key1"), "value1");
        Assert.assertEquals(cmdline.properties().getProperty("key2"), "value2");
        Assert.assertEquals(cmdline.remainder().size(), 1);
        Assert.assertEquals(cmdline.remainder().peek(), "NoOption");
    }

}
