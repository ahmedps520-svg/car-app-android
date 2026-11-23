package io.github.ahmedps520.carbrowser;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BloatTest {

    private static Bloat.Kind k(String pkg) {
        return Bloat.classify(pkg, false, false, false, false);
    }

    @Test
    public void phoneLeftoversAreSafe() {
        assertEquals(Bloat.Kind.SAFE, k("com.android.email"));
        assertEquals(Bloat.Kind.SAFE, k("com.android.calendar"));
        assertEquals(Bloat.Kind.SAFE, k("com.android.deskclock"));
        assertEquals(Bloat.Kind.SAFE, k("com.android.galaxy4"));
        assertEquals(Bloat.Kind.SAFE, k("com.baidu.searchbox"));
        assertEquals(Bloat.Kind.SAFE, k("cn.wps.moffice_eng"));
    }

    @Test
    public void carAndSystemPartsAreKept() {
        assertEquals(Bloat.Kind.KEEP, k("com.android.systemui"));
        assertEquals(Bloat.Kind.KEEP, k("com.android.settings"));
        assertEquals(Bloat.Kind.KEEP, k("com.zxw.launcher"));
        assertEquals(Bloat.Kind.KEEP, k("com.xx.canbus"));
        assertEquals(Bloat.Kind.KEEP, k("com.hct.radio"));
        assertEquals(Bloat.Kind.KEEP, k("com.syu.bt"));
        assertEquals(Bloat.Kind.KEEP, k("com.android.camera2"));
        assertEquals(Bloat.Kind.KEEP, k("com.example.car.mcu"));
        assertEquals(Bloat.Kind.KEEP, k("com.carbit.easyconnection"));
        assertEquals(Bloat.Kind.KEEP, k("com.baidu.input"));          // keyboard, even though Baidu
        assertEquals(Bloat.Kind.KEEP, k("com.autonavi.minimap"));     // maps the Navigation button may use
        assertEquals(Bloat.Kind.KEEP, k("android.rockchip.update.service"));
        assertEquals(Bloat.Kind.KEEP, k("com.estrongs.android.pop")); // the file manager you need
    }

    @Test
    public void flagsAlwaysWin() {
        assertEquals(Bloat.Kind.KEEP, Bloat.classify("com.android.email", true, false, false, false));
        assertEquals(Bloat.Kind.KEEP, Bloat.classify("com.android.email", false, true, false, false));
        assertEquals(Bloat.Kind.KEEP, Bloat.classify("com.weird.thing", false, false, true, false));
    }

    @Test
    public void shortTokensOnlyMatchWholeSegments() {
        assertEquals(Bloat.Kind.OTHER, k("com.example.network"));   // contains "rk" but isn't Rockchip
        assertEquals(Bloat.Kind.OTHER, k("com.example.scanner"));   // contains "can" but isn't CAN-bus
    }
}
