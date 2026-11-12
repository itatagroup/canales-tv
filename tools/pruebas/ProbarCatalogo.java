import cl.itatagroup.canales.Canal;
import cl.itatagroup.canales.Catalogo;
import cl.itatagroup.canales.Guia;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Pruebas de las reglas de Catalogo, en el PC. Uso: node tools/probar-catalogo.mjs [cl.m3u spa.m3u eng.m3u] */
public class ProbarCatalogo {
  static int fallas = 0;

  static void si(boolean cond, String que) {
    if (!cond) {
      fallas++;
      System.out.println("FALLA: " + que);
    }
  }

  public static void main(String[] args) throws Exception {
    // Pago
    si(Catalogo.esDePago("Disney Channel Latin America (1080p)"), "Disney es de pago");
    si(Catalogo.esDePago("ESPN 3 Latin America"), "ESPN es de pago");
    si(Catalogo.esDePago("TNT Sports (Chile) (480p)"), "TNT es de pago");
    si(!Catalogo.esDePago("T13 En Vivo (1080p)"), "T13 no es de pago");
    si(!Catalogo.esDePago("Canal 1 Nuble (1080p)"), "Canal 1 Ñuble no es de pago");
    si(!Catalogo.esDePago("Mega (Chile) (1080p)"), "Mega no es de pago");
    si(!Catalogo.esDePago("Radio Universal (720p)"), "Radio Universal no es de pago");
    si(!Catalogo.esDePago("Canal Claro (720p)"), "Canal Claro (regional) no es de pago");
    si(Catalogo.esDePago("Universal TV"), "Universal TV es de pago");
    // Origen
    si(Catalogo.origenDudoso("http://15.204.196.147:22880/MEGA2-MEGAMEDIA-EE.UU-PROVEEDOR/index.m3u8"), "IP suelta");
    si(Catalogo.origenDudoso("http://tr.live.clarovtrcdn.vtrplay.com/megahdchi/playlist.m3u8"), "CDN de VTR");
    si(Catalogo.origenDudoso("rtmp://algo/live"), "sin http");
    si(!Catalogo.origenDudoso("https://redirector.dps.live/hls/t13/playlist.m3u8"), "dps.live es del canal");
    si(!Catalogo.origenDudoso("https://pantera1-100gb-cl-movistar.dps.live/nctv/playlist.m3u8"), "nodo de dps en Movistar sigue siendo dps");
    // Lectura con coma dentro de un atributo
    String m3u = "#EXTM3U\n#EXTINF:-1 tvg-id=\"T13.cl@SD\" http-user-agent=\"Mozilla/5.0 (X11, Linux)\" group-title=\"News\",T13 En Vivo (1080p) [Not 24/7]\nhttps://redirector.dps.live/hls/t13/playlist.m3u8\n";
    List<Catalogo.Entrada> e = Catalogo.leer(new BufferedReader(new StringReader(m3u)));
    si(e.size() == 1 && e.get(0).nombre.startsWith("T13 En Vivo"), "nombre tras coma en atributo: " + (e.isEmpty() ? "-" : e.get(0).nombre));
    List<Canal> c = Catalogo.deChile(e);
    si(c.size() == 1 && c.get(0).nombre.equals("T13 En Vivo") && c.get(0).noSiempre && c.get(0).calidad.equals("1080p")
        && c.get(0).categoria.equals(Catalogo.NACIONALES), "T13 limpio, nacional, no 24/7, 1080p");
    // Internacional
    si(Catalogo.esInternacionalAbierto("DW.de@Espanol"), "DW en español entra");
    si(!Catalogo.esInternacionalAbierto("HBO.us@SD"), "HBO no entra");

    String tvn = "<a href=\"/senal-24play\" target=\"_top\" data-media-cntr=\"ms-player\" data-ms=\"57d1a22064f5d85712b20dab\" class=\"x\">24 Play</a>"
        + "<a href=\"/tvn3\" data-ms=\"5653641561b4eba30a7e4929\" class=\"x\">TVN 3</a>"
        + "<a href=\"/lineal-react\" data-ms=\"6706e34493921c967f4eafb9\">Lineal React</a>";
    List<Canal> t = Catalogo.deTvn(tvn);
    si(t.size() == 2 && t.get(0).nombre.equals("TVN 24 Play") && t.get(1).nombre.equals("TVN 3")
        && t.get(1).url.equals("https://mdstrm.com/live-stream-playlist/5653641561b4eba30a7e4929.m3u8"), "señales de tvn.cl");
    si(!Catalogo.origenDudoso(t.get(0).url), "mdstrm de TVN no es dudoso");

    // Guía: normalizar y parecidos
    String xml = "<channel id=\"Canal.La.Red.(Chile).cl\">\n<display-name lang=\"es\">La Red</display-name>\n</channel>\n"
        + "<programme start=\"20261112090000 -0300\" stop=\"20261112100000 -0300\" channel=\"Canal.La.Red.(Chile).cl\">\n"
        + "<title lang=\"es\">Hola Chile</title>\n</programme>\n"
        + "<programme start=\"20261112100000 -0300\" stop=\"20261112110000 -0300\" channel=\"Canal.La.Red.(Chile).cl\">\n"
        + "<title>Mentiras &amp; Verdades</title>\n</programme>\n";
    List<Canal> laRed = new java.util.ArrayList<>();
    laRed.add(new Canal("LaRed.cl", "La Red", Catalogo.NACIONALES, "https://x/y.m3u8", "", false, "iptv-org"));
    long ahora = new java.text.SimpleDateFormat("yyyyMMddHHmmss Z").parse("20261112093000 -0300").getTime();
    java.util.Map<String, List<Guia.Programa>> g = Guia.leer(new BufferedReader(new StringReader(xml)), laRed, ahora);
    Guia.Programa[] ad = Guia.ahoraYDespues(g.get("LaRed.cl"), ahora);
    si(ad != null && ad[0].titulo.equals("Hola Chile") && ad[1].titulo.equals("Mentiras & Verdades"), "guía: ahora y después de La Red");

    if (args.length >= 1) {
      List<Canal> chile = Catalogo.deChile(leerArchivo(args[0]));
      contar("Chile", chile);
      for (Canal x : chile) si(!x.nombre.startsWith("Mega"), "Mega de origen dudoso no debería entrar: " + x.url);
    }
    for (String a : args) {
      if (!a.endsWith(".xml") || args.length < 1) continue;
      List<Canal> todos = new java.util.ArrayList<>(Catalogo.deChile(leerArchivo(args[0])));
      for (int i = 1; i < args.length; i++) if (args[i].endsWith(".m3u")) todos.addAll(Catalogo.internacionales(leerArchivo(args[i])));
      todos.addAll(Catalogo.conAppPropia());
      try (BufferedReader r = Files.newBufferedReader(Paths.get(a), StandardCharsets.UTF_8)) {
        java.util.Map<String, List<Guia.Programa>> guia = Guia.leer(r, todos, System.currentTimeMillis());
        System.out.println("Guía: " + guia.size() + " canales con programación: " + guia.keySet());
      }
    }
    for (int i = 1; i < args.length; i++) if (args[i].endsWith(".m3u")) contar("Internacional " + args[i], Catalogo.internacionales(leerArchivo(args[i])));

    System.out.println(fallas == 0 ? "OK: todas las pruebas pasan" : fallas + " fallas");
    System.exit(fallas == 0 ? 0 : 1);
  }

  static List<Catalogo.Entrada> leerArchivo(String ruta) throws Exception {
    try (BufferedReader r = Files.newBufferedReader(Paths.get(ruta), StandardCharsets.UTF_8)) {
      return Catalogo.leer(r);
    }
  }

  static void contar(String que, List<Canal> canales) {
    Map<String, Integer> porCat = new TreeMap<>();
    for (Canal x : canales) porCat.merge(x.categoria, 1, Integer::sum);
    System.out.println(que + ": " + canales.size() + " " + porCat);
  }
}
