package cl.itatagroup.canales;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee listas M3U y decide qué canales entran. No toca la red ni Android: es Java puro, para poder
 * probarlo en el PC con {@code tools/probar-catalogo.mjs}.
 *
 * <p>Las reglas, en orden (ante la duda, el canal no entra):
 * <ol>
 *   <li>Fuera los canales de pago ({@link #PAGO}): las listas públicas mezclan señales de cable
 *       subidas sin licencia.</li>
 *   <li>Fuera los enlaces de origen dudoso ({@link #origenDudoso}): una IP suelta o la CDN de un
 *       cableoperador son retransmisiones, no la señal del canal.</li>
 *   <li>De las listas internacionales solo entran las señales de {@link #INTERNACIONALES}, que sus
 *       dueños transmiten gratis y en abierto.</li>
 * </ol>
 */
public final class Catalogo {
  private Catalogo() {}

  public static final String NACIONALES = "Nacionales";
  public static final String REGIONALES = "Regionales";
  public static final String NOTICIAS = "Noticias";
  public static final String MUSICA = "Música y radios";
  public static final String INTERNACIONAL = "Internacional";
  public static final String MIS_LISTAS = "Mis listas";
  public static final String[] CATEGORIAS = {NACIONALES, REGIONALES, NOTICIAS, MUSICA, INTERNACIONAL, MIS_LISTAS};

  /** Señales de cable o de pago. Se comparan contra el nombre del canal. */
  static final Pattern PAGO = Pattern.compile(
      "\\b(Disney|Cartoon|Boomerang|Tooncast|Nick(elodeon)?|Discovery|Nat ?Geo|History|A&E|Lifetime|Animal Planet"
          + "|ESPN|Fox|DSports|DirecTV|TNT|Win Sports|TyC|Golden|Space|AXN|Sony|Warner|HBO|HBO Max|Cinemax|Paramount"
          + "|Universal (TV|Channel|Premiere|Cinema)|Studio Universal|Star Channel|Cinecanal|TCM|MTV|VH1|Comedy Central"
          + "|E! Latin|TLC|Telemundo Internacional|CNN|Food Network|HGTV|Investigation Discovery|Film ?&? ?Arts"
          + "|Canal del F[uú]tbol|CDF)\\b",
      Pattern.CASE_INSENSITIVE);

  /** CDN de cableoperadores: si un canal abierto sale de ahí, es la copia del cable, no la del canal. */
  static final Pattern CDN_DE_CABLE = Pattern.compile(
      "(vtrplay|clarovtrcdn|clarovideo|movistarplay|directvgo|entel\\.tv|gtdmanquehue|mundopacifico)",
      Pattern.CASE_INSENSITIVE);

  static final Pattern IP = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

  /**
   * Prefijos de tvg-id de las señales internacionales que entran. Son canales que sus dueños
   * publican gratis para todo el mundo (no la copia de un cable).
   */
  static final String[] INTERNACIONALES = {
    "DW.de@Espanol", "DW.de@English", "France24.fr@Spanish", "France24.fr@English", "TVEInternacionalAmerica.es@HD",
    "EuronewsSpanish.fr", "EuronewsEnglish.fr@HD", "NHKWorldJapan.jp@HD", "AlJazeera.qa@English", "ArirangTV.kr@HD",
    "ABCNewsLive.us@SD", "CBSNews247.us", "BloombergTV.us@US", "CNA.sg@SD", "TRTWorld.tr@HD",
  };

  /** Canales chilenos de cobertura nacional (el resto de Chile va a Regionales, Noticias o Música). */
  static final Pattern NACIONAL = Pattern.compile(
      "^(13|T13|Canal 13|TVN|24 Horas|Mega|Chilevisi[oó]n|La Red|TV\\+|UCV|Telecanal|TV Senado|NTV|13[A-Z])",
      Pattern.CASE_INSENSITIVE);

  private static final Pattern ATRIBUTO = Pattern.compile("([a-z-]+)=\"([^\"]*)\"");
  private static final Pattern CALIDAD = Pattern.compile("\\((\\d{3,4}[pi])\\)");

  /**
   * Canales de una lista que agregó el usuario: tal cual, sin ningún filtro ni prueba. Lo que
   * contienen es responsabilidad de quien la agrega; la app solo los muestra aparte.
   */
  public static List<Canal> deListaPropia(List<Entrada> entradas, String nombreLista) {
    List<Canal> canales = new ArrayList<>();
    for (Entrada e : entradas) {
      String nombre = limpiar(e.nombre);
      if (nombre.isEmpty()) nombre = e.url;
      Matcher q = CALIDAD.matcher(e.nombre);
      canales.add(new Canal("lista:" + nombreLista + ":" + e.url, nombre, MIS_LISTAS, e.url, q.find() ? q.group(1) : "", false, nombreLista));
    }
    return canales;
  }

  /** Una entrada cruda de la lista, antes de filtrar. */
  public static final class Entrada {
    public final String tvgId, nombre, grupo, url;

    Entrada(String tvgId, String nombre, String grupo, String url) {
      this.tvgId = tvgId;
      this.nombre = nombre;
      this.grupo = grupo;
      this.url = url;
    }
  }

  /** Lee una lista M3U línea por línea (las de iptv-org pesan cerca de 1 MB). */
  public static List<Entrada> leer(BufferedReader in) throws IOException {
    List<Entrada> lista = new ArrayList<>();
    String linea;
    String extinf = null;
    while ((linea = in.readLine()) != null) {
      linea = linea.trim();
      if (linea.startsWith("#EXTINF")) {
        extinf = linea;
      } else if (extinf != null && !linea.isEmpty() && !linea.startsWith("#")) {
        String tvgId = "", grupo = "";
        // Solo los atributos de la cabecera, antes de la coma del nombre.
        int coma = indiceComaNombre(extinf);
        Matcher m = ATRIBUTO.matcher(extinf.substring(0, coma));
        while (m.find()) {
          if (m.group(1).equals("tvg-id")) tvgId = m.group(2);
          else if (m.group(1).equals("group-title")) grupo = m.group(2);
        }
        lista.add(new Entrada(tvgId, extinf.substring(coma + 1).trim(), grupo, linea));
        extinf = null;
      }
    }
    return lista;
  }

  /** La coma que separa los atributos del nombre: la primera fuera de comillas. */
  private static int indiceComaNombre(String extinf) {
    boolean comillas = false;
    for (int i = 0; i < extinf.length(); i++) {
      char ch = extinf.charAt(i);
      if (ch == '"') comillas = !comillas;
      else if (ch == ',' && !comillas) return i;
    }
    return extinf.length() - 1;
  }

  public static boolean esDePago(String nombre) {
    return PAGO.matcher(nombre).find();
  }

  /** Enlace que no viene del canal: sin https/http, IP suelta o CDN de un cableoperador. */
  public static boolean origenDudoso(String url) {
    String u = url.toLowerCase(Locale.ROOT);
    if (!u.startsWith("https://") && !u.startsWith("http://")) return true;
    String resto = u.substring(u.indexOf("://") + 3);
    int fin = resto.length();
    for (char c : new char[] {'/', ':', '?'}) {
      int i = resto.indexOf(c);
      if (i >= 0 && i < fin) fin = i;
    }
    String host = resto.substring(0, fin);
    return IP.matcher(host).matches() || CDN_DE_CABLE.matcher(u).find() || u.contains("proveedor");
  }

  public static boolean esInternacionalAbierto(String tvgId) {
    for (String p : INTERNACIONALES) if (tvgId.startsWith(p)) return true;
    return false;
  }

  public static final String MOTIVO_PAGO = "canal de pago";
  public static final String MOTIVO_ORIGEN = "no viene del propio canal";

  /** Un canal que la búsqueda deja fuera, con el motivo, para mostrarlo: nada se esconde. */
  public static final class Excluido {
    public final String nombre, motivo, url;

    Excluido(String nombre, String motivo, String url) {
      this.nombre = nombre;
      this.motivo = motivo;
      this.url = url;
    }
  }

  /** Canales de la lista de Chile que pasan el filtro, con su categoría. */
  public static List<Canal> deChile(List<Entrada> entradas) {
    return deChile(entradas, new ArrayList<>());
  }

  /** Igual, y anota en {@code fuera} cada canal que se deja fuera y por qué. */
  public static List<Canal> deChile(List<Entrada> entradas, List<Excluido> fuera) {
    List<Canal> canales = new ArrayList<>();
    for (Entrada e : entradas) {
      if (esDePago(e.nombre)) {
        fuera.add(new Excluido(limpiar(e.nombre), MOTIVO_PAGO, e.url));
        continue;
      }
      if (origenDudoso(e.url)) {
        fuera.add(new Excluido(limpiar(e.nombre), MOTIVO_ORIGEN, e.url));
        continue;
      }
      String nombre = limpiar(e.nombre);
      String cat;
      String g = e.grupo.toLowerCase(Locale.ROOT);
      String n = nombre.toLowerCase(Locale.ROOT);
      if (NACIONAL.matcher(nombre).find()) cat = NACIONALES;
      else if (g.contains("music") || n.contains("radio") || n.contains(" fm")) cat = MUSICA;
      else if (g.contains("news") || n.contains("noticias")) cat = NOTICIAS;
      else cat = REGIONALES;
      canales.add(crear(e, nombre, cat));
    }
    return canales;
  }

  /** De una lista por idioma, solo las señales internacionales abiertas. */
  public static List<Canal> internacionales(List<Entrada> entradas) {
    List<Canal> canales = new ArrayList<>();
    for (Entrada e : entradas) {
      if (!esInternacionalAbierto(e.tvgId) || origenDudoso(e.url)) continue;
      canales.add(crear(e, limpiar(e.nombre), INTERNACIONAL));
    }
    return canales;
  }

  /** Página pública de TVN con sus señales en vivo. */
  public static final String TVN_EN_VIVO = "https://www.tvn.cl/en-vivo";
  private static final Pattern TVN_SENAL = Pattern.compile(
      "<a[^>]*href=\"(/[a-z0-9-]+)\"[^>]*data-ms=\"([0-9a-f]{24})\"[^>]*>\\s*([^<]{2,40})");

  /**
   * Señales de TVN tal como las publica tvn.cl: cada botón de su reproductor lleva el identificador
   * de Mediastream, la CDN que contrata TVN. Se arma el enlace público de esa señal; si TVN cambia su
   * página, simplemente no aparecen (no se rompe nada más).
   */
  public static List<Canal> deTvn(String html) {
    List<Canal> canales = new ArrayList<>();
    Matcher m = TVN_SENAL.matcher(html);
    while (m.find()) {
      String nombre = m.group(3).trim();
      String lower = nombre.toLowerCase(Locale.ROOT);
      if (lower.startsWith("lineal") || lower.startsWith("más")) continue; // señales temáticas de prueba
      if (lower.equals("tvchile")) nombre = "TV Chile";
      if (!lower.contains("tvn") && !lower.equals("tvchile")) nombre = "TVN " + nombre;
      String url = "https://mdstrm.com/live-stream-playlist/" + m.group(2) + ".m3u8";
      canales.add(new Canal("tvn.cl" + m.group(1), nombre, NACIONALES, url, "", false, "tvn.cl"));
    }
    return canales;
  }

  /**
   * Canales grandes que no publican un enlace abierto: aparecen en la lista y abren su app oficial
   * (o su página en la tienda para instalarla). Así están donde la gente los busca sin inventar enlaces.
   */
  public static final String APP = "app:";

  public static List<Canal> conAppPropia() {
    List<Canal> canales = new ArrayList<>();
    canales.add(new Canal("app:tvn", "TVN", NACIONALES, APP + "TVN", "", false, "app oficial"));
    canales.add(new Canal("app:13", "Canal 13", NACIONALES, APP + "13Go", "", false, "app oficial"));
    canales.add(new Canal("app:chv", "Chilevisión", NACIONALES, APP + "Chilevisión", "", false, "app oficial"));
    return canales;
  }

  private static Canal crear(Entrada e, String nombre, String categoria) {
    Matcher q = CALIDAD.matcher(e.nombre);
    String id = e.tvgId.isEmpty() ? "nombre:" + nombre.toLowerCase(Locale.ROOT) : e.tvgId;
    return new Canal(id, nombre, categoria, e.url, q.find() ? q.group(1) : "", e.nombre.contains("Not 24/7"), "iptv-org");
  }

  /** «Canal 13 (Chile) (1080p) [Not 24/7]» → «Canal 13». */
  static String limpiar(String nombre) {
    return nombre.replaceAll("\\[[^\\]]*\\]", "").replaceAll("\\((\\d{3,4}[pi]|Chile)\\)", "").replaceAll("\\s+", " ").trim();
  }
}
