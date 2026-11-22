package mc.util;

import java.util.*;

/** Minimal JSON reader/writer (no external dependencies). Objects map to LinkedHashMap, arrays to ArrayList,
 * numbers to Double, plus String/Boolean/null. */
public final class Json {
    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    public static Object parse(String text) {
        Json j = new Json(text);
        j.ws();
        Object v = j.value();
        j.ws();
        if (j.i != j.s.length()) throw j.err("trailing characters");
        return v;
    }

    private RuntimeException err(String m) {
        int line = 1;
        for (int k = 0; k < Math.min(i, s.length()); k++) if (s.charAt(k) == '\n') line++;
        return new IllegalArgumentException("JSON error at line " + line + ": " + m);
    }

    private void ws() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') i++;
            else break;
        }
    }

    private Object value() {
        if (i >= s.length()) throw err("unexpected end");
        char c = s.charAt(i);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': expect("true"); return Boolean.TRUE;
            case 'f': expect("false"); return Boolean.FALSE;
            case 'n': expect("null"); return null;
            default: return number();
        }
    }

    private void expect(String w) {
        if (!s.startsWith(w, i)) throw err("expected " + w);
        i += w.length();
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        ws();
        if (s.charAt(i) == '}') { i++; return m; }
        while (true) {
            ws();
            String k = string();
            ws();
            if (s.charAt(i) != ':') throw err("expected :");
            i++;
            ws();
            m.put(k, value());
            ws();
            char c = s.charAt(i++);
            if (c == '}') return m;
            if (c != ',') throw err("expected , or }");
        }
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++;
        ws();
        if (s.charAt(i) == ']') { i++; return l; }
        while (true) {
            ws();
            l.add(value());
            ws();
            char c = s.charAt(i++);
            if (c == ']') return l;
            if (c != ',') throw err("expected , or ]");
        }
    }

    private String string() {
        if (s.charAt(i) != '"') throw err("expected string");
        i++;
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'r': b.append('\r'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                    default: b.append(e);
                }
            } else b.append(c);
        }
    }

    private Double number() {
        int st = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (st == i) throw err("unexpected character '" + s.charAt(i) + "'");
        return Double.parseDouble(s.substring(st, i));
    }

    // ---------------- writer ----------------
    public static String write(Object o) {
        StringBuilder b = new StringBuilder();
        write(b, o, 0);
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(StringBuilder b, Object o, int ind) {
        if (o == null) b.append("null");
        else if (o instanceof String) quote(b, (String) o);
        else if (o instanceof Number) {
            double d = ((Number) o).doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < 1e15) b.append((long) d);
            else b.append(d);
        } else if (o instanceof Boolean) b.append(o);
        else if (o instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) o;
            b.append("{");
            boolean first = true;
            for (Map.Entry<String, Object> e : m.entrySet()) {
                if (!first) b.append(",");
                first = false;
                b.append("\n");
                indent(b, ind + 1);
                quote(b, e.getKey());
                b.append(": ");
                write(b, e.getValue(), ind + 1);
            }
            if (!m.isEmpty()) { b.append("\n"); indent(b, ind); }
            b.append("}");
        } else if (o instanceof Collection) {
            b.append("[");
            boolean first = true;
            for (Object e : (Collection<Object>) o) {
                if (!first) b.append(", ");
                first = false;
                write(b, e, ind + 1);
            }
            b.append("]");
        } else quote(b, o.toString());
    }

    private static void indent(StringBuilder b, int n) { for (int k = 0; k < n; k++) b.append("  "); }

    private static void quote(StringBuilder b, String s) {
        b.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 32) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
    }

    // ---------------- typed accessors ----------------
    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) { return o == null ? new LinkedHashMap<>() : (Map<String, Object>) o; }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o) { return o == null ? new ArrayList<>() : (List<Object>) o; }

    public static double num(Map<String, Object> m, String k, double def) {
        Object o = m.get(k);
        return o instanceof Number ? ((Number) o).doubleValue() : def;
    }

    public static int integer(Map<String, Object> m, String k, int def) { return (int) num(m, k, def); }

    public static String str(Map<String, Object> m, String k, String def) {
        Object o = m.get(k);
        return o instanceof String ? (String) o : def;
    }

    public static boolean bool(Map<String, Object> m, String k, boolean def) {
        Object o = m.get(k);
        return o instanceof Boolean ? (Boolean) o : def;
    }
}
