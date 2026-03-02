package it.isprambiente.pdnd;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        // Opzioni di default
        String env = "produzione";
        String configPath = null;
        boolean debug = false;
        boolean pretty = false;
        String apiUrl = null;
        String statusUrl = null;
        String apiUrlFilters = null;
        boolean jsonOutput = false;
        boolean save = false;
        boolean verifySSL = true;

        // Parsing manuale argomenti
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-e", "--env" -> { if (i + 1 < args.length) env = args[++i]; }
                case "-c", "--config" -> { if (i + 1 < args.length) configPath = args[++i]; }
                case "--debug" -> debug = true;
                case "--pretty" -> pretty = true;
                case "--api-url" -> { if (i + 1 < args.length) apiUrl = args[++i]; }
                case "--status-url" -> { if (i + 1 < args.length) statusUrl = args[++i]; }
                case "--api-url-filters" -> { if (i + 1 < args.length) apiUrlFilters = args[++i]; }
                case "--json" -> jsonOutput = true;
                case "--save" -> save = true;
                case "--no-verify-ssl" -> verifySSL = false;
                case "--help", "-h" -> {
                    printHelp();
                    return;
                }
            }
        }

        if (args.length == 0) {
            printHelp();
            return;
        }

        PdndClient client = new PdndClient();
        client.setDebug(debug);
        client.setEnv(env);
        client.setVerifySSL(verifySSL);

        try {
            // Carica configurazione
            client.config(configPath);

            // Parsing filtri
            if (apiUrlFilters != null) {
                Map<String, String> filters = new HashMap<>();
                String[] pairs = apiUrlFilters.split("&");
                for (String pair : pairs) {
                    String[] kv = pair.split("=");
                    if (kv.length == 2) {
                        filters.put(kv[0], kv[1]);
                    }
                }
                client.setFilters(filters);
            }

            // Gestione Token
            String token = null;
            if (save) {
                token = client.loadToken();
            }

            if (token == null || !client.isTokenValid()) {
                token = client.requestToken();
            }

            if (token != null) {
                if (save) client.saveToken(token);

                if (statusUrl != null) {
                    client.setStatusUrl(statusUrl);
                    Map<String, Object> status = client.getStatus(token);
                    printJson(status, pretty || debug);
                    return;
                }

                if (apiUrl != null) {
                    client.setApiUrl(apiUrl);
                    Map<String, Object> result = client.getApi(token);
                    String body = (String) result.get("body");
                    
                    if (pretty || debug) {
                        // Tenta di formattare il body se è JSON
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            Object json = mapper.readValue(body, Object.class);
                            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));
                        } catch(Exception e) {
                            System.out.println(body);
                        }
                    } else {
                        System.out.println(body);
                    }
                } else {
                    // Solo token richiesto
                    System.out.println(token);
                }
            }

        } catch (PdndException e) {
            if (jsonOutput) {
                System.out.println("{\"error\": \"" + e.getMessage() + "\"}");
            } else {
                System.err.println("Errore PDND: " + e.getMessage());
            }
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Errore Generico: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printJson(Object obj, boolean pretty) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            if (pretty) {
                System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj));
            } else {
                System.out.println(mapper.writeValueAsString(obj));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void printHelp() {
        System.out.println("""
            Utilizzo:
              java -jar pdnd-client.jar -c /percorso/config.json [opzioni]
            
            Opzioni:
              -e, --env         Specifica l'ambiente da usare (es. collaudo, produzione). Default: produzione
              -c, --config      Specifica il percorso completo del file di configurazione
              --debug           Abilita output dettagliato
              --pretty          Abilita output JSON formattato
              --api-url         URL dell’API da chiamare dopo la generazione del token
              --api-url-filters Filtri da applicare all'API (es. param=val&param2=val2)
              --status-url      URL dell’API di status per verificare la validità del token
              --json            Stampa errori in formato JSON
              --save            Salva il token per evitare di richiederlo a ogni chiamata
              --no-verify-ssl   Disabilita la verifica SSL (utile per ambienti di collaudo)
              --help            Mostra questo aiuto
            """);
    }
}