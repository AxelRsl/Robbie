package com.robbie.platform.vision

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import android.view.Surface

class OrionVisionSdkClient(context: Context) {
    interface Listener {
        fun onVisionDataConnected()
        fun onVisionDataDisconnected()
        fun onSurfaceShareConnected()
        fun onSurfaceShareDisconnected()
        fun onPersonData(persons: List<OrionPersonData>)
        fun onExposure(exposure: String)
        fun onVisionState(state: String, message: String)
        fun onSurfaceStop(reason: Int)
    }

    private val appContext = context.applicationContext

    @Volatile
    private var visionDataBinder: IBinder? = null

    @Volatile
    private var surfaceShareBinder: IBinder? = null

    @Volatile
    private var isVisionDataBound = false

    @Volatile
    private var isSurfaceShareBound = false

    var listener: Listener? = null

    private val dataCallbackBinder = DataCallbackBinder(
        onPersonData = { listener?.onPersonData(it) },
        onExposure = { listener?.onExposure(it) },
        onVisionState = { state, message -> listener?.onVisionState(state, message) }
    )

    private val surfaceCallbackBinder = SurfaceShareCallbackBinder {
        listener?.onSurfaceStop(it)
    }

    private val visionDataConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            visionDataBinder = service
            isVisionDataBound = service != null
            Log.i(TAG, "VisionData connected: $name binder=$service")
            listener?.onVisionDataConnected()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "VisionData disconnected: $name")
            visionDataBinder = null
            isVisionDataBound = false
            listener?.onVisionDataDisconnected()
        }
    }

    private val surfaceShareConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            surfaceShareBinder = service
            isSurfaceShareBound = service != null
            Log.i(TAG, "SurfaceShare connected: $name binder=$service")
            listener?.onSurfaceShareConnected()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "SurfaceShare disconnected: $name")
            surfaceShareBinder = null
            isSurfaceShareBound = false
            listener?.onSurfaceShareDisconnected()
        }
    }

    fun bindVisionData(): Boolean {
        if (isVisionDataBound) {
            return true
        }
        val intent = Intent().setClassName(VISION_PACKAGE, VISION_SERVICE).setAction(VISION_DATA_ACTION)
        val result = appContext.bindService(intent, visionDataConnection, Context.BIND_AUTO_CREATE)
        Log.i(TAG, "bindVisionData result=$result intent=$intent")
        return result
    }

    fun bindSurfaceShare(): Boolean {
        if (isSurfaceShareBound) {
            return true
        }
        val intent = Intent().setClassName(VISION_PACKAGE, VISION_SERVICE)
        val result = appContext.bindService(intent, surfaceShareConnection, Context.BIND_AUTO_CREATE)
        Log.i(TAG, "bindSurfaceShare result=$result intent=$intent")
        return result
    }

    fun isVisionDataConnected(): Boolean = isVisionDataBound && visionDataBinder != null

    fun isSurfaceShareConnected(): Boolean = isSurfaceShareBound && surfaceShareBinder != null

    fun registerDataCallback(): Boolean {
        val binder = visionDataBinder ?: return false
        return transactVoid(binder, VISION_DATA_DESCRIPTOR, TRANSACTION_SET_CALLBACK) {
            writeStrongBinder(dataCallbackBinder)
        }
    }

    fun clearDataCallback(): Boolean {
        val binder = visionDataBinder ?: return false
        return transactVoid(binder, VISION_DATA_DESCRIPTOR, TRANSACTION_SET_CALLBACK) {
            writeStrongBinder(null)
        }
    }

    fun showRecognizeBox(showFace: Boolean, showBody: Boolean): Boolean {
        val binder = visionDataBinder ?: return false
        return transactVoid(binder, VISION_DATA_DESCRIPTOR, TRANSACTION_SHOW_RECOGNIZE_BOX) {
            writeInt(if (showFace) 1 else 0)
            writeInt(if (showBody) 1 else 0)
        }
    }

    fun trackFace(enable: Boolean): Boolean {
        val binder = visionDataBinder ?: return false
        return transactVoid(binder, VISION_DATA_DESCRIPTOR, TRANSACTION_TRACK_FACE) {
            writeInt(if (enable) 1 else 0)
        }
    }

    fun setPersonLimit(limit: Int): Boolean {
        val binder = visionDataBinder ?: return false
        return transactVoid(binder, VISION_DATA_DESCRIPTOR, TRANSACTION_SET_PERSON_LIMIT) {
            writeInt(limit)
        }
    }

    fun setDetectOtherFaceOnTrack(enable: Boolean): Boolean {
        val binder = visionDataBinder ?: return false
        return transactVoid(binder, VISION_DATA_DESCRIPTOR, TRANSACTION_SET_DETECT_OTHER_FACE_ON_TRACK) {
            writeInt(if (enable) 1 else 0)
        }
    }

    fun setExposureThreshold(under: Int, over: Int): Boolean {
        val binder = visionDataBinder ?: return false
        return transactVoid(binder, VISION_DATA_DESCRIPTOR, TRANSACTION_SET_EXPOSURE_THRESHOLD) {
            writeInt(under)
            writeInt(over)
        }
    }

    fun startRecord(path: String): Boolean {
        val binder = visionDataBinder ?: return false
        return transactVoid(binder, VISION_DATA_DESCRIPTOR, TRANSACTION_START_RECORD) {
            writeString(path)
        }
    }

    fun stopRecord(): Boolean {
        val binder = visionDataBinder ?: return false
        return transactVoid(binder, VISION_DATA_DESCRIPTOR, TRANSACTION_STOP_RECORD)
    }

    fun saveFrameImage(path: String): Boolean {
        val binder = visionDataBinder ?: return false
        return transactVoid(binder, VISION_DATA_DESCRIPTOR, TRANSACTION_SAVE_FRAME_IMAGE) {
            writeString(path)
        }
    }

    fun bindSurface(surface: Surface?): Boolean {
        val binder = visionDataBinder ?: return false
        return transactSurfaceVoid(binder, VISION_DATA_DESCRIPTOR, TRANSACTION_BIND_SURFACE, surface)
    }

    fun registerSurfaceCallback(): Boolean {
        val binder = surfaceShareBinder ?: return false
        return transactVoid(binder, SURFACE_SHARE_DESCRIPTOR, TRANSACTION_SURFACE_REGISTER_CALLBACK) {
            writeStrongBinder(surfaceCallbackBinder)
        }
    }

    fun unregisterSurfaceCallback(): Boolean {
        val binder = surfaceShareBinder ?: return false
        return transactVoid(binder, SURFACE_SHARE_DESCRIPTOR, TRANSACTION_SURFACE_UNREGISTER_CALLBACK) {
            writeStrongBinder(surfaceCallbackBinder)
        }
    }

    fun setStreamSurface(surface: Surface): Boolean {
        val binder = surfaceShareBinder ?: return false
        return transactSurfaceBoolean(binder, SURFACE_SHARE_DESCRIPTOR, TRANSACTION_SURFACE_SET_STREAM, surface) {}
    }

    fun unSetStreamSurface(): Boolean {
        val binder = surfaceShareBinder ?: return false
        return transactVoid(binder, SURFACE_SHARE_DESCRIPTOR, TRANSACTION_SURFACE_UNSET_STREAM)
    }

    fun showSurface(surface: Surface, show: Boolean): Boolean {
        val binder = surfaceShareBinder ?: return false
        return transactSurfaceBoolean(binder, SURFACE_SHARE_DESCRIPTOR, TRANSACTION_SURFACE_SHOW, surface) {
            writeInt(if (show) 1 else 0)
        }
    }

    fun disconnect() {
        try {
            clearDataCallback()
        } catch (t: Throwable) {
            Log.w(TAG, "clearDataCallback failed", t)
        }
        try {
            unregisterSurfaceCallback()
        } catch (t: Throwable) {
            Log.w(TAG, "unregisterSurfaceCallback failed", t)
        }
        if (isVisionDataBound) {
            try {
                appContext.unbindService(visionDataConnection)
            } catch (t: Throwable) {
                Log.w(TAG, "unbind visionData failed", t)
            }
        }
        if (isSurfaceShareBound) {
            try {
                appContext.unbindService(surfaceShareConnection)
            } catch (t: Throwable) {
                Log.w(TAG, "unbind surfaceShare failed", t)
            }
        }
        visionDataBinder = null
        surfaceShareBinder = null
        isVisionDataBound = false
        isSurfaceShareBound = false
    }

    private fun transactVoid(
        binder: IBinder,
        descriptor: String,
        code: Int,
        writer: Parcel.() -> Unit = {}
    ): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(descriptor)
            data.writer()
            val status = binder.transact(code, data, reply, 0)
            if (!status) {
                Log.e(TAG, "transact failed descriptor=$descriptor code=$code")
                false
            } else {
                reply.readException()
                true
            }
        } catch (t: Throwable) {
            Log.e(TAG, "transact error descriptor=$descriptor code=$code", t)
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun transactSurfaceVoid(
        binder: IBinder,
        descriptor: String,
        code: Int,
        surface: Surface?
    ): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(descriptor)
            if (surface != null) {
                data.writeInt(1)
                surface.writeToParcel(data, 0)
            } else {
                data.writeInt(0)
            }
            val status = binder.transact(code, data, reply, 0)
            if (!status) {
                Log.e(TAG, "transactSurfaceVoid failed code=$code")
                false
            } else {
                reply.readException()
                if (reply.readInt() != 0 && surface != null) {
                    surface.readFromParcel(reply)
                }
                true
            }
        } catch (t: Throwable) {
            Log.e(TAG, "transactSurfaceVoid error code=$code", t)
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun transactSurfaceBoolean(
        binder: IBinder,
        descriptor: String,
        code: Int,
        surface: Surface,
        writer: Parcel.() -> Unit
    ): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(descriptor)
            data.writeInt(1)
            surface.writeToParcel(data, 0)
            data.writer()
            val status = binder.transact(code, data, reply, 0)
            if (!status) {
                Log.e(TAG, "transactSurfaceBoolean failed code=$code")
                false
            } else {
                reply.readException()
                val result = reply.readInt() != 0
                if (reply.readInt() != 0) {
                    surface.readFromParcel(reply)
                }
                result
            }
        } catch (t: Throwable) {
            Log.e(TAG, "transactSurfaceBoolean error code=$code", t)
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    companion object {
        private const val TAG = "OrionVisionSdkClient"

        const val VISION_PACKAGE = "com.ainirobot.visionsdk"
        const val VISION_SERVICE = "com.ainirobot.visionsdk.service.VisionSdkService"
        const val VISION_SERVICE_ACTION = "com.ainirobot.visionsdk.SERVICE"
        const val VISION_DATA_ACTION = "com.ainirobot.visionsdk.visiondata"

        private const val VISION_DATA_DESCRIPTOR = "com.ainirobot.visionsdk.IVisionData"
        private const val DATA_CALLBACK_DESCRIPTOR = "com.ainirobot.visionsdk.IDataCallback"
        private const val SURFACE_SHARE_DESCRIPTOR = "com.ainirobot.coreservice.surfaceshare.ISurfaceShareCommand"
        private const val SURFACE_SHARE_CALLBACK_DESCRIPTOR = "com.ainirobot.coreservice.surfaceshare.ISurfaceShareCallback"

        private const val TRANSACTION_BIND_SURFACE = 1
        private const val TRANSACTION_SHOW_RECOGNIZE_BOX = 2
        private const val TRANSACTION_START_RECORD = 3
        private const val TRANSACTION_STOP_RECORD = 4
        private const val TRANSACTION_SET_CALLBACK = 5
        private const val TRANSACTION_SET_EXPOSURE_THRESHOLD = 6
        private const val TRANSACTION_SET_DETECT_OTHER_FACE_ON_TRACK = 7
        private const val TRANSACTION_SAVE_FRAME_IMAGE = 8
        private const val TRANSACTION_TRACK_FACE = 9
        private const val TRANSACTION_SET_PERSON_LIMIT = 10

        private const val TRANSACTION_CALLBACK_PERSON_DATA = 1
        private const val TRANSACTION_CALLBACK_EXPOSURE = 2
        private const val TRANSACTION_CALLBACK_VISION_STATE = 3

        private const val TRANSACTION_SURFACE_SET_STREAM = 1
        private const val TRANSACTION_SURFACE_UNSET_STREAM = 2
        private const val TRANSACTION_SURFACE_SHOW = 3
        private const val TRANSACTION_SURFACE_REGISTER_CALLBACK = 4
        private const val TRANSACTION_SURFACE_UNREGISTER_CALLBACK = 5

        private const val INTERFACE_TRANSACTION_CODE = 1598968902
    }

    private class DataCallbackBinder(
        private val onPersonData: (List<OrionPersonData>) -> Unit,
        private val onExposure: (String) -> Unit,
        private val onVisionState: (String, String) -> Unit
    ) : Binder(), IInterface {
        init {
            attachInterface(this, DATA_CALLBACK_DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return when (code) {
                INTERFACE_TRANSACTION_CODE -> {
                    reply?.writeString(DATA_CALLBACK_DESCRIPTOR)
                    true
                }
                TRANSACTION_CALLBACK_PERSON_DATA -> {
                    data.enforceInterface(DATA_CALLBACK_DESCRIPTOR)
                    onPersonData(readPersonArray(data))
                    reply?.writeNoException()
                    true
                }
                TRANSACTION_CALLBACK_EXPOSURE -> {
                    data.enforceInterface(DATA_CALLBACK_DESCRIPTOR)
                    onExposure(data.readString().orEmpty())
                    reply?.writeNoException()
                    true
                }
                TRANSACTION_CALLBACK_VISION_STATE -> {
                    data.enforceInterface(DATA_CALLBACK_DESCRIPTOR)
                    val state = data.readString().orEmpty()
                    val message = data.readString().orEmpty()
                    onVisionState(state, message)
                    reply?.writeNoException()
                    true
                }
                else -> super.onTransact(code, data, reply, flags)
            }
        }

        private fun readPersonArray(parcel: Parcel): List<OrionPersonData> {
            val size = parcel.readInt()
            if (size <= 0) {
                return emptyList()
            }
            val list = ArrayList<OrionPersonData>(size)
            repeat(size) {
                val present = parcel.readInt() != 0
                if (present) {
                    list.add(OrionPersonData.fromParcel(parcel))
                }
            }
            return list
        }
    }

    private class SurfaceShareCallbackBinder(
        private val onStop: (Int) -> Unit
    ) : Binder(), IInterface {
        init {
            attachInterface(this, SURFACE_SHARE_CALLBACK_DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return when (code) {
                INTERFACE_TRANSACTION_CODE -> {
                    reply?.writeString(SURFACE_SHARE_CALLBACK_DESCRIPTOR)
                    true
                }
                1 -> {
                    data.enforceInterface(SURFACE_SHARE_CALLBACK_DESCRIPTOR)
                    onStop(data.readInt())
                    reply?.writeNoException()
                    true
                }
                else -> super.onTransact(code, data, reply, flags)
            }
        }
    }
}

data class OrionPersonData(
    val name: String,
    val associateId: Int,
    val id: Int,
    val withFace: Boolean,
    val withBody: Boolean,
    val angle: Int,
    val latency: Long,
    val faceX: Int,
    val faceY: Int,
    val faceWidth: Int,
    val faceHeight: Int,
    val faceAngleX: Float,
    val faceAngleY: Float,
    val distance: Float,
    val bodyX: Int,
    val bodyY: Int,
    val bodyWidth: Int,
    val bodyHeight: Int,
    val faceType: Int,
    val age: Int,
    val gender: Int,
    val mouthMove: Float,
    val yawLeft: Float,
    val yawEstimate: Float,
    val yawRight: Float,
    val liveNess: Int,
    val hasFkp: Boolean
) {
    companion object {
        fun fromParcel(parcel: Parcel): OrionPersonData {
            return OrionPersonData(
                name = parcel.readString().orEmpty(),
                associateId = parcel.readInt(),
                id = parcel.readInt(),
                withFace = parcel.readByte().toInt() != 0,
                withBody = parcel.readByte().toInt() != 0,
                angle = parcel.readInt(),
                latency = parcel.readLong(),
                faceX = parcel.readInt(),
                faceY = parcel.readInt(),
                faceWidth = parcel.readInt(),
                faceHeight = parcel.readInt(),
                faceAngleX = parcel.readFloat(),
                faceAngleY = parcel.readFloat(),
                distance = parcel.readFloat(),
                bodyX = parcel.readInt(),
                bodyY = parcel.readInt(),
                bodyWidth = parcel.readInt(),
                bodyHeight = parcel.readInt(),
                faceType = parcel.readInt(),
                age = parcel.readInt(),
                gender = parcel.readInt(),
                mouthMove = parcel.readFloat(),
                yawLeft = parcel.readFloat(),
                yawEstimate = parcel.readFloat(),
                yawRight = parcel.readFloat(),
                liveNess = parcel.readInt(),
                hasFkp = parcel.readByte().toInt() != 0
            )
        }
    }
}
