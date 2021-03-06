package net.starschema.html_to_csv;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.jsoup.Jsoup;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Core {

    private static final class Column {
        public final String name;
        public final String selector;
        public final String extract;
        public final Pattern regexp;

        public Column(String name, String selector, String extract, String regexpPattern) {
            this.name = name;
            this.selector = selector;
            this.extract = extract;
            this.regexp = Pattern.compile( regexpPattern, Pattern.DOTALL & Pattern.MULTILINE);
        }

    }

    private static final class Param {
        private final Map data;

        public Param(Map data) { this.data = data; }

        public <T> T get(final String key) { return (T) data.get(key);}

        public <T> T get(final String key, T defaultValue) {
            Object o = data.get(key);
            return o == null ? defaultValue : (T)o;
        }
    }

    public static void main(String[] args) throws IOException {

        String result;
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: <java-cmd-line> <YAML config>");
        } else {
            result = args[0];
        }
        InputStream input =  new FileInputStream(new File(result));
        Yaml yaml = new Yaml();
        final Param config = new Param((Map) yaml.load(input));


        final List<Column> columnData = config
                .<List<Map>>get("columns")
                .stream()
                .map(node -> {
                    final Param n = new Param(node);
                    return new Column(
                            n.get("name", ""),
                            n.get("selector", ""),
                            n.get("extract", "text()"),
                            n.get("regexp", "(.*)")
                    );
                })
                .collect(Collectors.toList());

        final CSVPrinter printer = CSVFormat.DEFAULT
                .withHeader(
                        columnData.stream()
                                .map(col -> col.name)
                                .collect(Collectors.toList())
                                .toArray(new String[0]))
                .print(System.out);

        Jsoup.connect(config.get("url", "http://127.0.0.1/"))
                .get()
                .select(config.get("root", "body"))
                .stream()
                .map(row ->
                        columnData.stream()
                        .map(col ->
                                row.select(col.selector).stream()
                                .map(el ->
                                        (col.extract.charAt(0) == '@')
                                        ? el.attr(col.extract.substring(1))
                                        : (el.text()))
                                .map(res -> {
                                    Matcher m = col.regexp.matcher(res);
                                    return m.find() ? m.group(1) : null;
                                })
                                .reduce("", (memo, e) -> memo + e))
                        .collect(Collectors.toList()))

                .forEach(row -> {
                    try {
                        printer.printRecord(row);
                    } catch (IOException e) {
                        e.printStackTrace(System.err);
                    }
                });

    }

}
