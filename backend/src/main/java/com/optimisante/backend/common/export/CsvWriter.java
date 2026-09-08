package com.optimisante.backend.common.export;

import java.util.List;

/**
 * Générateur CSV minimal (RFC 4180) pour les exports du back-office : journal d'audit, jeux de
 * données analytics. Sépare par ';' (Excel FR), échappe les guillemets, préfixe un BOM UTF-8
 * pour que les accents s'affichent correctement à l'ouverture dans Excel.
 */
public final class CsvWriter {

    private static final String SEP = ";";
    private static final String EOL = "\r\n";
    public static final String BOM = "\uFEFF";

    private CsvWriter() {
    }

    public static String toCsv(List<String> headers, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder(BOM);
        sb.append(String.join(SEP, headers.stream().map(CsvWriter::escape).toList())).append(EOL);
        for (List<Object> row : rows) {
            sb.append(String.join(SEP, row.stream().map(CsvWriter::cell).toList())).append(EOL);
        }
        return sb.toString();
    }

    private static String cell(Object value) {
        return escape(value == null ? "" : String.valueOf(value));
    }

    private static String escape(String value) {
        boolean mustQuote = value.contains(SEP) || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return mustQuote ? "\"" + escaped + "\"" : escaped;
    }
}
