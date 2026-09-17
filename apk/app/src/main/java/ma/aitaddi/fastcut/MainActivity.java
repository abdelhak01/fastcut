package ma.aitaddi.fastcut;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

/**
 * FASTCUT — enveloppe Android.
 *
 * L'application web est EMBARQUEE dans l'APK (dossier assets) : elle
 * fonctionne sans aucune connexion.
 *
 * Deux points demandent du code natif :
 *
 * 1. ENREGISTRER UN FICHIER. Dans une WebView, un telechargement
 *    declenche par JavaScript ne produit rien. La passerelle
 *    `AndroidFichiers` ecrit dans le dossier Telechargements.
 *
 * 2. CHOISIR UNE IMAGE. Un champ <input type="file"> reste sans effet
 *    tant qu'un WebChromeClient n'ouvre pas le selecteur du systeme :
 *    c'est le role de onShowFileChooser ci-dessous.
 */
public class MainActivity extends Activity {

    private WebView vue;
    private ValueCallback<Uri[]> retourSelection;
    private static final int CODE_SELECTION = 1001;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle etat) {
        super.onCreate(etat);

        vue = new WebView(this);
        setContentView(vue);

        WebSettings reglages = vue.getSettings();
        reglages.setJavaScriptEnabled(true);
        reglages.setDomStorageEnabled(true);      // localStorage : projets, reglages
        reglages.setAllowFileAccess(true);
        // Sans ces deux reglages, une page chargee en file:// se voit
        // refuser l'acces au stockage sur certaines versions d'Android :
        // les projets ne seraient pas conserves d'un lancement a l'autre.
        reglages.setAllowFileAccessFromFileURLs(true);
        reglages.setAllowUniversalAccessFromFileURLs(true);
        reglages.setLoadWithOverviewMode(true);
        reglages.setUseWideViewPort(true);
        reglages.setBuiltInZoomControls(false);
        reglages.setTextZoom(100);                // la taille se regle dans l'app

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            reglages.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        vue.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest requete) {
                return false;   // tout reste dans l'application
            }
        });

        // Ouvre le selecteur du systeme quand la page demande un fichier
        vue.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v,
                                             ValueCallback<Uri[]> callback,
                                             FileChooserParams parametres) {
                if (retourSelection != null) {
                    retourSelection.onReceiveValue(null);
                }
                retourSelection = callback;

                Intent intention;
                try {
                    intention = parametres.createIntent();
                } catch (Exception e) {
                    intention = new Intent(Intent.ACTION_GET_CONTENT);
                    intention.addCategory(Intent.CATEGORY_OPENABLE);
                    intention.setType("image/*");
                }

                try {
                    startActivityForResult(
                            Intent.createChooser(intention, "Choisir une photo"),
                            CODE_SELECTION);
                } catch (Exception e) {
                    retourSelection = null;
                    signaler("Aucune application de fichiers disponible");
                    return false;
                }
                return true;
            }
        });

        vue.addJavascriptInterface(new PasserelleFichiers(), "AndroidFichiers");
        vue.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int code, int resultat, Intent donnees) {
        if (code != CODE_SELECTION) {
            super.onActivityResult(code, resultat, donnees);
            return;
        }
        if (retourSelection == null) return;

        Uri[] fichiers = null;
        if (resultat == RESULT_OK && donnees != null) {
            try {
                fichiers = WebChromeClient.FileChooserParams.parseResult(resultat, donnees);
            } catch (Exception e) {
                Uri unique = donnees.getData();
                if (unique != null) fichiers = new Uri[]{ unique };
            }
        }
        retourSelection.onReceiveValue(fichiers);
        retourSelection = null;
    }

    /** Retour arriere : revenir dans l'application plutot que la fermer. */
    @Override
    public void onBackPressed() {
        if (vue != null && vue.canGoBack()) vue.goBack();
        else super.onBackPressed();
    }

    private class PasserelleFichiers {

        /**
         * @param nomFichier     nom du fichier a creer
         * @param contenuBase64  contenu encode en base64
         * @param typeMime       type du fichier (pour l'indexation systeme)
         */
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
                        // Certaines versions refusent : le fichier existe quand meme
                    }
                }

                signaler(nomFichier + " enregistre dans Telechargements");

            } catch (Exception e) {
                signaler("Echec de l'enregistrement");
            }
        }

        /** Indique a la page web qu'elle tourne dans l'application Android. */
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
