package cl.itatagroup.canales;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Página para administrar «Mis listas» desde el teléfono, en la red de la casa. Solo escucha mientras
 * la tarjeta «Mis listas» está abierta en la tele, y cada cambio exige el código de 4 dígitos que se
 * ve en pantalla (cambia cada vez que se abre). No sirve nada más que esa página.
 */
public final class ServidorListas {
  private static final String TAG = "Canales";
  public static final int PUERTO = 8642;
  private static final int MAX_PEDIDO = 16 * 1024;

  public interface Cambios {
    /** En el hilo principal. */
    void agregar(String nombre, String url);

    /** Quita la lista en esa posición de {@link #listas()}. */
    void quitar(int indice);

    /** Listas actuales: pares {nombre, url}. Se llama desde el hilo del servidor. */
    List<String[]> listas();
  }

  private final Cambios cambios;
  private final Handler principal = new Handler(Looper.getMainLooper());
  public final String codigo;
  private ServerSocket socket;
  private int errados;

  public ServidorListas(Cambios cambios) {
    this.cambios = cambios;
    codigo = String.format(Locale.ROOT, "%04d", new SecureRandom().nextInt(10000));
  }

  public void iniciar() throws IOException {
    socket = new ServerSocket(PUERTO);
    new Thread(() -> {
      while (!socket.isClosed()) {
        try {
          Socket s = socket.accept();
          // Un hilo por conexión: una conexión lenta (p. ej. una precarga del navegador) no bloquea a las demás.
          new Thread(() -> {
            try (Socket c = s) {
              c.setSoTimeout(5000);
              atender(c);
            } catch (IOException | RuntimeException e) {
              Log.w(TAG, "pedido a Mis listas", e);
            }
          }, "mis-listas-pedido").start();
        } catch (IOException e) {
          if (!socket.isClosed()) Log.w(TAG, "servidor de listas", e);
        }
      }
    }, "mis-listas").start();
  }

  public void detener() {
    try {
      if (socket != null) socket.close();
    } catch (IOException ignorado) {
      // ya estaba cerrado
    }
  }

  /** La dirección de la tele en la red local, para mostrarla en pantalla. */
  public static String direccion() {
    try {
      for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
        if (!ni.isUp() || ni.isLoopback()) continue;
        for (InetAddress a : Collections.list(ni.getInetAddresses())) {
          if (a instanceof Inet4Address && a.isSiteLocalAddress()) return "http://" + a.getHostAddress() + ":" + PUERTO;
        }
      }
    } catch (IOException e) {
      Log.w(TAG, "sin dirección local", e);
    }
    return null;
  }

  private void atender(Socket s) throws IOException {
    InputStream in = s.getInputStream();
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    byte[] b = new byte[2048];
    int fin = -1;
    int largo = 0;
    while (buf.size() < MAX_PEDIDO) {
      int n = in.read(b);
      if (n < 0) break;
      buf.write(b, 0, n);
      String hasta = buf.toString("ISO-8859-1");
      if (fin < 0 && (fin = hasta.indexOf("\r\n\r\n")) >= 0) {
        for (String linea : hasta.substring(0, fin).split("\r\n")) {
          if (linea.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
            try {
              largo = Math.max(0, Math.min(Integer.parseInt(linea.substring(15).trim()), MAX_PEDIDO));
            } catch (NumberFormatException e) {
              largo = 0; // largo inválido: se lee lo que haya llegado
            }
          }
        }
      }
      if (fin >= 0 && buf.size() >= fin + 4 + largo) break;
    }
    byte[] bytes = buf.toByteArray();
    String cabecera = buf.toString("ISO-8859-1");
    String primera = cabecera.contains("\r\n") ? cabecera.substring(0, cabecera.indexOf("\r\n")) : cabecera;
    String mensaje = "";
    if (primera.startsWith("POST ") && fin >= 0) {
      int desde = Math.min(fin + 4, bytes.length);
      Map<String, String> campos = formulario(new String(bytes, desde, bytes.length - desde, StandardCharsets.US_ASCII));
      if (!codigo.equals(campos.get("codigo"))) {
        // Tras 5 códigos errados se deja de escuchar: probar los 10.000 desde la red no es posible.
        if (++errados >= 5) {
          responder(s.getOutputStream(), "403 Forbidden", "Demasiados códigos errados. Vuelve a abrir «Mis listas» en la tele.");
          detener();
          return;
        }
        mensaje = "El código no coincide con el que muestra la tele.";
      } else if (primera.startsWith("POST /quitar")) {
        // Se quita por posición, no por enlace: el enlace (que en Xtream lleva usuario y clave) nunca
        // viaja en la página.
        try {
          int indice = Integer.parseInt(campos.get("i"));
          principal.post(() -> cambios.quitar(indice));
          mensaje = "Lista quitada.";
        } catch (NumberFormatException e) {
          mensaje = "No se encontró esa lista.";
        }
      } else {
        String nombre = limpiar(campos.get("nombre"));
        String url = campos.get("url") == null ? "" : campos.get("url").trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
          mensaje = "El enlace tiene que empezar con http:// o https://";
        } else {
          String n = nombre.isEmpty() ? "Mi lista" : nombre;
          principal.post(() -> cambios.agregar(n, url));
          mensaje = "Lista agregada. La tele la está cargando.";
        }
      }
    } else if (!primera.startsWith("GET / ") && !primera.startsWith("GET /?")) {
      responder(s.getOutputStream(), "404 Not Found", "No existe.");
      return;
    }
    responder(s.getOutputStream(), "200 OK", pagina(mensaje));
  }

  private static Map<String, String> formulario(String cuerpo) {
    Map<String, String> campos = new HashMap<>();
    for (String par : cuerpo.split("&")) {
      int i = par.indexOf('=');
      if (i <= 0) continue;
      try {
        campos.put(URLDecoder.decode(par.substring(0, i), "UTF-8"), URLDecoder.decode(par.substring(i + 1), "UTF-8"));
      } catch (IOException | IllegalArgumentException ignorado) {
        // campo mal formado: se ignora
      }
    }
    return campos;
  }

  private static String limpiar(String s) {
    if (s == null) return "";
    s = s.trim();
    return s.length() > 40 ? s.substring(0, 40) : s;
  }

  private static String html(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }

  private String pagina(String mensaje) {
    StringBuilder filas = new StringBuilder();
    List<String[]> listas = cambios.listas();
    for (int i = 0; i < listas.size(); i++) {
      String[] l = listas.get(i);
      // Solo el nombre y el servidor: la página se ve sin código y el enlace puede llevar usuario y clave.
      filas.append("<li><b>").append(html(l[0])).append("</b><br><small>").append(html(Reproductor.hostDe(l[1])))
          .append("</small><form method=post action=/quitar><input type=hidden name=i value=").append(i)
          .append("><input name=codigo inputmode=numeric maxlength=4 placeholder=Código required>")
          .append("<button>Quitar</button></form></li>");
    }
    if (filas.length() == 0) filas.append("<li>Todavía no hay listas.</li>");
    return "<!doctype html><html lang=es><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\">"
        + "<title>Mis listas · Canales</title><style>"
        + "body{font:17px/1.5 system-ui,sans-serif;background:#22251E;color:#F2F3EC;margin:0;padding:20px;max-width:560px}"
        + "h1{font-size:24px;margin:0 0 4px}p{color:#C9CDBE}label{display:block;margin-top:14px;font-weight:600}"
        + "input{width:100%;box-sizing:border-box;font:inherit;padding:10px;border-radius:6px;border:1px solid #3A3F33;background:#1B1E16;color:#F2F3EC}"
        + "button{margin-top:14px;font:inherit;font-weight:600;padding:10px 18px;border:0;border-radius:6px;background:#B5D67A;color:#1B1E16}"
        + "ul{padding:0;list-style:none}li{border-top:1px solid #3A3F33;padding:12px 0;word-break:break-all}"
        + "li input{width:110px;margin-right:8px}.msg{background:#3A3F33;padding:10px;border-radius:6px;color:#F2F3EC}"
        + "</style><h1>Mis listas</h1>"
        + "<p>Agrega una lista M3U o Xtream (el enlace <code>get.php?…&amp;type=m3u_plus</code>). Las listas que agregas no pasan por "
        + "ningún filtro y se ven en «Mis listas»: lo que contienen es responsabilidad de quien las agrega.</p>"
        + (mensaje.isEmpty() ? "" : "<p class=msg>" + html(mensaje) + "</p>")
        + "<form method=post action=/agregar>"
        + "<label for=n>Nombre</label><input id=n name=nombre maxlength=40 placeholder=\"Ej.: Mi proveedor\">"
        + "<label for=u>Enlace de la lista</label><input id=u name=url type=url required placeholder=\"https://…\">"
        + "<label for=c>Código que muestra la tele</label><input id=c name=codigo inputmode=numeric maxlength=4 required>"
        + "<button>Agregar</button></form><h1 style=\"margin-top:28px\">Listas</h1><ul>" + filas + "</ul>"
        + "<p><small>Esta página solo funciona mientras «Mis listas» está abierta en la tele.</small></p></html>";
  }

  private static void responder(OutputStream out, String estado, String cuerpo) throws IOException {
    byte[] datos = cuerpo.getBytes(StandardCharsets.UTF_8);
    String cabecera = "HTTP/1.0 " + estado + "\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + datos.length
        + "\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'\r\nConnection: close\r\n\r\n";
    out.write(cabecera.getBytes(StandardCharsets.ISO_8859_1));
    out.write(datos);
    out.flush();
  }
}
