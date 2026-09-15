package ma.aitaddi.fastcut;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * FASTCUT — enveloppe Android.
 *
 * L'application web est EMBARQUEE dans l'APK (dossier assets) : elle
 * fonctionne sans aucune connexion, comme une application classique.
 *
 * Le seul point qui demande du code natif est le telechargement : dans
 * une WebView, un lien de telechargement cree par JavaScript ne declenche
 * rien. On expose donc une passerelle `AndroidFichiers` que la page web
 * appelle pour ecrire un fichier dans le dossier Telechargements.
 */
public class MainActivity extends AppCompatActivity {

    private WebView vue;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle etat) {
        super.onCreate(etat);

        vue = new WebView(this);
        setContentView(vue);

        WebSettings reglages = vue.getSettings();
        reglages.setJavaScriptEnabled(true);
        reglages.setDomStorageEnabled(true);          // localStorage : projets, reglages
        reglages.setAllowFileAccess(true);
        reglages.setLoadWithOverviewMode(true);
        reglages.setUseWideViewPort(true);
        reglages.setBuiltInZoomControls(false);
        reglages.setTextZoom(100);                    // la taille se regle dans l'app

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            reglages.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        vue.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest requete) {
                // Tout reste dans l'application : aucun lien externe
                return false;
            }
        });

        vue.addJavascriptInterface(new PasserelleFichiers(), "AndroidFichiers");

        // Retour arriere : revenir dans l'application plutot que la fermer
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (vue.canGoBack()) vue.goBack();
                else finish();
            }
        });

        vue.loadUrl("file:///android_asset/index.html");
    }

    /**
     * Passerelle appelee depuis la page web pour enregistrer un fichier
     * dans le dossier Telechargements public.
     */
    private class PasserelleFichiers {

        /**
         * @param nomFichier nom du fichier a creer
         * @param contenuBase64 contenu encode en base64
         * @param typeMime type du fichier (pour l'indexation systeme)
         */
        @JavascriptInterface
        public void enregistrer(String nomFichier, String contenuBase64, String typeMime) {
            try {
                byte[] donnees = Base64.decode(contenuBase64, Base64.DEFAULT);

                File dossier = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                if (!dossier.exists() && !dossier.mkdirs()) {
                    signaler("Impossible d'accéder au dossier Téléchargements");
                    return;
                }

                File fichier = new File(dossier, nomFichier);
                try (FileOutputStream flux = new FileOutputStream(fichier)) {
                    flux.write(donnees);
                }

                // Rendre le fichier visible immediatement dans le gestionnaire
                DownloadManager gestionnaire =
                        (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (gestionnaire != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    try {
                        gestionnaire.addCompletedDownload(nomFichier, "FASTCUT", true,
                                typeMime, fichier.getAbsolutePath(), fichier.length(), true);
                    } catch (Exception ignore) {
                        // Certaines versions refusent : le fichier existe quand meme
                    }
                }

                signaler(nomFichier + " enregistré dans Téléchargements");

            } catch (IOException e) {
                signaler("Échec de l'enregistrement : " + e.getMessage());
            } catch (Exception e) {
                signaler("Échec de l'enregistrement");
            }
        }

        /** Indique a la page web qu'elle tourne dans l'application Android. */
        @JavascriptInterface
        public boolean disponible() {
            return true;
        }
    }

    private void signaler(final String texte) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, texte, Toast.LENGTH_LONG).show());
    }
}
