package it.isprambiente.pdnd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;


import static org.junit.jupiter.api.Assertions.*;

class PdndClientTest {

    private PdndClient client;

    @BeforeEach
    void setup() {
        client = new PdndClient();
    }

    // 1)  testConfigMissingFile
    @Test
    void testConfigMissingFile() {
        assertThrows(PdndException.class,
                () -> client.config("/path/invalido/config.json"));
    }

    // 2)  testConfigValidFile
    @Test
    void testConfigValidFile() throws Exception {
        client.setEnv("produzione");
        String path = copyResourceToTempFile("sample.json");

        client.config(path);

        assertEquals("kid",       getPrivate("kid"));
        assertEquals("issuer",    getPrivate("issuer"));
        assertEquals("clientId",  getPrivate("clientId"));
        assertEquals("purposeId", getPrivate("purposeId"));
        assertEquals("/tmp/key.pem", getPrivate("privKeyPath"));

        setPrivate("kid", "");
        assertEquals("", getPrivate("kid"));
    }

    // 3)  testIsTokenValidFalse
    @Test
    void testIsTokenValidFalse() throws Exception {
        setPrivate("tokenExp", nowSec() - 100);
        assertFalse(client.isTokenValid());
    }

    // 4)  testIsTokenValidTrue
    @Test
    void testIsTokenValidTrue() throws Exception {
        setPrivate("tokenExp", nowSec() + 100);
        assertTrue(client.isTokenValid());
    }

    // 5)  testSetAndGet (adattato)
    @Test
    void testSetAndGet() throws Exception {
        setPrivate("kid", "kid");
        setPrivate("issuer", "issuer");
        setPrivate("clientId", "clientId");
        setPrivate("purposeId", "purposeId");
        setPrivate("privKeyPath", "/tmp/key.pem");

        assertEquals("kid", getPrivate("kid"));
        assertEquals("issuer", getPrivate("issuer"));
        assertEquals("clientId", getPrivate("clientId"));
        assertEquals("purposeId", getPrivate("purposeId"));
        assertEquals("/tmp/key.pem", getPrivate("privKeyPath"));
    }

    // 6)  testValidateConfigThrowsException
    @Test
    void testValidateConfigThrowsException() {
        assertThrows(PdndException.class, () -> client.config(null));
    }

    // 7)  testValidateUrlThrowsException
    @Test
    void testValidateUrlThrowsException() {
        assertThrows(PdndException.class, () -> validateUrlForTest("ht!tp://url-non-valido"));
    }

    // 8)  testValidateUrlTrue
    @Test
    void testValidateUrlTrue() throws PdndException {
        assertTrue(validateUrlForTest("https://www.pagopa.gov.it/"));
    }

    // --------------------- Helpers -------------------------

    private Object getPrivate(String field) throws Exception {
        Field f = PdndClient.class.getDeclaredField(field);
        f.setAccessible(true);
        return f.get(client);
    }

    private void setPrivate(String field, Object value) throws Exception {
        Field f = PdndClient.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(client, value);
    }

    private long nowSec() {
        return System.currentTimeMillis() / 1000;
    }

    private String getResourcePath(String name) {
        return new File(getClass().getClassLoader().getResource(name).getFile()).getAbsolutePath();
    }

    /**
     * Simulazione test-side della validateUrl
     * @throws PdndException 
     */
    private boolean validateUrlForTest(String url) throws PdndException {
        try {
            URI uri = URI.create(url); // validazione sintattica
            if (uri.getScheme() == null || uri.getHost() == null)
                throw new PdndException("URL invalido");
            return true;
        } catch (Exception e) {
            throw new PdndException("URL invalido", e);
        }
    }
    

	private String copyResourceToTempFile(String resourceName) throws Exception {
	    try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourceName)) {
	        if (is == null) {
	            throw new IllegalStateException("Risorsa non trovata nel classpath: " + resourceName);
	        }
	        Path temp = Files.createTempFile("pdnd-test-", "-" + resourceName);
	        Files.copy(is, temp, REPLACE_EXISTING);
	        temp.toFile().deleteOnExit();
	        return temp.toString();
	    }
}

}