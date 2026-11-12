package cl.itatagroup.canales;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;

/**
 * Reproductor a pantalla completa (H0): tres canales de prueba fijos, se cambia con ↑ ↓.
 * Mide cuánto tarda cada canal en mostrar la primera imagen y lo deja en el rótulo y en
 * {@code adb logcat -s Canales}. La grilla, el escaneo y las listas llegan en los hitos siguientes.
 */
public class Reproductor extends Activity {
  private static final String TAG = "Canales";

  /** Canales de prueba: enlaces de la CDN que usan los propios canales (dps.live). */
  private static final String[][] CANALES = {
    {"T13 En Vivo", "https://redirector.dps.live/hls/t13/playlist.m3u8"},
    {"UCV TV", "https://unlimited2-cl-isp.dps.live/ucvtv2/ucvtv2.smil/playlist.m3u8"},
    {"TV+", "https://jireh-8-hls-video-us-isp.dps.live/hls-video/ey6283je82983je9823je8jowowiekldk9838274/tvmas/tvmas.smil/playlist.m3u8"},
  };

  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable ocultarRotulo = () -> this.rotulo.setVisibility(View.GONE);

  private ExoPlayer player;
  private SurfaceView video;
  private View rotulo;
  private TextView nombre;
  private TextView estado;
  private int actual = 0;
  private long inicio;
  private String userAgent;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.reproductor);
    video = findViewById(R.id.video);
    rotulo = findViewById(R.id.rotulo);
    nombre = findViewById(R.id.nombre);
    estado = findViewById(R.id.estado);
    String version = "?";
    try {
      version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
    } catch (Exception e) {
      Log.w(TAG, "sin versión", e);
    }
    userAgent = "Canales/" + version;
  }

  @Override
  protected void onStart() {
    super.onStart();
    player = new ExoPlayer.Builder(this).build();
    player.setVideoSurfaceView(video);
    player.addListener(new Player.Listener() {
      @Override
      public void onRenderedFirstFrame() {
        long ms = SystemClock.elapsedRealtime() - inicio;
        Log.i(TAG, CANALES[actual][0] + ": primera imagen en " + ms + " ms");
        estado.setText(ms + " ms · " + getString(R.string.ayuda));
        handler.removeCallbacks(ocultarRotulo);
        handler.postDelayed(ocultarRotulo, 4000);
      }

      @Override
      public void onPlayerError(PlaybackException error) {
        Log.w(TAG, CANALES[actual][0] + ": " + error.getErrorCodeName(), error);
        handler.removeCallbacks(ocultarRotulo);
        rotulo.setVisibility(View.VISIBLE);
        estado.setText(getString(R.string.sin_senal) + " (" + error.getErrorCodeName() + ")");
      }
    });
    sintonizar(actual);
  }

  @Override
  protected void onStop() {
    handler.removeCallbacks(ocultarRotulo);
    if (player != null) {
      player.release();
      player = null;
    }
    super.onStop();
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_DPAD_UP:
      case KeyEvent.KEYCODE_CHANNEL_UP:
        sintonizar((actual + CANALES.length - 1) % CANALES.length);
        return true;
      case KeyEvent.KEYCODE_DPAD_DOWN:
      case KeyEvent.KEYCODE_CHANNEL_DOWN:
        sintonizar((actual + 1) % CANALES.length);
        return true;
      case KeyEvent.KEYCODE_DPAD_CENTER:
      case KeyEvent.KEYCODE_ENTER:
        handler.removeCallbacks(ocultarRotulo);
        rotulo.setVisibility(View.VISIBLE);
        handler.postDelayed(ocultarRotulo, 4000);
        return true;
      default:
        return super.onKeyDown(keyCode, event);
    }
  }

  private void sintonizar(int indice) {
    actual = indice;
    handler.removeCallbacks(ocultarRotulo);
    rotulo.setVisibility(View.VISIBLE);
    nombre.setText(CANALES[indice][0]);
    estado.setText(R.string.cargando);
    // User-Agent honesto: dice qué app es y su versión, nada del equipo ni del usuario.
    DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
        .setUserAgent(userAgent)
        .setAllowCrossProtocolRedirects(true);
    MediaItem item = new MediaItem.Builder()
        .setUri(Uri.parse(CANALES[indice][1]))
        .setMimeType(MimeTypes.APPLICATION_M3U8)
        .build();
    inicio = SystemClock.elapsedRealtime();
    player.setMediaSource(new HlsMediaSource.Factory(http).createMediaSource(item));
    player.prepare();
    player.setPlayWhenReady(true);
  }
}
