package app.candash.cluster

import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext

/**
 * To create a different CANService, add your new service to the CANServiceType, create a new
 * class that implements CANService, and add the new class to the CANServiceFactory.
 */
interface CANService {
    fun startRequests(lifetime: CoroutineScope)
    fun shutdown()
    fun restart()
    fun clearCarState()
    fun carState() : CarState
    fun carStateTimestamp() : CarStateTimestamp
    fun liveCarState() : LiveCarState
    fun isRunning() : Boolean
    fun getType() : CANServiceType
}

enum class CANServiceType(val nameResId: Int) {
    MOCK(R.string.mock_server),
    PANDA(R.string.can_server)
}