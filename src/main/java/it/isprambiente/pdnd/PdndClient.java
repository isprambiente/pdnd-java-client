package it.isprambiente.pdnd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import it.isprambiente.pdnd.model.Config;
import it.isprambiente.pdnd.model.TokenData;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

// Classe per interagire con l'API PDND.
public class PdndClient {

    private String kid;
    private String issuer;
    private String clientId;
    private String purposeId;
    private String privKeyPath;
    
    private boolean debug = false;
    private String apiUrl = null;
    private String statusUrl = null;
    private Long tokenExp = null;
    
    private String env = "produzione";
    private String endpoint = "https://auth.interop.pagopa.it/token.oauth2";
    private String aud = "auth.interop.pagopa.it/client-assertion";
    
    private String tokenFile = "";
    private boolean verifySSL = true;
    private Map<String, String> filters = new HashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpClient httpClient;

    public PdndClient() {
        // Inizializzazione standard
        initHttpClient();
    }

    // --- Setters ---
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    public void setDebug(boolean debug) { this.debug = debug; }
    public void setFilters(Map<String, String> filters) { this.filters = filters; }
    public void setStatusUrl(String statusUrl) { this.statusUrl = statusUrl; }

    public void setEnv(String env) {
        this.env = env;
        if ("collaudo".equalsIgnoreCase(env)) {
            this.endpoint = "https://auth.uat.interop.pagopa.it/token.oauth2";
            this.aud = "auth.uat.interop.pagopa.it/client-assertion";
        }
    }

    public void setVerifySSL(boolean verifySSL) {
        this.verifySSL = verifySSL;
        initHttpClient(); // Ricrea il client con le nuove impostazioni SSL
    }

    // --- Core Logic ---

    private void initHttpClient() {
        try {
            HttpClient.Builder builder = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2);

            if (!verifySSL) {
                // Trust manager che accetta tutto (per collaudo)
                TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
                };
                SSLContext sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, trustAllCerts, new SecureRandom());
                builder.sslContext(sslContext);
            }
            
            this.httpClient = builder.build();
        } catch (Exception e) {
            throw new RuntimeException("Impossibile inizializzare HTTP Client", e);
        }
    }

    public void config(String configPath) throws PdndException {
        try {
            JsonNode rootNode = null;
            if (configPath != null && !configPath.isEmpty()) {
                File configFile = new File(configPath);
                if (!configFile.exists()) {
                    throw new PdndException("File di configurazione non trovato: " + configPath);
                }
                rootNode = objectMapper.readTree(configFile);
            }

            // Determina la configurazione specifica per l'ambiente
            Config config = null;
            if (rootNode != null && rootNode.has(this.env)) {
                config = objectMapper.treeToValue(rootNode.get(this.env), Config.class);
            }

            // Fallback su variabili d'ambiente
            this.kid = (config != null && config.kid() != null) ? config.kid() : getEnvOrThrow("PDND_KID");
            this.issuer = (config != null && config.issuer() != null) ? config.issuer() : getEnvOrThrow("PDND_ISSUER");
            this.clientId = (config != null && config.clientId() != null) ? config.clientId() : getEnvOrThrow("PDND_CLIENT_ID");
            this.purposeId = (config != null && config.purposeId() != null) ? config.purposeId() : getEnvOrThrow("PDND_PURPOSE_ID");
            this.privKeyPath = (config != null && config.privKeyPath() != null) ? config.privKeyPath() : getEnvOrThrow("PDND_PRIVKEY_PATH");

            this.tokenFile = System.getProperty("java.io.tmpdir") + "/pdnd_token_" + this.purposeId + ".json";
            
            validateConfig();

        } catch (IOException e) {
            throw new PdndException("Errore durante la lettura della configurazione", e);
        }
    }

    public String requestToken() throws PdndException {
        try {
            PrivateKey privateKey = readPrivateKey(this.privKeyPath);
            
            long now = Instant.now().getEpochSecond();
            long exp = now + 600; // 10 minuti per il JWT assertion
            String jti = UUID.randomUUID().toString();

            // Creazione JWT Client Assertion
            String clientAssertion = Jwts.builder()
                    .header().add("kid", this.kid).add("typ", "JWT").and()
                    .issuer(this.issuer)
                    .subject(this.issuer)
                    .audience().add(this.aud).and()
                    .claim("purposeId", this.purposeId)
                    .id(jti)
                    .issuedAt(Date.from(Instant.ofEpochSecond(now)))
                    .expiration(Date.from(Instant.ofEpochSecond(exp)))
                    .signWith(privateKey, Jwts.SIG.RS256)
                    .compact();

            if (debug) {
                System.out.println("\n✅ Environment: " + this.env);
                System.out.println("📄 JWT (client_assertion): " + clientAssertion);
            }

            // Richiesta HTTP
            String formData = Map.of(
                    "client_id", this.clientId,
                    "client_assertion", clientAssertion,
                    "client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                    "grant_type", "client_credentials"
            ).entrySet().stream()
             .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
             .collect(Collectors.joining("&"));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.endpoint))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());
                String accessToken = json.get("access_token").asText();
                
                // Decodifica manuale payload per exp (senza verifica firma qui)
                String[] parts = accessToken.split("\\.");
                if (parts.length == 3) {
                    String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                    JsonNode payloadJson = objectMapper.readTree(payload);
                    this.tokenExp = payloadJson.get("exp").asLong();
                }

                if (debug) {
                    System.out.println("\n🔐 Access Token: "+ accessToken);
                    System.out.println("⏰ Scadenza: " + Date.from(Instant.ofEpochSecond(this.tokenExp)));
                }
                return accessToken;
            } else {
                throw new PdndException("Errore richiesta token. Status: " + response.statusCode() + " Body: " + response.body());
            }

        } catch (Exception e) {
            throw new PdndException("Errore durante la richiesta del token: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> getApi(String token) throws PdndException {
        String url = (this.apiUrl != null) ? this.apiUrl : prompt("apiUrl", "URL API mancante");

        if (!filters.isEmpty()) {
             String queryString = filters.entrySet().stream()
                    .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
             url += (url.contains("?") ? "&" : "?") + queryString;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "*/*")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            String body = response.body();
            if (debug) {
                System.out.println("\n🌐 Chiamata API: " + url);
                System.out.println("Status: " + response.statusCode());
                try {
                    Object json = objectMapper.readValue(body, Object.class);
                    System.out.println("Body:\n" + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));
                } catch (Exception e) {
                    System.out.println("Body: " + body);
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("status", response.statusCode());
            result.put("body", body);
            return result;

        } catch (Exception e) {
            throw new PdndException("Errore chiamata API: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> getStatus(String token) throws PdndException {
        String url = (this.statusUrl != null) ? this.statusUrl : prompt("statusUrl", "URL Status mancante");
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                 return objectMapper.readValue(response.body(), Map.class);
            } else {
                throw new PdndException("Errore Status API. Code: " + response.statusCode());
            }
        } catch (Exception e) {
            throw new PdndException("Exception in Status API: " + e.getMessage(), e);
        }
    }

    // --- Token Management ---

    public boolean isTokenValid() {
        if (this.tokenExp == null) return false;
        return Instant.now().getEpochSecond() < this.tokenExp;
    }

    public String loadToken() {
        File f = new File(this.tokenFile);
        if (!f.exists()) return null;
        try {
            TokenData data = objectMapper.readValue(f, TokenData.class);
            this.tokenExp = data.exp();
            return data.token();
        } catch (IOException e) {
            return null;
        }
    }

    public void saveToken(String token) {
        if (this.tokenExp == null) return;
        try {
            TokenData data = new TokenData(token, this.tokenExp);
            objectMapper.writeValue(new File(this.tokenFile), data);
        } catch (IOException e) {
            System.err.println("Impossibile salvare il token: " + e.getMessage());
        }
    }

    // --- Helpers ---

    private String getEnvOrThrow(String key) {
        String val = System.getenv(key);
        if (val == null) return null; // Gestito poi dal validateConfig
        return val;
    }

    private void validateConfig() throws PdndException {
        if (kid == null) throw new PdndException("Configurazione mancante: KID");
        if (clientId == null) throw new PdndException("Configurazione mancante: ClientID");
        if (privKeyPath == null) throw new PdndException("Configurazione mancante: Private Key Path");
    }

    private String prompt(String key, String msg) throws PdndException {
        System.out.print(msg + ": ");
        Scanner scanner = new Scanner(System.in);
        String line = scanner.nextLine();
        if (line == null || line.trim().isEmpty()) throw new PdndException("Input obbligatorio mancante: " + key);
        return line.trim();
    }

    private PrivateKey readPrivateKey(String path) throws Exception {
        try (FileReader keyReader = new FileReader(path);
             PEMParser pemParser = new PEMParser(keyReader)) {
            
            Object object = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            
            if (object instanceof PrivateKeyInfo) {
                return converter.getPrivateKey((PrivateKeyInfo) object);
            } else if (object instanceof org.bouncycastle.openssl.PEMKeyPair) {
                return converter.getPrivateKey(((org.bouncycastle.openssl.PEMKeyPair) object).getPrivateKeyInfo());
            } else {
                throw new PdndException("Formato chiave privata non supportato o non riconosciuto.");
            }
        }
    }
}