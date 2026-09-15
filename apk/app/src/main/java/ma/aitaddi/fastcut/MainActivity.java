package ma.aitaddi.fastcut;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends Activity {

    private WebView vue;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle etat) {
        super.onCreate(etat);

        vue = new WebView(this);
        setContentView(vue);

        WebSettings reglages = vue.getSettings();
        reglages.setJavaScriptEnabled(true);
        reglages.setDomStorageEnabled(true);
        reglages.setAllowFileAccess(true);
        reglages.setLoadWithOverviewMode(true);
        reglages.setUseWideViewPort(true);
        reglages.setBuiltInZoomControls(false);
        reglages.setTextZoom(100);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            reglages.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        vue.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest requete) {
                return false;
            }
        });

        vue.addJavascriptInterface(new PasserelleFichiers(), "AndroidFichiers");
        vue.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        if (vue != null && vue.canGoBack()) vue.goBack();
        else super.onBackPressed();
    }

    private class PasserelleFichiers {

        @JavascriptInterface
        public void enregistrer(String nomFichier, String contenuBase64, String typeMime) {
            try {
                byte[] donnees = Base64.decode(contenuBase64, Base64.DEFAULT);

                File dossier = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                if (!dossier.exists() && !dossier.mkdirs()) {
                    signaler("Impossible d'acceder au dossier Telechargements");
                    return;
                }

                File fichier = new File(dossier, nomFichier);
                FileOutputStream flux = new FileOutputStream(fichier);
                try {
                    flux.write(donnees);
                } finally {
                    flux.close();
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    try {
                        DownloadManager dm = (DownloadManager)
                                getSystemService(Context.DOWNLOAD_SERVICE);
                        if (dm != null) {
                            dm.addCompletedDownload(nomFichier, "FASTCUT", true,
                                    typeMime, fichier.getAbsolutePath(), fichier.length(), true);
                        }
                    } catch (Exception ignore) {
                    }
                }

                signaler(nomFichier + " enregistre dans Telechargements");

            } catch (Exception e) {
                signaler("Echec de l'enregistrement");
            }
        }

        @JavascriptInterface
        public boolean disponible() {
            return true;
        }
    }

    private void signaler(final String texte) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, texte, Toast.LENGTH_LONG).show();
            }
        });
    }
}
