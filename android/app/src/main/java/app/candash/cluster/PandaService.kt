package app.candash.cluster

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.IOException
import java.net.*
import java.util.concurrent.Executors


@ExperimentalCoroutinesApi
class PandaService(val sharedPreferences: SharedPreferences, val context: Context) :
    CANService {
    private val TAG = PandaService::class.java.simpleName

    @ExperimentalCoroutinesApi
    private var flipBus = false
    private val carStateFlow = MutableStateFlow(CarState())
    private val carState: CarState = CarState()
    private val port = 1338
    private var shutdown = false
    private var inShutdown = false
    private val heartbeat = "ehllo"
    private val goodbye = "bye"
    private var lastHeartbeatTimestamp = 0L
    private val heartBeatIntervalMs = 4_000
    private val socketTimeoutMs = 1_000
    private val signalHelper = CANSignalHelper()
    private val pandaContext = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var signalsToRequest: List<String> = arrayListOf()
    private var recentSignalsReceived: MutableSet<String> = mutableSetOf()
    private var lastReceivedCheckTimestamp = 0L
    private val signalsReceivedCheckIntervalMs = 5_000
    private lateinit var socket: DatagramSocket
    private var pandaConnected = false






@ExperimentalCoroutinesApi
    override fun carState(): Flow<CarState> {
        return carStateFlow
    }

    private fun getSocket(): DatagramSocket {
        if (!this::socket.isInitialized) {
            socket = DatagramSocket(null)
            socket.soTimeout = heartBeatIntervalMs
            socket.reuseAddress = true
        }
        return socket
    }

    private fun createSocket(): DatagramSocket {
        socket = DatagramSocket(null)
        socket.soTimeout = heartBeatIntervalMs
        socket.reuseAddress = true
        return socket
    }

    private fun twosComplement(s: String): Long {
        if (s[0].equals('0'))
            return s.toLong(radix = 2)
        var seenOne: Boolean = false
        val chars = s.toCharArray()
        for (i in s.length - 1 downTo 0) {
            if (seenOne == false) {
                if (chars[i].equals('1')) {
                    seenOne = true;
                }
            } else {
                if (chars[i].equals('1')) {
                    chars[i] = '0'
                } else {
                    chars[i] = '1'
                }
            }
        }
        return (String(chars).toLong(radix = 2)) * -1
    }

    override fun isRunning() : Boolean {
        return !shutdown
    }

    override fun getType(): CANServiceType {
        return CANServiceType.PANDA
    }

    @ExperimentalCoroutinesApi
    override suspend fun startRequests(signalNamesToRequest: List<String>) {

        withContext(pandaContext) {
            signalsToRequest = signalNamesToRequest
            Log.d(TAG, "Starting requests on thread: ${Thread.currentThread().name}")
            shutdown = false
            try {

                Log.d(TAG, "Sending heartbeat on thread: ${Thread.currentThread().name}")
                sendHello(createSocket())

                while (!shutdown) {
                    val now = System.currentTimeMillis()
                    if (now > (lastHeartbeatTimestamp + heartBeatIntervalMs)) {
                        Log.d(TAG, "Sending heartbeat on thread: ${Thread.currentThread().name}")
                        sendHello(getSocket())
                    }
                    // Warning for missing signals
                    if (pandaConnected && now > lastReceivedCheckTimestamp + signalsReceivedCheckIntervalMs) {
                        val deltaSeconds = ((now-lastReceivedCheckTimestamp) / 1000).toInt()
                        for (name in signalHelper.getALLCANSignals().keys){
                            if (!recentSignalsReceived.contains(name)){
                                Log.w(
                                    TAG,"Did not receive signal '$name' in the last $deltaSeconds seconds"
                                )
                                carState.carData.remove(name)
                                carStateFlow.value = CarState(HashMap(carState.carData))
                            }
                        }
                        recentSignalsReceived.clear()
                        lastReceivedCheckTimestamp = now
                    }
                    // up to 512 frames which are 16 bytes each
                    val buf = ByteArray(16 * 512)
                    val packet = DatagramPacket(buf, buf.size, serverAddress())
                    Log.d(TAG, "C: Waiting to receive... on thread: ${Thread.currentThread().name}")

                    try {
                        getSocket().receive(packet)
                        if (!pandaConnected) {
                            recentSignalsReceived.clear()
                            lastReceivedCheckTimestamp = System.currentTimeMillis()
                            pandaConnected = true
                        }
                    } catch (socketTimeoutException: SocketTimeoutException) {
                        Log.w(
                            TAG,
                            "Socket timed out without receiving a packet on thread: ${Thread.currentThread().name}"
                        )
                        pandaConnected = false
                        sendBye(getSocket())
                        carState.carData.clear()
                        carStateFlow.value = CarState(HashMap(carState.carData))
                        yield()
                        continue
                    }

                    //Log.d(TAG, "Packet from: " + packet.address + ":" + packet.port)
                    for (i in buf.indices step 16){

                        val newPandaFrame = NewPandaFrame(buf.sliceArray(i..i+15))
                        // Log.d(TAG, "bufindex = " + i.toString()+ " pandaFrame :" + newPandaFrame.frameId.toString())
                        if (newPandaFrame.frameId == 0L){
                            break
                        } else if (newPandaFrame.frameId == 6L && newPandaFrame.busId == 15L) {
                            // It's an ack
                            sendFilter(getSocket(), signalsToRequest)
                        } else {
                            handleFrame(newPandaFrame)
                        }
                    }

                    /*
                    Log.d(TAG, "Binary = " + buf.getPayloadBinaryString())
                    Log.d(TAG, "FrameId = " + newPandaFrame.frameIdHex.hexString)
                    Log.d(TAG, "BusId = " + newPandaFrame.busId)
                    Log.d(TAG, "FrameLength = " + newPandaFrame.frameLength)
                     */

                    yield()
                }
                pandaConnected = false
                sendBye(getSocket())
                Log.d(
                    TAG,
                    "End while loop: shutdown requests received on thread: ${Thread.currentThread().name}"
                )
                getSocket().disconnect()
                Log.d(TAG, "Socket disconnected")
                getSocket().close()
                Log.d(TAG, "Socket closed")
                carState.carData.clear()
                carStateFlow.value = CarState(HashMap(carState.carData))
                inShutdown = false
            } catch (exception: Exception) {
                inShutdown = false
                Log.e(TAG, "Exception while sending or receiving data", exception)
            }
        }
        Log.d(TAG, "Stopping requests on thread: ${Thread.currentThread().name}")
    }

    private fun handleFrame(frame: NewPandaFrame) {
        var binaryPayloadString = ""
        val updateState = CarState(HashMap())
        // 0x399 is a different length on each bus, so we use it to auto-detect the buses
        // Length of 3 only on vehicle bus
        if (frame.frameIdHex == Hex(0x399) && (frame.frameLength == 3L)){
            if (frame.busId.toInt() == Constants.vehicleBus && flipBus){
                // chassis bus = 0, vehicle bus = 1, don't flip
                Log.i(TAG, "chassis bus = 0, vehicle bus = 1; un-flipping bus IDs")
                flipBus = false
                sendFilter(getSocket(), signalsToRequest)
                return
            }else if (frame.busId.toInt() == Constants.chassisBus && !flipBus){
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

        signalHelper.getSignalsForFrame(busId, frame.frameIdHex).forEach { signal ->
            if (frame.getCANValue(signal) != null){
                carState.updateValue(signal.name, frame.getCANValue(signal)!!)
                carStateFlow.value = CarState(HashMap(carState.carData))
                recentSignalsReceived.add(signal.name)
            }
        }
    }

    override suspend fun shutdown() {
        Log.d(TAG, "in shutdown on thread: ${Thread.currentThread().name}")
        withContext(pandaContext) {
            inShutdown = true
            shutdown = true
            Log.d(TAG, "shutdown true on thread: ${Thread.currentThread().name}")
        }
    }

    private fun sendHello(socket: DatagramSocket) {
        // prepare data to be sent
        val udpOutputData = heartbeat

        // prepare data to be sent
        val buf: ByteArray = udpOutputData.toByteArray()

        sendData(socket, buf)
        lastHeartbeatTimestamp = System.currentTimeMillis()
    }

    private fun sendBye(socket: DatagramSocket) {
        // prepare data to be sent
        val udpOutputData = goodbye

        // prepare data to be sent
        val buf: ByteArray = udpOutputData.toByteArray()

        sendData(socket, buf, true)
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
            Log.e(TAG, "IOException while sending data.", ioException)
            if (!isBye) checkNetwork()
        } catch (socketException: SocketException) {
            Log.e(TAG, "SocketException while sending data.", socketException)
            if (!isBye) checkNetwork()
        }
    }

    private fun checkNetwork() {
        //val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        CoroutineScope(pandaContext).launch {
            restartLater()

        }
    }


    private suspend fun restartLater() {
        Log.d(TAG, "in restartLater on thread: ${Thread.currentThread().name}")
        withContext(pandaContext) {
            shutdown()
            Log.d(TAG, "in restartLater after shutdown on thread: ${Thread.currentThread().name}")
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkRequest = NetworkRequest.Builder()
                .build()
            connectivityManager.registerNetworkCallback(networkRequest, object :
                ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.d(TAG, "in network callback, on available")
                    doRestart()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    super.onCapabilitiesChanged(network, networkCapabilities)
                    Log.d(TAG, "in network callback, capabilities changed")
                    doRestart()

                }
            })
        }
    }

    private fun doRestart() {
        CoroutineScope(pandaContext).launch {
            Log.d(
                TAG,
                "in doRestart, restarting requests on thread: ${Thread.currentThread().name}"
            )
            startRequests(signalsToRequest)
        }
    }

    private fun serverAddress(): InetSocketAddress =
        InetSocketAddress(InetAddress.getByName(ipAddress()), port)

    private fun ipAddress() =
        sharedPreferences.getString(Constants.ipAddressPrefKey, Constants.ipAddressLocalNetwork)
}