package br.com.estoqueimpressoras;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Abre o app web (assets/www) numa WebView, totalmente offline. */
public class MainActivity extends Activity {
    private static final String URL_INICIAL = "https://appassets.androidplatform.net/assets/www/index.html";
    private static final int REQ_ABRIR_ARQUIVO = 1;
    private static final int REQ_SALVAR_ARQUIVO = 2;

    private WebView web;
    private ValueCallback<Uri[]> callbackArquivo;
    private String conteudoParaSalvar;

    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);   // localStorage = onde ficam os dados
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (ActivityNotFoundException ignored) { }
                return true;
            }
        });

        // Escolher arquivo (Restaurar backup). alert/confirm usam os diálogos padrão.
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (callbackArquivo != null) callbackArquivo.onReceiveValue(null);
                callbackArquivo = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "Escolha o arquivo de backup"), REQ_ABRIR_ARQUIVO);
                } catch (ActivityNotFoundException e) {
                    callbackArquivo = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Ponte(), "AndroidApp");

        if (estado != null) web.restoreState(estado);
        else web.loadUrl(URL_INICIAL);
    }

    /** Chamado pelo JavaScript para salvar o backup onde o usuário escolher. */
    private class Ponte {
        @JavascriptInterface
        public void salvarArquivo(final String nome, final String conteudo) {
            runOnUiThread(() -> {
                conteudoParaSalvar = conteudo;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, nome);
                try {
                    startActivityForResult(i, REQ_SALVAR_ARQUIVO);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this, "Não foi possível abrir o seletor de arquivos", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent dados) {
        super.onActivityResult(req, res, dados);
        Uri uri = (res == RESULT_OK && dados != null) ? dados.getData() : null;

        if (req == REQ_ABRIR_ARQUIVO && callbackArquivo != null) {
            callbackArquivo.onReceiveValue(uri != null ? new Uri[]{uri} : null);
            callbackArquivo = null;
        } else if (req == REQ_SALVAR_ARQUIVO) {
            if (uri != null && conteudoParaSalvar != null) {
                try (OutputStream o = getContentResolver().openOutputStream(uri)) {
                    o.write(conteudoParaSalvar.getBytes(StandardCharsets.UTF_8));
                    Toast.makeText(this, "Backup salvo!", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Erro ao salvar o backup", Toast.LENGTH_LONG).show();
                }
            }
            conteudoParaSalvar = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle estado) {
        super.onSaveInstanceState(estado);
        web.saveState(estado);
    }
}
