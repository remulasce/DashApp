package app.candash.cluster

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.google.common.collect.Lists
import kotlinx.coroutines.*
import java.io.Closeable
import java.io.IOException
import java.net.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration


@ExperimentalCoroutinesApi
class PandaService(val sharedPreferences: SharedPreferences, val context: Context) :
    CANService {
    private val TAG = PandaService::class.java.simpleName

    @ExperimentalCoroutinesApi
    private var flipBus = false
    private val carState = createCarState()
    private val carStateTimestamp = createCarStateTimestamp()
    private val liveCarState: LiveCarState = createLiveCarState()
    private var clearRequest = false
    private val port = 1338

    //    private var shutdown = false
//    private var inShutdown = false
    private val heartbeat = "ehllo" // sic. "ehllo" indicates that we support filter frames.
    private val goodbye = "bye"
    private val loopMinInterval = 0
    private var lastHeartbeatTimestamp = 0L
    private val heartBeatIntervalMs = 1_000
    private val socketTimeoutMs = 1_000
    private var socketTimeoutCounter = 0
    private val signalHelper = CANSignalHelper(sharedPreferences)
    private val pandaContext = newSingleThreadContext("PandaService")
    private var signalsToRequest: List<String> = arrayListOf()
    private var recentSignalsReceived: MutableSet<String> = mutableSetOf()
    private var lastReceivedCheckTimestamp = 0L
    private val signalsReceivedCheckIntervalMs = 10_000
    private lateinit var socket: DatagramSocket
    private var lastReadSucceeded = false

    private val listeners = AtomicInteger(0)

    // Technically these are cancellable, but they are basically process singletons.
    private val pandaJob = getPandaServiceJob()

    init {
        pandaJob.start()
    }

    // Head of the queue is the actively running state. It's a queue so multiple immediate state
    // changes don't get lost- ie. if user uses a restart() command, then a new listener comes in.
    // The loop shouldn't forget the fact that it has to do the restart() before starting the job.
    private val stateQueue = ConcurrentLinkedQueue<State>(Lists.newArrayList(State.NoListeners))

    sealed interface State {

        sealed interface ShutdownType : State

        /**
         * Service is shut down because nobody is listening. It will start again if a listener
         * registers.
         */
        data object NoListeners : ShutdownType

        /** Service is shut down because of an explicit shutdown request. It can only be restarted
         * by an explicit restart request. Likely, settings are being changed so we need to wait to
         * pull them again.
         */
        data object ForcedOff : ShutdownType


        sealed interface ListeningType : State

        /** Connected and running successfully */
        data object Running : ListeningType

        /** We want to connect, but aren't able due to errors */
        data object Error : ListeningType
    }

    override fun clearCarState() {
        clearRequest = true
    }

    override fun carState(): CarState {
        return carState
    }

    override fun carStateTimestamp(): CarStateTimestamp {
        return carStateTimestamp
    }

    override fun liveCarState(): LiveCarState {
        return liveCarState
    }

    private fun getSocket(): DatagramSocket {
        if (!this::socket.isInitialized || socket.isClosed) {
            socket = DatagramSocket(null)
            socket.soTimeout = socketTimeoutMs
            socket.reuseAddress = true
        }
        return socket
    }

    override fun isRunning(): Boolean {
        // Return the current active state. Really there could be a queued shutdown, but not yet.
        // Does not account for errors- if we're trying to run, we're running.
        return stateQueue.peek() == State.Running
    }

    override fun getType(): CANServiceType {
        return CANServiceType.PANDA
    }

    @ExperimentalCoroutinesApi
    override fun startRequests(lifetime: CoroutineScope) {
        Log.i(TAG, "startRequests")

        lifetime.launch {
            // We're first; start the party.
            if (listeners.incrementAndGet() == 1) {
                // The eternally running panda service will notice there's a listener now.
                pushStartup()
            }
            // Don't need to do anything; just hang around to wait for the parent scope to finish.
            delay(Duration.INFINITE)
        }
            .invokeOnCompletion {
                if (listeners.decrementAndGet() == 0) {
                    pushShutdownNoListeners()
                    // Main job cancel.
                }
            }
    }

    override fun shutdown() {
        Log.i(TAG, "shutdown. Posting ForcedOff")
        stateQueue.add(State.ForcedOff)
    }

    override fun restart() {
        pushRestartInternal()
    }

    private fun pushStartup() {
        // Can't start up from forced off. And no need to if we're on, even in an error state.
        if (stateQueue.peek() == State.NoListeners) {
            // Try to start running.
            stateQueue.add(State.Running)
        }
    }

    private fun pushShutdownNoListeners() {
        if (stateQueue.last() !is State.ShutdownType) {
            stateQueue.add(State.NoListeners)
        }
    }

    private fun pushRestartInternal() {
        // Restart implies running after. The service will dedupe repeated "shutdown" states, so no
        // need to check if we're currently not running. Restart is the only thing that can clear
        // a force-off.
        val newState = if (listeners.get() == 0) State.NoListeners else State.Running

        stateQueue.addAll(Lists.newArrayList(State.ForcedOff, newState))
    }

    /**
     * Should only be called/running once!
     * This job starts paused, so call .start() on it soon.
     */
    private fun getPandaServiceJob(): Job {
        signalsToRequest = Lists.newArrayList()

        // GlobalScope: This is a true application-level singleton that lives for the lifetime of
        // the program. It puts itself to "sleep" (delay()) when no UI elements need it, but
        // does not itself actually stop running until the Application is destroyed.
        return GlobalScope.launch(pandaContext, CoroutineStart.LAZY) {
            // FIXME: Implicitly requests all values. signalsToRequest doesn't really work anyway,
            // and especially doesn't work between multiple differing requests.
            Log.d(TAG, "Starting requests on thread: ${Thread.currentThread().name}")
            registerRecoverOnNewNetwork()
            while (true) {
                try {
                    pandaServiceInternal()
                } catch (exception: Exception) {
                    // FIXME: Ugh, so how does this interact with the inner-loop service? It's basically
                    // unrecoverable at this point, except with explicit restart()
                    // FIXME: Should remember the last state...
                    stateQueue.add(State.Error)
                    stateQueue.retainAll(setOf(State.Error))
                    Log.e(
                        TAG,
                        "Unknown Exception while sending or receiving data. Disconnecting and shutting down.",
                        exception
                    )
                }
            }
        }
    }

    private suspend fun pandaServiceInternal() {

        // Complete all state transitions first. This avoids weird single-frame requests if we try
        // to act on a "RUN" that already has "SHUTDOWN" following it.
        while (stateQueue.size > 1) {
            // This is the only place elements are removed, so no need to synchronize on stateQueue.
            val currState = stateQueue.poll()
            val nextState = stateQueue.peek()

            if (currState == nextState) {
                // This case is here so the next lines line up. Can happen if restart() is sent
                // while the service is shutdown
            } else if (currState is State.ShutdownType && nextState is State.Running) {
                // kinda dumb, but if you close and restart the app real fast this forces the
                // CANServer to close the old connection.
                sendBye()
                sendHello()
            } else if (currState is State.Running && nextState is State.ShutdownType) {
                sendBye()
                disconnectSocket()
            }
        }

        // Now act on the eventual state
        when (stateQueue.peek()!!) {
            State.Running -> {
                maybeSendHeartbeat()
                readSingleFrame()
            }

            State.Error -> {
                Log.i(
                    TAG,
                    "PandaService is in ERROR state. Occasionally checking for connection..."
                )
                maybeSendHeartbeat()
                readSingleFrame()
                delay(1_000) // Same as RUNNING, but with less logspam.
            }

            State.NoListeners -> {
                Log.v(TAG, "PandaService has no listeners. Waiting...")
                delay(1_000)
            }

            State.ForcedOff -> {
                Log.v(TAG, "PandaService is forced off. Waiting...")
                delay(10)
            }
        }

        /*
                    Log.d(TAG, "Binary = " + buf.getPayloadBinaryString())
                    Log.d(TAG, "FrameId = " + newPandaFrame.frameIdHex.hexString)
                    Log.d(TAG, "BusId = " + newPandaFrame.busId)
                    Log.d(TAG, "FrameLength = " + newPandaFrame.frameLength)
                     */

        yield()

        if (clearRequest) {
            carState.clear()
            liveCarState.clear()
            clearRequest = false
        }
    }

    private fun maybeSendHeartbeat() {
        val now = System.currentTimeMillis()
        if (now > (lastHeartbeatTimestamp + heartBeatIntervalMs)) {
            Log.d(TAG, "Sending regular heartbeat on thread: ${Thread.currentThread().name}")
            sendHello()
        }
    }

    private fun readSingleFrame() {
        warnIfMissingSignals()

        // up to 512 frames which are 16 bytes each
        val buf = ByteArray(16 * 512)
        val packet = DatagramPacket(buf, buf.size, serverAddress())
        Log.d(TAG, "C: Waiting to receive... on thread: ${Thread.currentThread().name}")

        try {
            getSocket().receive(packet)
            if (!lastReadSucceeded) {
                // Cool, reconnected after a timeout.
                Log.w(TAG, "Panda not connected; clearing signals")
                recentSignalsReceived.clear()
                lastReceivedCheckTimestamp = System.currentTimeMillis()
                lastReadSucceeded = true
                socketTimeoutCounter = 0
            }
        } catch (socketTimeoutException: SocketTimeoutException) {
            Log.w(
                TAG,
                "Socket timed out without receiving a packet on thread: ${Thread.currentThread().name}"
            )
            lastReadSucceeded = false
            socketTimeoutCounter += 1
            if (socketTimeoutCounter == 3) {
                // one-shot clear data on 3rd timeout
                carState.clear()
                liveCarState.clear()
            }
            // FIXME: A TimeoutException doesn't mean much to us. The car has no guarantee that it
            // produces CAN signals at any given rate, especially not the subset we are requesting.
            // Further, the CANServer may be configured to rate-limit us. Or, because the packets
            // are UDP, we could have dropped some.
            // Therefore, we shouldn't kill the connection just because of a timeout.
            //                        sendBye(getSocket())
            return
        }

        //Log.d(TAG, "Packet from: " + packet.address + ":" + packet.port)
        for (i in buf.indices step 16) {

            val newPandaFrame = NewPandaFrame(buf.sliceArray(i..i + 15))
            // Log.d(TAG, "bufindex = " + i.toString()+ " pandaFrame :" + newPandaFrame.frameId.toString())
            if (newPandaFrame.frameId == 0L) {
                break
            } else if (newPandaFrame.frameId == 6L && newPandaFrame.busId == 15L) {
                Log.v(TAG, "Received ack")
                // Post acks unconditionally to show we're connected to the server
                carState[SName.canServerAck] = 1f
                carStateTimestamp[SName.canServerAck] = System.currentTimeMillis()
                liveCarState[SName.canServerAck]!!.postValue(
                    SignalState(
                        1f,
                        System.currentTimeMillis()
                    )
                )
                // It's an ack
                sendFilter(getSocket(), signalsToRequest)
            } else {
                handleFrame(newPandaFrame)
            }
        }
    }

    private fun warnIfMissingSignals() {
        val now = System.currentTimeMillis()
        if (lastReadSucceeded && now > lastReceivedCheckTimestamp + signalsReceivedCheckIntervalMs) {
            val deltaSeconds = ((now - lastReceivedCheckTimestamp) / 1000).toInt()
            for (name in signalHelper.getAllCANSignalNames()) {
                if (!recentSignalsReceived.contains(name)) {
                    Log.v(
                        TAG,
                        "Did not receive signal '$name' in the last $deltaSeconds seconds"
                    )
                    if (carState[name] != null) {
                        carState[name] = null
                        liveCarState[name]!!.postValue(null)
                        // Calculate augmented signals which depend on this signal (even when null)
                        calculateAugments(name)
                    }
                }
            }
            recentSignalsReceived.clear()
            lastReceivedCheckTimestamp = now
        }
    }

    private fun handleFrame(frame: NewPandaFrame) {
        // 0x399 is a different length on each bus, so we use it to auto-detect the buses
        // Length of 3 only on vehicle bus
        if (frame.frameIdHex == Hex(0x399) && (frame.frameLength == 3L)) {
            if (frame.busId.toInt() == Constants.vehicleBus && flipBus) {
                // chassis bus = 0, vehicle bus = 1, don't flip
                Log.i(TAG, "chassis bus = 0, vehicle bus = 1; un-flipping bus IDs")
                flipBus = false
                sendFilter(getSocket(), signalsToRequest)
                return
            } else if (frame.busId.toInt() == Constants.chassisBus && !flipBus) {
                // chassis bus = 1, vehicle bus = 0, should flip
                Log.i(TAG, "chassis bus = 1, vehicle bus = 0; flipping bus IDs")
                flipBus = true
                sendFilter(getSocket(), signalsToRequest)
                return
            }
        }

        var busId = frame.busId.toInt()
        if (flipBus) {
            busId = when (busId) {
                Constants.chassisBus -> Constants.vehicleBus
                Constants.vehicleBus -> Constants.chassisBus
                else -> Constants.anyBus
            }
        }

        Log.v(TAG, "Received signals: ${signalHelper.getSignalsForFrame(busId, frame.frameIdHex)}")
        signalHelper.getSignalsForFrame(busId, frame.frameIdHex).forEach { signal ->
            val sigVal = frame.getCANValue(signal)
            carStateTimestamp[signal.name] = System.currentTimeMillis()
            // Only send the value if it changed from last time
            if (sigVal != null && sigVal != carState[signal.name]) {
                carState[signal.name] = sigVal
                liveCarState[signal.name]!!.postValue(
                    SignalState(
                        sigVal,
                        System.currentTimeMillis()
                    )
                )
            }
            if (sigVal != null) {
                recentSignalsReceived.add(signal.name)
                // Calculate augmented signals which depend on this signal
                calculateAugments(signal.name)
            }
        }
    }

    private fun calculateAugments(signalName: String) {
        signalHelper.getAugmentsForDep(signalName).forEach {
            val value = it.second(carState)
            if (value != null && value != carState[it.first]) {
                carState[it.first] = value
                carStateTimestamp[it.first] = System.currentTimeMillis()
                liveCarState[it.first]!!.postValue(
                    SignalState(value, System.currentTimeMillis())
                )
                // Recursively calculate deeper augments:
                calculateAugments(it.first)
            }
            if (value != null) {
                recentSignalsReceived.add(it.first)
            }
        }
    }

    private fun sendHello() {
        val socket = getSocket()
        // prepare data to be sent
        val udpOutputData = heartbeat

        // prepare data to be sent
        val buf: ByteArray = udpOutputData.toByteArray()

        Log.d(TAG, "sendHello")
        sendData(socket, buf)
        lastHeartbeatTimestamp = System.currentTimeMillis()
    }

    private fun sendBye() {
        val socket = getSocket()
        // prepare data to be sent
        val udpOutputData = goodbye

        // prepare data to be sent
        val buf: ByteArray = udpOutputData.toByteArray()
        sendData(socket, buf, true)
    }

    private fun disconnectSocket() {
        Log.d(
            TAG,
            "End while loop: shutdown requests received on thread: ${Thread.currentThread().name}"
        )
        getSocket().disconnect()
        Log.d(TAG, "Socket disconnected")
        getSocket().close()
        Log.d(TAG, "Socket closed")
        lastReadSucceeded = false
    }


    private fun sendFilter(socket: DatagramSocket, signalNamesToUse: List<String>) {
        sendData(socket, signalHelper.clearFiltersPacket())
        signalHelper.addFilterPackets(signalNamesToUse, flipBus).forEach {
            sendData(socket, it)
        }
        // Uncomment this to send all data
        //sendData(socket, byteArrayOf(0x0C))
    }

    private fun sendData(socket: DatagramSocket, buf: ByteArray, isBye: Boolean = false) {
        // create a UDP packet with data and its destination ip & port
        val packet = DatagramPacket(buf, buf.size, serverAddress())
        Log.d(TAG, "C: Sending: '" + String(buf) + "'")

        // send the UDP packet
        try {
            socket.send(packet)
        } catch (ioException: IOException) {
            if (!isBye) {
                Log.e(TAG, "IOException while sending data.", ioException)
            }
        } catch (socketException: SocketException) {
            if (!isBye) {
                Log.e(TAG, "SocketException while sending data.", socketException)
            }
        }
    }

    private fun registerRecoverOnNewNetwork(): Closeable {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder()
            .build()

        val networkCallback = object :
            ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.d(TAG, "in network callback, on available")
                maybeRecoverFromError()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                Log.d(TAG, "in network callback, capabilities changed")
                maybeRecoverFromError()
            }
        }
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        // Allow us to be removed when not needed.
        return Closeable {
            connectivityManager.unregisterNetworkCallback(
                networkCallback
            )
        }
    }

    /** Clears an "error" state by changing it to RUNNING. No effect on a shutdown or running service. */
    private fun maybeRecoverFromError() {
        if (stateQueue.last()!! is State.Error) {
            stateQueue.add(State.Running)
        }
    }

    private fun serverAddress(): InetSocketAddress =
        InetSocketAddress(InetAddress.getByName(ipAddress()), port)

    private fun ipAddress() =
        sharedPreferences.getString(Constants.ipAddressPrefKey, Constants.ipAddressLocalNetwork)
}