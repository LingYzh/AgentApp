package com.example.myapplication.device

import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException

/**
 * Interface and client proxy for Shizuku privileged UserService IPC.
 * Uses manual Binder protocol with request-id and cancellation support.
 */
interface IDeviceUserService {
    fun asBinder(): IBinder
    fun getUid(): Int
    fun listApps(requestId: String, onlyLaunchable: Boolean, includeSystem: Boolean): String
    fun forceStop(requestId: String, packageName: String): String
    fun keyevent(requestId: String, keyCode: Int): String
    fun openSettings(requestId: String): String
    fun cancel(requestId: String): Boolean
    fun destroy()

    companion object {
        const val DESCRIPTOR = "com.example.myapplication.device.IDeviceUserService"
        const val TRANSACTION_DESTROY = 16777115
        const val TRANSACTION_GET_UID = 1
        const val TRANSACTION_LIST_APPS = 2
        const val TRANSACTION_FORCE_STOP = 3
        const val TRANSACTION_KEYEVENT = 4
        const val TRANSACTION_OPEN_SETTINGS = 5
        const val TRANSACTION_CANCEL = 6
    }
}

class DeviceUserServiceProxy(private val remote: IBinder) : IDeviceUserService {

    override fun asBinder(): IBinder = remote

    override fun getUid(): Int {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(IDeviceUserService.DESCRIPTOR)
            remote.transact(IDeviceUserService.TRANSACTION_GET_UID, data, reply, 0)
            reply.readException()
            reply.readInt()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    override fun listApps(requestId: String, onlyLaunchable: Boolean, includeSystem: Boolean): String {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(IDeviceUserService.DESCRIPTOR)
            data.writeString(requestId)
            data.writeInt(if (onlyLaunchable) 1 else 0)
            data.writeInt(if (includeSystem) 1 else 0)
            remote.transact(IDeviceUserService.TRANSACTION_LIST_APPS, data, reply, 0)
            reply.readException()
            reply.readString() ?: "[]"
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    override fun forceStop(requestId: String, packageName: String): String {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(IDeviceUserService.DESCRIPTOR)
            data.writeString(requestId)
            data.writeString(packageName)
            remote.transact(IDeviceUserService.TRANSACTION_FORCE_STOP, data, reply, 0)
            reply.readException()
            reply.readString() ?: "{}"
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    override fun keyevent(requestId: String, keyCode: Int): String {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(IDeviceUserService.DESCRIPTOR)
            data.writeString(requestId)
            data.writeInt(keyCode)
            remote.transact(IDeviceUserService.TRANSACTION_KEYEVENT, data, reply, 0)
            reply.readException()
            reply.readString() ?: "{}"
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    override fun openSettings(requestId: String): String {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(IDeviceUserService.DESCRIPTOR)
            data.writeString(requestId)
            remote.transact(IDeviceUserService.TRANSACTION_OPEN_SETTINGS, data, reply, 0)
            reply.readException()
            reply.readString() ?: "{}"
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    override fun cancel(requestId: String): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(IDeviceUserService.DESCRIPTOR)
            data.writeString(requestId)
            remote.transact(IDeviceUserService.TRANSACTION_CANCEL, data, reply, 0)
            reply.readException()
            reply.readInt() == 1
        } catch (_: Throwable) {
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    override fun destroy() {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(IDeviceUserService.DESCRIPTOR)
            remote.transact(IDeviceUserService.TRANSACTION_DESTROY, data, reply, 0)
            reply.readException()
        } catch (_: RemoteException) {
            // Expected when the remote service exits
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
