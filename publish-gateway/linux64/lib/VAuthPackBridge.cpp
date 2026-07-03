#include "VAuthSDK.h"

#include <cstring>

namespace {

const int BRIDGE_OK = 1;
const int BRIDGE_SDK_FAILED = 0;
const int BRIDGE_INVALID_ARGUMENT = -1;
const int BRIDGE_OUTPUT_TOO_SMALL = -2;

int copyOutput(bool success, unsigned char *nativeOut, unsigned int nativeOutLen,
               unsigned char *outBuffer, unsigned int outCapacity, unsigned int *outLen) {
    if (outLen != NULL) {
        *outLen = nativeOutLen;
    }

    if (!success || nativeOut == NULL) {
        return BRIDGE_SDK_FAILED;
    }

    if (outBuffer == NULL || outLen == NULL) {
        return BRIDGE_INVALID_ARGUMENT;
    }

    if (nativeOutLen > outCapacity) {
        return BRIDGE_OUTPUT_TOO_SMALL;
    }

    if (nativeOutLen > 0) {
        std::memcpy(outBuffer, nativeOut, nativeOutLen);
    }
    // Do not call VAuth_Free for PackData output here. In the JVM process the
    // vendor SDK aborts with "double free or corruption" when that pointer is
    // released, even when the release is performed inside this native bridge.
    // This is a deliberate stability workaround until the SDK release contract
    // is clarified or PackData is isolated in a helper process.
    return BRIDGE_OK;
}

} // namespace

extern "C" {

int VAuthBridge_EncryptPackData(int handle, int isSign,
                                const unsigned char *pData, unsigned int dataLen,
                                unsigned char *outBuffer, unsigned int outCapacity,
                                unsigned int *outLen) {
    if (pData == NULL || dataLen == 0 || outLen == NULL) {
        return BRIDGE_INVALID_ARGUMENT;
    }

    unsigned char *nativeOut = NULL;
    unsigned int nativeOutLen = 0;
    BOOL success = VAuth_EncryptPackData(handle, isSign ? TRUE : FALSE,
                                         pData, dataLen, nativeOut, nativeOutLen);
    return copyOutput(success != FALSE, nativeOut, nativeOutLen,
                      outBuffer, outCapacity, outLen);
}

int VAuthBridge_DecryptPackData(int handle, int isVerify,
                                const unsigned char *pData, unsigned int dataLen,
                                unsigned char *outBuffer, unsigned int outCapacity,
                                unsigned int *outLen) {
    if (pData == NULL || dataLen == 0 || outLen == NULL) {
        return BRIDGE_INVALID_ARGUMENT;
    }

    unsigned char *nativeOut = NULL;
    unsigned int nativeOutLen = 0;
    BOOL success = VAuth_DecryptPackData(handle, isVerify ? TRUE : FALSE,
                                         pData, dataLen, nativeOut, nativeOutLen);
    return copyOutput(success != FALSE, nativeOut, nativeOutLen,
                      outBuffer, outCapacity, outLen);
}

}
