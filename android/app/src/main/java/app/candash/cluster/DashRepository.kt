package app.candash.cluster

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

class DashRepository @ExperimentalCoroutinesApi
@Inject constructor(private val canServiceFactory: CANServiceFactory) {

    private fun getCANService() : CANService {
        return canServiceFactory.getService()
    }

    fun getCANServiceType() : CANServiceType {
        return canServiceFactory.getService().getType()
    }

    fun setCANServiceType(type: CANServiceType) {
        canServiceFactory.setServiceType(type)
    }

    fun startRequests(lifetime: CoroutineScope) {
        getCANService().startRequests(lifetime)
    }

    fun isRunning() : Boolean {
        return getCANService().isRunning()
    }

    fun shutdown() {
        getCANService().shutdown()
    }

    fun restart() {
        getCANService().restart()
    }

    fun clearCarState() {
        getCANService().clearCarState()
    }

    fun carState() : CarState {
        return getCANService().carState()
    }

    fun liveCarState() : LiveCarState {
        return getCANService().liveCarState();
    }

    fun carStateTimestamp() : CarStateTimestamp {
        return getCANService().carStateTimestamp()
    }
}