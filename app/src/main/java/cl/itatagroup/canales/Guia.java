package cl.itatagroup.canales;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.Normalizer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guía de programación (XMLTV). Java puro, sin red ni Android, para probarlo en el PC. Se lee línea
 * por línea (la guía de Chile pesa unos 3 MB) y solo se guardan los programas de los canales que
 * calzan con los nuestros, entre 6 horas atrás y 36 horas adelante.
 */
public final class Guia {
  private Guia() {}

  /** Guía pública de Chile en formato XMLTV (comprimida). */
  public static final String CHILE = "https://epgshare01.online/epgshare01/epg_ripper_CL1.xml.gz";

  public static final class Programa {
    public final long inicio, fin;
    public final String titulo;

    public Programa(long inicio, long fin, String titulo) {
      this.inicio = inicio;
      this.fin = fin;
      this.titulo = titulo;
    }
  }

  private static final Pattern CANAL = Pattern.compile("<channel id=\"([^\"]+)\"");
  private static final Pattern NOMBRE = Pattern.compile("<display-name[^>]*>([^<]+)</display-name>");
  private static final Pattern PROGRAMA = Pattern.compile(
      "<programme start=\"([0-9]{14} ?[+-][0-9]{4})\" stop=\"([0-9]{14} ?[+-][0-9]{4})\" channel=\"([^\"]+)\"");
  private static final Pattern TITULO = Pattern.compile("<title[^>]*>([^<]+)</title>");

  /**
   * Programas por id de nuestro canal. {@code ahora} en milisegundos (se pasa para poder probarlo).
   */
  public static Map<String, List<Programa>> leer(BufferedReader in, List<Canal> canales, long ahora) throws IOException {
    SimpleDateFormat fecha = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.ROOT);
    Map<String, String> guiaANuestro = new HashMap<>();
    Map<String, List<Programa>> porCanal = new HashMap<>();
    String linea;
    String canalGuia = null;
    String[] pendiente = null; // {inicio, fin, canal} de un <programme> que espera su <title>
    long desde = ahora - 6 * 3600_000L, hasta = ahora + 36 * 3600_000L;
    while ((linea = in.readLine()) != null) {
      Matcher m = CANAL.matcher(linea);
      if (m.find()) {
        canalGuia = m.group(1);
        asociar(canalGuia, canalGuia, canales, guiaANuestro);
      }
      m = NOMBRE.matcher(linea);
      if (m.find() && canalGuia != null) asociar(canalGuia, desescapar(m.group(1)), canales, guiaANuestro);
      m = PROGRAMA.matcher(linea);
      if (m.find()) pendiente = guiaANuestro.containsKey(m.group(3)) ? new String[] {m.group(1), m.group(2), m.group(3)} : null;
      m = TITULO.matcher(linea);
      if (m.find() && pendiente != null) {
        try {
          long ini = fecha.parse(pendiente[0].replace(" ", "").replaceFirst("([+-])", " $1")).getTime();
          long fin = fecha.parse(pendiente[1].replace(" ", "").replaceFirst("([+-])", " $1")).getTime();
          if (fin > desde && ini < hasta) {
            String id = guiaANuestro.get(pendiente[2]);
            List<Programa> lista = porCanal.get(id);
            if (lista == null) porCanal.put(id, lista = new ArrayList<>());
            lista.add(new Programa(ini, fin, desescapar(m.group(1)).trim()));
          }
        } catch (ParseException ignorado) {
          // fecha mal formada: se salta ese programa
        }
        pendiente = null;
      }
    }
    return porCanal;
  }

  /** El programa que está al aire y el siguiente, o null. */
  public static Programa[] ahoraYDespues(List<Programa> programas, long ahora) {
    if (programas == null) return null;
    Programa actual = null, siguiente = null;
    for (Programa p : programas) {
      if (p.inicio <= ahora && ahora < p.fin) actual = p;
      else if (p.inicio >= ahora && (siguiente == null || p.inicio < siguiente.inicio)) siguiente = p;
    }
    return actual == null && siguiente == null ? null : new Programa[] {actual, siguiente};
  }

  private static void asociar(String idGuia, String nombre, List<Canal> canales, Map<String, String> mapa) {
    if (mapa.containsKey(idGuia)) return;
    String n = normalizar(nombre);
    if (n.length() < 4) return;
    for (Canal c : canales) {
      if (parecidos(n, normalizar(c.nombre))) {
        mapa.put(idGuia, c.id);
        return;
      }
    }
  }

  /** «Canal.La.Red.(Chile).cl» y «La Red» → «lared». */
  static String normalizar(String s) {
    String t = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    t = t.replaceAll("\\(chile\\)|\\.cl$|^canal[ .]|\\bhd\\b|\\bsd\\b", " ");
    return t.replaceAll("[^a-z0-9]", "");
  }

  /** Iguales, uno dentro del otro, o un comienzo común largo («tveinternacional…»). */
  static boolean parecidos(String a, String b) {
    if (a.length() < 4 || b.length() < 4) return false;
    if (a.equals(b)) return true;
    // Contener al otro solo vale con nombres de 6 letras o más: «claro» no es «clarotv».
    if (Math.min(a.length(), b.length()) >= 6 && (a.contains(b) || b.contains(a))) return true;
    int i = 0;
    while (i < a.length() && i < b.length() && a.charAt(i) == b.charAt(i)) i++;
    return i >= 12;
  }

  private static String desescapar(String s) {
    return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'");
  }
}
