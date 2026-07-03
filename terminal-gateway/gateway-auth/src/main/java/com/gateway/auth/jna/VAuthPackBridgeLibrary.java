package com.gateway.auth.jna;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;

/**
 * SVAC PackData native bridge.
 *
 * <p>The bridge copies SDK output into a caller-provided buffer. PackData
 * output is not released in-process because the vendor SDK aborts in the JVM
 * when VAuth_Free is called for that pointer.
 */
public interface VAuthPackBridgeLibrary extends Library {

    String os = System.getProperty("os.name").toLowerCase();
    String libName = os.contains("win") ? "VAuthPackBridge" : "vauthpackbridge";
    VAuthPackBridgeLibrary INSTANCE = Native.load(libName, VAuthPackBridgeLibrary.class);

    int VAuthBridge_EncryptPackData(int handle, int isSign,
                                    byte[] pData, int dataLen,
                                    byte[] outBuffer, int outCapacity,
                                    IntByReference outLen);

    int VAuthBridge_DecryptPackData(int handle, int isVerify,
                                    byte[] pData, int dataLen,
                                    byte[] outBuffer, int outCapacity,
                                    IntByReference outLen);
}
