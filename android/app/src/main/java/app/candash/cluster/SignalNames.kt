package app.candash.cluster

typealias SignalName = String

object SName {
    const val accActive: SignalName = "accActive"
    const val accSpeedLimit: SignalName = "accSpeedLimit"
    const val accState: SignalName = "accState"
    const val autoHighBeamEnabled: SignalName = "autoHighBeamEnabled"
    const val autopilotHands: SignalName = "AutopilotHands"
    const val autopilotState: SignalName = "AutopilotState"
    const val battAmps: SignalName = "BattAmps"
    const val battBrickMin: SignalName = "battBrickMin"
    const val battVolts: SignalName = "BattVolts"
    const val blindSpotLeft: SignalName = "BSL"
    const val blindSpotRight: SignalName = "BSR"
    const val brakeApplied: SignalName = "brakeApplied"
    const val brakeHold: SignalName = "brakeHold"
    const val brakePark: SignalName = "brakePark"
    const val brakeTempFL: SignalName = "brakeTempFL"
    const val brakeTempFR: SignalName = "brakeTempFR"
    const val brakeTempRL: SignalName = "brakeTempRL"
    const val brakeTempRR: SignalName = "brakeTempRR"
    const val chargeStatus: SignalName = "chargeStatus"
    const val conditionalSpeedLimit: SignalName = "conditionalSpeedLimit"
    const val coolantFlow: SignalName = "coolantFlow"
    const val courtesyLightRequest: SignalName = "courtesyLightRequest"
    const val cruiseControlSpeed: SignalName = "CruiseControlSpeed"
    const val dc12vAmps: SignalName = "dc12vAmps"
    const val dc12vPower: SignalName = "dc12vPower"
    const val dc12vVolts: SignalName = "dc12vVolts"
    const val displayBrightnessLev: SignalName = "displayBrightnessLev"
    const val displayOn: SignalName = "displayOn"
    const val driveConfig: SignalName = "driveConfig"
    const val driverOrientation: SignalName = "driverOrientation"
    const val driverUnbuckled: SignalName = "driverUnbuckled"
    const val forwardCollisionWarning: SignalName = "FCW"
    const val frontFogStatus: SignalName = "frontFogStatus"
    const val frontLeftDoorState: SignalName = "FrontLeftDoor"
    const val frontOccupancy: SignalName = "frontOccupancy"
    const val frontRightDoorState: SignalName = "FrontRightDoor"
    const val frontTemp: SignalName = "frontTemp"
    const val frontTorque: SignalName = "frontTorque"
    const val frunkState: SignalName = "frunkState"
    const val fusedSpeedLimit: SignalName = "fusedSpeedLimit"
    const val gearSelected: SignalName = "GearSelected"
    const val heatBattery: SignalName = "heatBattery"
    const val highBeamRequest: SignalName = "highBeamRequest"
    const val highBeamStatus: SignalName = "highBeamStatus"
    const val hvacAirDistribution: SignalName = "hvacAirDistribution"
    const val insideTemp: SignalName = "insideTemp"
    const val insideTempReq: SignalName = "insideTempReq"
    const val isSunUp: SignalName = "isSunUp"
    const val keepClimateReq: SignalName = "keepClimateReq"
    const val kwhChargeTotal: SignalName = "kwhChargeTotal"
    const val kwhDischargeTotal: SignalName = "kwhDischargeTotal"
    const val leftVehicle: SignalName = "leftVehDetected"
    const val liftgateState: SignalName = "liftgateState"
    const val lightingState: SignalName = "lightingState"
    const val lightSwitch: SignalName = "lightSwitch"
    const val limRegen: SignalName = "limRegen"
    const val lockStatus: SignalName = "lockStatus"
    const val mapRegion: SignalName = "mapRegion"
    const val maxDischargePower: SignalName = "maxDischargePower"
    const val maxHeatPower: SignalName = "maxHeatPower"
    const val maxRegenPower: SignalName = "maxRegenPower"
    const val maxSpeedAP: SignalName = "maxSpeedAP"
    const val nominalFullPackEnergy: SignalName = "nominalFullPackEnergy"
    const val odometer: SignalName = "odometer"
    const val outsideTemp: SignalName = "outsideTemp"
    const val partyHoursLeft: SignalName = "partyHoursLeft"
    const val passengerUnbuckled: SignalName = "passengerUnbuckled"
    const val PINenabled: SignalName = "PINenabled"
    const val PINpassed: SignalName = "PINpassed"
    const val rearFogStatus: SignalName = "rearFogStatus"
    const val rearLeftDoorState: SignalName = "RearLeftDoor"
    const val rearLeftVehicle: SignalName = "rearLeftVehDetected"
    const val rearRightDoorState: SignalName = "RearRightDoor"
    const val rearRightVehicle: SignalName = "rearRightVehDetected"
    const val rearTemp: SignalName = "rearTemp"
    const val rearTorque: SignalName = "rearTorque"
    const val rightVehicle: SignalName = "rightVehDetected"
    const val slowPower: SignalName = "slowPower"
    const val stateOfCharge: SignalName = "UI_SOC"
    const val steeringAngle: SignalName = "SteeringAngle"
    const val tpmsHard: SignalName = "tpmsHard"
    const val tpmsSoft: SignalName = "tpmsSoft"
    const val turnSignalLeft: SignalName = "VCFRONT_indicatorLef"
    const val turnSignalRight: SignalName = "VCFRONT_indicatorRig"
    const val uiRange: SignalName = "UI_Range"
    const val uiSpeed: SignalName = "UISpeed"
    const val uiSpeedHighSpeed: SignalName = "UISpeedHighSpeed"
    const val uiSpeedTestBus0: SignalName = "uiSpeedTestBus0"
    const val uiSpeedUnits: SignalName = "UISpeedUnits"
    const val vehicleSpeed: SignalName = "Vehicle Speed"

    // Augmented signals:
    const val power: SignalName = "power"
    const val smoothBattAmps: SignalName = "smoothBattAmps"
    const val l1Distance: SignalName = "l1Distance"
    const val l2Distance: SignalName = "l2Distance"

    // CANServer debug signals:
    const val canServerAck: SignalName = "CANServer_Ehllo_Ack"
    const val canServerPandaConnection: SignalName = "CANServer_PandaConnection"
}

object SGroup {
    val closures = listOf(
        SName.frontLeftDoorState,
        SName.frontRightDoorState,
        SName.rearLeftDoorState,
        SName.rearRightDoorState,
        SName.liftgateState,
        SName.frunkState
    )

    val lights = listOf(
        SName.lightingState,
        SName.lightSwitch,
        SName.highBeamRequest,
        SName.highBeamStatus,
        SName.autoHighBeamEnabled,
        SName.frontFogStatus,
        SName.rearFogStatus,
        SName.courtesyLightRequest,
        SName.mapRegion
    )
}