package net.starschema.html_to_csv;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.jsoup.Jsoup;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by t on 02/06/15.
 */
public class Main {


    private static class Column {
        public final String name;
        public final String selector;
        public final String extract;
        public final Pattern regex;

        public Column(String name, String selector, String extract, Pattern regex) {
            this.name = name;
            this.selector = selector;
            this.extract = extract;
            this.regex = regex;
        }

        @Override
        public String toString() {
            return "Column{" +
                    "name='" + name + '\'' +
                    ", selector='" + selector + '\'' +
                    ", extract='" + extract + '\'' +
                    ", regex=" + regex +
                    '}';
        }
    }


    public static void main(String[] args) throws IOException {
        if (args.length != 1) System.exit(-1);

        final Map config = (Map) new Yaml().load(new FileInputStream(args[0]));
        final String url = getProperty(config, "url", "http://localhost:8080/about_team.html");
        final String root = getProperty(config, "root", "body");
        final List<Column> columns = columnsFromConfig((List<Map>) config.get("columns"));


        CSVPrinter csvPrinter = createCSVPrinter(columns, System.out);

        Jsoup.connect(url)
                .get()
                .select(root)
                .stream()
                .map(row -> columns
                                .stream()
                                .map(col -> row.select(col.selector)
                                                .stream()
                                                .map(el -> col.extract.equals("text()")
                                                        ? el.text()
                                                        : el.attr(col.extract))
                                                .map(el -> {
                                                    Matcher m = col.regex.matcher(el);
                                                    return m.find() ? m.group(1) : null;
                                                })
                                                .reduce("", (memo, el) -> memo + el)
                                )
                                .collect(Collectors.toList())
                )

                .forEach(row -> {
                    try {
                        csvPrinter.printRecord(row);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

        ;

    }

    private static CSVPrinter createCSVPrinter(final List<Column> columns, Appendable out) throws IOException {
        return CSVFormat.DEFAULT
                    .withHeader(columns
                            .stream()
                            .map(c -> c.name)
                            .collect(Collectors.toList())
                            .toArray(new String[0]))
                    .print(out);
    }

    private static List<Column> columnsFromConfig(List<Map> columnsConfig) {
        return columnsConfig
                .stream()
                .map(colData -> new Column(
                        getProperty(colData, "name", "<unnamed>"),
                        getProperty(colData, "selector", "*"),
                        getProperty(colData, "extract", "text()"),
                        Pattern.compile(getProperty(colData, "regexp", "(.*)$"),
                                Pattern.MULTILINE & Pattern.DOTALL)
                ))
                .collect(Collectors.toList());
    }

    private static String getProperty(Map config, String paramName, String defaultValue) {
        return config.containsKey(paramName) ? (String) config.get(paramName) : defaultValue;
    }
}
