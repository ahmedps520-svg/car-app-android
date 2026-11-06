package io.github.ahmedps520.carbrowser;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UserAgentTest {

    /** What the WebView on a Rockchip PX3 head unit (Android 4.4, reporting "6.0.4") sends. */
    private static final String PX3 = "Mozilla/5.0 (Linux; Android 6.0.4; rkpx3 Build/KTU84Q) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/33.0.0.0 Safari/537.36";

    @Test
    public void desktopModeKeepsEngineButClaimsPc() {
        assertEquals("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/33.0.0.0 Safari/537.36", UserAgent.desktop(PX3));
    }

    @Test
    public void desktopModeDropsMobileToken() {
        String phone = "Mozilla/5.0 (Linux; Android 13; Pixel 7; wv) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Version/4.0 Chrome/120.0.6099.230 Mobile Safari/537.36";
        assertEquals("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/120.0.6099.230 Safari/537.36", UserAgent.desktop(phone));
    }

    @Test
    public void mobileModeDropsWebViewMarker() {
        assertEquals("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36",
                UserAgent.mobile("Mozilla/5.0 (Linux; Android 13; Pixel 7; wv) AppleWebKit/537.36"));
        assertEquals(PX3, UserAgent.mobile(PX3));
    }
}
