package rikka.shizuku.server;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * Translates old Shizuku API transaction codes to ShizukuPlus explicit codes.
 *
 * Legacy apps (e.g. Hex Bodhi) were compiled against the original Shizuku API where transaction
 * codes were assigned by AIDL position rather than explicit = N values. ShizukuPlus uses explicit
 * codes that are 1 higher than the positional codes the old client sends, so this proxy applies a
 * -1 offset for all standard methods.
 *
 * Special case: old code 14 was attachApplication(IBinder, String). In ShizukuPlus the new
 * attachApplication is at code 17 with signature (IShizukuApplication, Bundle). The proxy adapts
 * the old-style parcel into the new format so the server's ClientRecord is properly created.
 */
class LegacyShizukuBinderProxy extends Binder {

    private static final String DESCRIPTOR = "moe.shizuku.server.IShizukuService";
    // ShizukuApiConstants.ATTACH_APPLICATION_PACKAGE_NAME
    private static final String ATTACH_PACKAGE_KEY = "shizuku:attach-package-name";

    private final IBinder mRealBinder;

    LegacyShizukuBinderProxy(IBinder realBinder) {
        mRealBinder = realBinder;
        attachInterface(null, DESCRIPTOR);
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == 14) {
            // Old: attachApplication(IBinder app, String packageName)
            // New (SP code 17): attachApplication(IShizukuApplication app, Bundle args)
            data.enforceInterface(DESCRIPTOR);
            IBinder appBinder = data.readStrongBinder();
            String packageName = data.readString();

            Parcel newData = Parcel.obtain();
            try {
                newData.writeInterfaceToken(DESCRIPTOR);
                newData.writeStrongBinder(appBinder);
                Bundle args = new Bundle();
                args.putString(ATTACH_PACKAGE_KEY, packageName);
                newData.writeBundle(args);
                // No apiVersion → server treats client as pre-v13, which is correct for Hex Bodhi
                return mRealBinder.transact(17, newData, reply, flags);
            } finally {
                newData.recycle();
            }
        }

        // All other methods: apply -1 offset (legacy positional codes are +1 vs SP explicit codes)
        return mRealBinder.transact(code - 1, data, reply, flags);
    }
}
