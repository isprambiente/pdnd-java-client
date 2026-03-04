# pdnd-java-client
![Java Security Audit](https://github.com/isprambiente/pdnd-java-client/actions/workflows/security-audit.yml/badge.svg)
![Java 21](https://img.shields.io/badge/Java-21-blue)
![Java 25](https://img.shields.io/badge/Java-25-orange)
![License](https://img.shields.io/github/license/isprambiente/pdnd-java-client)

Client Java 25 per autenticazione e chiamata API PDND (Piattaforma Digitale Nazionale Dati).

## Licenza

MIT

## Requisiti

- **Java JDK**: versione 21 o superiore (Consigliato **JDK 25**)
- **Maven**: 3.8+

## Installazione e Build

1. **Clona il repository**:

```bash
   git clone <repository-url>
   cd pdnd-java-client

```

2. **Compila il progetto**:
Esegui il comando Maven per pulire e pacchettizzare l'applicazione. Questo genererà un JAR "uber" (o "fat") contenente tutte le dipendenze necessarie.

```bash
mvn clean package

```


Il file eseguibile verrà creato in: `target/pdnd-client.jar`

## Configurazione

Il client richiede un file di configurazione JSON contenente i parametri per l'autenticazione OAuth2.

Crea un file (es. `configs/config.json`) con la seguente struttura:

```json
{
  "collaudo": {
    "kid": "IL_TUO_KID",
    "issuer": "IL_TUO_ISSUER",
    "clientId": "IL_TUO_CLIENT_ID",
    "purposeId": "IL_TUO_PURPOSE_ID",
    "privKeyPath": "/percorso/assoluto/private.key"
  },
  "produzione": {
    "kid": "IL_TUO_KID",
    "issuer": "IL_TUO_ISSUER",
    "clientId": "IL_TUO_CLIENT_ID",
    "purposeId": "IL_TUO_PURPOSE_ID",
    "privKeyPath": "/percorso/assoluto/private.key"
  }
}

```

> **Nota:** La chiave privata indicata in `privKeyPath` deve essere in formato PEM.

### Variabili d'ambiente

Se non viene fornito un file di configurazione, il client cercherà di leggere i seguenti parametri dalle variabili d'ambiente:

* `PDND_KID`
* `PDND_ISSUER`
* `PDND_CLIENT_ID`
* `PDND_PURPOSE_ID`
* `PDND_PRIVKEY_PATH`

## Utilizzo da Riga di Comando (CLI)

Esegui il JAR generato specificando le opzioni desiderate.

### Sintassi

```bash
java -jar target/pdnd-client.jar [OPZIONI]

```

### Opzioni Disponibili

| Opzione | Descrizione | Default |
| --- | --- | --- |
| `-c`, `--config` | Percorso  del file JSON di  configurazione. | - |
| `-e`, `--env` | Ambiente  da  utilizzare (`collaudo` o `produzione`). | `produzione` |
| `--api-url` | URL della  risorsa API PDND da  chiamare  dopo l'autenticazione. | - |
| `--api-url-filters` | Query string per filtri API (es. `stato=attivo&limit=10`). | - |
| `--status-url` | URL per verificare  la  validità  del token. | - |
| `--save` | Salva  il token su  disco (nella  cartella  temporanea) per riutilizzarlo  nelle  chiamate successive. | `false` |
| `--debug` | Abilita output dettagliato (payload JWT, risposte raw, ecc). | `false` |
| `--pretty` | Formatta l'output JSON (se  la  risposta è JSON). | `false` |
| `--json` | Stampa  gli  errori in formato JSON (utile per integrazioni script). | `false` |
| `--no-verify-ssl` | Disabilita  la  verifica  dei  certificati SSL (utile in collaudo/local). | `false` |
| `-h`, `--help` | Mostra  la  guida all'uso. | - |

### Esempi Pratici

**Chiamata API generica:**

```bash
java -jar target/pdnd-client.jar --api-url="https://api.pdnd.example.it/resource" --config /percorso/assoluto/progetto.json

```

**Verifica validità token:**

```bash
java -jar target/pdnd-client.jar --status-url="https://api.pdnd.example.it/status" --config /percorso/assoluto/progetto.json

```

**Debug attivo:**

```bash
java -jar target/pdnd-client.jar --debug --api-url="https://api.pdnd.example.it/resource"

```

## Utilizzo come Libreria Java

Puoi includere questo progetto come dipendenza e usare la classe `PdndClient` direttamente nel tuo codice Java.

```java
import it.isprambiente.pdnd.PdndClient;
import java.util.Map;

public class App {
    public static void main(String[] args) {
        try {
            PdndClient client = new PdndClient();
            
            // Configurazione
            client.setEnv("collaudo");
            client.config("/percorso/sample.json");
            
            // (Opzionale) Disabilita verifica SSL per ambiente di collaudo
            client.setVerifySSL(false); 

            // Ottenimento Token (con logica di cache su file se necessario)
            // Nota: implementare logica di cache personalizzata se non si usa saveToken()
            String token = client.requestToken();
            
            System.out.println("Token ottenuto: " + token);

            // Chiamata API
            client.setApiUrl("https://www.tuogateway.example.it/indirizzo/della/api");
            Map<String, Object> response = client.getApi(token);
            
            System.out.println("Risposta API: " + response.get("body"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

```

## Note Tecniche

* **Java 25 Features**: Il codice utilizza le più recenti funzionalità Java come `Records` per i DTO, `Switch Expressions` e il nuovo `java.net.http.HttpClient`.
* **Crittografia**: Utilizza BouncyCastle per la gestione robusta delle chiavi private (RSA) e `jjwt` per la creazione dei Client Assertion.

## Contribuire

Per domande o suggerimenti, apri una issue!
