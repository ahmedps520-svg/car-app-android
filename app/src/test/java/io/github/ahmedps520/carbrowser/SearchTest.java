package io.github.ahmedps520.carbrowser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SearchTest {

    private static String url(String typed) {
        return Search.toUrl(typed, Search.GOOGLE);
    }

    @Test
    public void emptyInputLoadsNothing() {
        assertNull(url(null));
        assertNull(url("   "));
    }

    @Test
    public void addressesAreOpenedDirectly() {
        assertEquals("https://google.com", url("google.com"));
        assertEquals("https://en.m.wikipedia.org/wiki/Changan", url(" en.m.wikipedia.org/wiki/Changan "));
        assertEquals("https://example.app/x?y=1", url("example.app/x?y=1"));
        assertEquals("https://www.youtube.com", url("https://www.youtube.com"));
        assertEquals("http://neverssl.com", url("http://neverssl.com"));
    }

    @Test
    public void localAddressesUsePlainHttp() {
        assertEquals("http://192.168.1.1", url("192.168.1.1"));
        assertEquals("http://192.168.43.1:8080/status", url("192.168.43.1:8080/status"));
        assertEquals("http://localhost:3000", url("localhost:3000"));
    }

    @Test
    public void everythingElseIsSearched() {
        assertEquals("https://www.google.com/search?q=weather", url("weather"));
        assertEquals("https://www.google.com/search?q=changan+cs75+price", url("changan cs75 price"));
        assertEquals("https://www.google.com/search?q=c%2B%2B+%26+java", url("c++ & java"));
        assertEquals("https://www.google.com/search?q=hello.", url("hello."));
    }

    @Test
    public void otherEngines() {
        assertEquals("https://html.duckduckgo.com/html/?q=news", Search.toUrl("news", Search.DUCKDUCKGO));
        assertEquals("https://www.bing.com/search?q=news", Search.toUrl("news", Search.BING));
    }
}
