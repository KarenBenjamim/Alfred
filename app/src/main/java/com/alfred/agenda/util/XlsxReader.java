package com.alfred.agenda.util;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

/**
 * Leitor mínimo de planilhas .xlsx (Office Open XML), só com o Java padrão, sem bibliotecas.
 * Lê o texto/valor de cada célula de todas as abas; ignora formatação, fórmulas e imagens
 * (de fórmulas, usa o último valor calculado que o Excel salvou no arquivo).
 *
 * Proteções: o arquivo é um zip, então há limite de tamanho por parte e no total
 * (contra "zip bombs"), e o XML não carrega entidades externas.
 */
public final class XlsxReader {

    private static final long MAX_ENTRY_BYTES = 16L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 48L * 1024 * 1024;

    /** Arquivo que não é um .xlsx válido (ou está corrompido). */
    public static final class FormatException extends Exception {
        public FormatException(String message) {
            super(message);
        }
    }

    /** Uma aba: linhas numeradas como no Excel (a 1ª é 1); colunas a partir de 0 (A = 0). */
    public static final class Sheet {
        public final String name;
        private final TreeMap<Integer, Map<Integer, String>> rows = new TreeMap<>();

        Sheet(String name) {
            this.name = name;
        }

        /** Texto da célula, sem espaços nas pontas ("" se vazia). */
        public String cell(int row, int col) {
            Map<Integer, String> r = rows.get(row);
            if (r == null) return "";
            String v = r.get(col);
            return v == null ? "" : v.trim();
        }

        public int lastRow() {
            return rows.isEmpty() ? 0 : rows.lastKey();
        }

        /** true se todas as células da linha estão vazias. */
        public boolean isBlank(int row) {
            Map<Integer, String> r = rows.get(row);
            if (r == null) return true;
            for (String v : r.values()) if (v != null && !v.trim().isEmpty()) return false;
            return true;
        }

        void put(int row, int col, String value) {
            rows.computeIfAbsent(row, k -> new HashMap<>()).put(col, value);
        }
    }

    /** As abas na ordem do arquivo. */
    public static final class Workbook {
        public final Map<String, Sheet> sheets;

        Workbook(Map<String, Sheet> sheets) {
            this.sheets = Collections.unmodifiableMap(sheets);
        }

        public List<String> sheetNames() {
            return new ArrayList<>(sheets.keySet());
        }
    }

    private XlsxReader() {
    }

    public static Workbook read(InputStream in) throws IOException, FormatException {
        Map<String, byte[]> parts = unzip(in);
        byte[] workbookXml = parts.get("xl/workbook.xml");
        if (workbookXml == null) throw new FormatException("sem xl/workbook.xml");

        // workbook.xml: nome de cada aba -> id da relação; workbook.xml.rels: id -> arquivo
        List<String[]> declared = new ArrayList<>(); // {nome, rId}
        parse(workbookXml, new DefaultHandler() {
            @Override
            public void startElement(String uri, String localName, String qName, Attributes a) {
                if ("sheet".equals(local(qName))) declared.add(new String[]{a.getValue("name"), relId(a)});
            }
        });
        Map<String, String> targets = new HashMap<>();
        byte[] rels = parts.get("xl/_rels/workbook.xml.rels");
        if (rels != null) {
            parse(rels, new DefaultHandler() {
                @Override
                public void startElement(String uri, String localName, String qName, Attributes a) {
                    if ("Relationship".equals(local(qName))) targets.put(a.getValue("Id"), a.getValue("Target"));
                }
            });
        }

        List<String> shared = sharedStrings(parts.get("xl/sharedStrings.xml"));

        Map<String, Sheet> sheets = new LinkedHashMap<>();
        for (int i = 0; i < declared.size(); i++) {
            String name = declared.get(i)[0];
            if (name == null) continue;
            String target = targets.get(declared.get(i)[1]);
            String path = target == null ? "xl/worksheets/sheet" + (i + 1) + ".xml" : resolve(target);
            byte[] xml = parts.get(path);
            Sheet sheet = new Sheet(name);
            if (xml != null) readSheet(xml, shared, sheet);
            sheets.put(name, sheet);
        }
        return new Workbook(sheets);
    }

    // ---------------- Zip ----------------

    private static Map<String, byte[]> unzip(InputStream in) throws IOException, FormatException {
        Map<String, byte[]> parts = new HashMap<>();
        long total = 0;
        boolean any = false;
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            byte[] buf = new byte[16 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                any = true;
                String name = entry.getName();
                // Só as partes que interessam (ignora imagens, estilos, etc.)
                boolean wanted = name.equals("xl/workbook.xml") || name.equals("xl/_rels/workbook.xml.rels")
                        || name.equals("xl/sharedStrings.xml")
                        || (name.startsWith("xl/worksheets/") && name.endsWith(".xml"));
                if (!wanted || entry.isDirectory()) continue;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int n;
                long size = 0;
                while ((n = zip.read(buf)) > 0) {
                    size += n;
                    total += n;
                    if (size > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) {
                        throw new FormatException("planilha grande demais");
                    }
                    out.write(buf, 0, n);
                }
                parts.put(name, out.toByteArray());
            }
        } catch (ZipException | IllegalArgumentException e) {
            throw new FormatException("não é um arquivo zip/xlsx");
        }
        if (!any) throw new FormatException("não é um arquivo zip/xlsx");
        return parts;
    }

    /** "worksheets/sheet1.xml" ou "/xl/worksheets/sheet1.xml" -> "xl/worksheets/sheet1.xml". */
    private static String resolve(String target) {
        if (target.startsWith("/")) return target.substring(1);
        return "xl/" + target;
    }

    // ---------------- Textos compartilhados ----------------

    private static List<String> sharedStrings(byte[] xml) throws FormatException {
        List<String> list = new ArrayList<>();
        if (xml == null) return list;
        parse(xml, new DefaultHandler() {
            private StringBuilder current;
            private boolean inText;
            private int phonetic; // <rPh>: guia de pronúncia, não faz parte do texto

            @Override
            public void startElement(String uri, String localName, String qName, Attributes a) {
                String n = local(qName);
                if ("si".equals(n)) current = new StringBuilder();
                else if ("rPh".equals(n)) phonetic++;
                else if ("t".equals(n) && current != null && phonetic == 0) inText = true;
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                String n = local(qName);
                if ("si".equals(n)) {
                    list.add(current == null ? "" : current.toString());
                    current = null;
                } else if ("rPh".equals(n)) {
                    phonetic--;
                } else if ("t".equals(n)) {
                    inText = false;
                }
            }

            @Override
            public void characters(char[] ch, int start, int length) {
                if (inText && current != null) current.append(ch, start, length);
            }
        });
        return list;
    }

    // ---------------- Abas ----------------

    private static void readSheet(byte[] xml, List<String> shared, Sheet sheet) throws FormatException {
        parse(xml, new DefaultHandler() {
            private int nextRow = 1;
            private int row;
            private int nextCol;
            private int col = -1;
            private String type;
            private StringBuilder value;
            private boolean capture;

            @Override
            public void startElement(String uri, String localName, String qName, Attributes a) {
                String n = local(qName);
                if ("row".equals(n)) {
                    String r = a.getValue("r");
                    row = r != null ? parseIntSafe(r, nextRow) : nextRow;
                    nextRow = row + 1;
                    nextCol = 0;
                } else if ("c".equals(n)) {
                    String ref = a.getValue("r");
                    col = ref != null ? columnIndex(ref) : nextCol;
                    nextCol = col + 1;
                    type = a.getValue("t");
                    value = new StringBuilder();
                } else if (("v".equals(n) || "t".equals(n)) && value != null) {
                    capture = true;
                }
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                String n = local(qName);
                if ("v".equals(n) || "t".equals(n)) {
                    capture = false;
                } else if ("c".equals(n) && value != null) {
                    String text = cellText(type, value.toString(), shared);
                    if (!text.isEmpty()) sheet.put(row, col, text);
                    value = null;
                }
            }

            @Override
            public void characters(char[] ch, int start, int length) {
                if (capture && value != null) value.append(ch, start, length);
            }
        });
    }

    private static String cellText(String type, String raw, List<String> shared) {
        if ("s".equals(type)) {
            int idx = parseIntSafe(raw.trim(), -1);
            return idx >= 0 && idx < shared.size() ? shared.get(idx) : "";
        }
        if ("b".equals(type)) return "1".equals(raw.trim()) ? "VERDADEIRO" : "FALSO";
        if ("e".equals(type)) return "";
        if (type == null || "n".equals(type)) return formatNumber(raw.trim());
        return raw; // inlineStr, str (resultado de fórmula de texto)
    }

    /** "35" -> "35"; "2.4500000000000002" -> "2.45"; "0.3125" fica como está. */
    static String formatNumber(String raw) {
        if (raw.isEmpty()) return "";
        try {
            double d = Double.parseDouble(raw);
            if (d == Math.rint(d) && Math.abs(d) < 1e15) return Long.toString((long) d);
            return new BigDecimal(raw).round(new java.math.MathContext(12)).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    /** "AB12" -> 27 (A = 0). */
    static int columnIndex(String ref) {
        int col = 0;
        for (int i = 0; i < ref.length(); i++) {
            char c = Character.toUpperCase(ref.charAt(i));
            if (c < 'A' || c > 'Z') break;
            col = col * 26 + (c - 'A' + 1);
        }
        return col - 1;
    }

    private static int parseIntSafe(String s, int fallback) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ---------------- XML ----------------

    /** "x:row" -> "row" (alguns programas gravam com prefixo de namespace). */
    private static String local(String qName) {
        int i = qName.indexOf(':');
        return i >= 0 ? qName.substring(i + 1) : qName;
    }

    /** Atributo r:id (o prefixo varia conforme o programa que salvou). */
    private static String relId(Attributes a) {
        for (int i = 0; i < a.getLength(); i++) {
            String q = a.getQName(i);
            if (q.endsWith(":id") || q.equals("id")) return a.getValue(i);
        }
        return null;
    }

    private static void parse(byte[] xml, DefaultHandler handler) throws FormatException {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(false);
            trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
            trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            org.xml.sax.XMLReader reader = factory.newSAXParser().getXMLReader();
            reader.setContentHandler(handler);
            // Nunca busca nada fora do arquivo (DTD/entidades externas viram vazio)
            reader.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            reader.parse(new InputSource(new ByteArrayInputStream(xml)));
        } catch (SAXException | ParserConfigurationException | IOException e) {
            throw new FormatException("XML inválido na planilha");
        }
    }

    private static void trySetFeature(SAXParserFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
            // Nem todo parser conhece todas as opções; o EntityResolver acima cobre o resto
        }
    }
}
