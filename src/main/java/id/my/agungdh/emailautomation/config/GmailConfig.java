package id.my.agungdh.emailautomation.config;

import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

@Configuration
public class GmailConfig {

    private static final String APPLICATION_NAME = "Email Automation";
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = List.of(GmailScopes.GMAIL_READONLY);

    // ====== sumber dinamis ======
    @Value("${gmail.credentials.location:}")
    private String credentialsLocation; // contoh: classpath:/oauth/credentials.json atau file:/etc/creds/credentials.json

    @Value("${gmail.tokens.dir:tokens}")
    private String tokensDir; // tempat simpan token refresh

    @Value("${gmail.oauth.port:8888}")
    private int oauthPort; // port callback LocalServerReceiver (dev)

    @Bean
    public Gmail gmail() throws Exception {
        // 1) coba dari ENV JSON langsung (raw atau Base64)
        InputStream in = tryEnvJson("GOOGLE_CREDENTIALS_JSON");
        if (in == null) {
            // 2) coba dari ENV path
            in = tryEnvPath("GOOGLE_CREDENTIALS_PATH");
        }
        if (in == null && credentialsLocation != null && !credentialsLocation.isBlank()) {
            // 3) property gmail.credentials.location (classpath:/... atau file:/...)
            in = openFromLocation(credentialsLocation);
        }
        if (in == null) {
            // 4) fallback classpath default
            in = getResourceOrNull("/credentials.json");
        }
        if (in == null) {
            throw new IllegalStateException("""
          Tidak menemukan credentials.json.
          Set salah satu:
          - ENV GOOGLE_CREDENTIALS_JSON (raw JSON atau Base64)
          - ENV GOOGLE_CREDENTIALS_PATH (path file)
          - property gmail.credentials.location (classpath:/... atau file:/...)
          - atau taruh src/main/resources/credentials.json
          """);
        }

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in, StandardCharsets.UTF_8));

        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(Path.of(tokensDir).toFile()))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(oauthPort).build();

        return new Gmail.Builder(
                httpTransport,
                JSON_FACTORY,
                new AuthorizationCodeInstalledApp(flow, receiver).authorize("user"))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    // ===== helpers =====

    private InputStream tryEnvJson(String envKey) {
        String val = System.getenv(envKey);
        if (val == null || val.isBlank()) return null;

        // support Base64
        String json = val.trim();
        try {
            // kalau base64 valid dan decode menghasilkan teks JSON, pakai itu
            byte[] decoded = Base64.getDecoder().decode(json);
            String asText = new String(decoded, StandardCharsets.UTF_8);
            if (asText.trim().startsWith("{")) {
                return new ByteArrayInputStream(asText.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IllegalArgumentException ignored) {
            // bukan base64 — coba anggap raw JSON
        }

        if (json.startsWith("{")) {
            return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        }
        return null;
    }

    private InputStream tryEnvPath(String envKey) throws IOException {
        String path = System.getenv(envKey);
        if (path == null || path.isBlank()) return null;
        return Files.newInputStream(Path.of(path));
    }

    private InputStream openFromLocation(String location) throws IOException {
        Objects.requireNonNull(location);
        if (location.startsWith("classpath:")) {
            String cp = location.substring("classpath:".length());
            if (!cp.startsWith("/")) cp = "/" + cp;
            InputStream in = getResourceOrNull(cp);
            if (in == null) throw new FileNotFoundException("classpath tidak ditemukan: " + location);
            return in;
        } else if (location.startsWith("file:")) {
            String fp = location.substring("file:".length());
            return Files.newInputStream(Path.of(fp));
        } else {
            // kalau tidak ada prefix, anggap path file
            return Files.newInputStream(Path.of(location));
        }
    }

    private InputStream getResourceOrNull(String cpPath) {
        return GmailConfig.class.getResourceAsStream(cpPath);
    }
}
