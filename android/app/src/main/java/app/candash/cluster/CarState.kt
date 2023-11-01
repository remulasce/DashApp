package app.candash.cluster

import android.util.Log
import androidx.lifecycle.MutableLiveData

typealias CarState = MutableMap<String, Float?>

fun createCarState(carData: MutableMap<String, Float> = mutableMapOf()): CarState {
    return HashMap(carData)
}

typealias LiveCarState = Map<String, MutableLiveData<SignalState?>>

fun createLiveCarState(): LiveCarState {
    val liveCarState: MutableMap<String, MutableLiveData<SignalState?>> = mutableMapOf()
    // Create live data for each signal name
    SName.javaClass.declaredFields.forEach { field ->
        if (field.type == String::class.java) {
            val name = field.get(null) as String
            liveCarState[name] = MutableLiveData<SignalState?>(null)
        }
    }
    // Make it immutable
    return liveCarState.toMap()
}

fun LiveCarState.clear() {
    Log.i("LiveCarState", "Clear LiveCarState")
    this.forEach {
        it.value.postValue(null)
    }
}

/**
 * The state of each signal, including its value and staleness
 */
class SignalState(val value: Float, val timestamp: Long)