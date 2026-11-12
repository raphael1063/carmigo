package com.lrlr.carmigo.car

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.EvBatteryLevelListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import com.lrlr.carmigo.BuildConfig
import com.lrlr.carmigo.core.Seats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 기어. 앱은 P인지 아닌지만 본다. */
enum class Gear { UNKNOWN, PARK, MOVING }

/**
 * 앱이 읽는 차량 신호. 전부 읽기 전용이다(차량 제어 없음).
 * 받지 못한 값은 null로 두고, 화면에는 적지 않는다.
 */
interface CarFeed {
    val gear: StateFlow<Gear>
    val odometerKm: StateFlow<Double?>
    val seats: StateFlow<Seats>
    /** 배터리의 남은 에너지(Wh) */
    val batteryWh: StateFlow<Double?>
    fun start()
    fun stop()
    /** 달리기 시작할 때 부른다: 운전석이 비어 있다고 읽히면 착석 값의 방향을 뒤집는다. */
    fun calibrateSeats()
    /** 바깥 기온(섭씨). 못 읽으면 null */
    fun outsideTempC(): Float?
}

class PleosCarFeed(context: Context) : CarFeed {
    private val app = context.applicationContext
    private val vehicle = Vehicle(app)
    private var started = false
    private var car: Car? = null
    private var props: CarPropertyManager? = null

    override val gear = MutableStateFlow(Gear.UNKNOWN)
    override val odometerKm = MutableStateFlow<Double?>(null)
    override val seats = MutableStateFlow(Seats())
    override val batteryWh = MutableStateFlow<Double?>(null)

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(g: VehicleGear?) { gear.value = mapGear(g) }
        override fun onFailed(e: Exception) { Log.w(TAG, "gear subscription failed", e) }
    }

    private val odoListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(km: Float?) { setOdo(km) }
        override fun onFailed(e: Exception) { Log.w(TAG, "odometer subscription failed", e) }
    }

    private val batteryListener = object : EvBatteryLevelListener {
        override fun onEvBatteryLevelUpdated(wh: Float?) { setBattery(wh) }
        override fun onFailed(e: Exception) { Log.w(TAG, "battery subscription failed", e) }
    }

    private class SeatSlot(val value: SeatValue, val set: (Seats, Boolean?) -> Seats)

    private val slots = listOf(
        SeatSlot(SeatValue.ROW_1_LEFT) { s, v -> s.copy(driver = v) },
        SeatSlot(SeatValue.ROW_1_RIGHT) { s, v -> s.copy(passenger = v) },
        SeatSlot(SeatValue.ROW_2_LEFT) { s, v -> s.copy(rearLeft = v) },
        SeatSlot(SeatValue.ROW_2_RIGHT) { s, v -> s.copy(rearRight = v) },
    )
    private val seatListeners = slots.associateWith { slot ->
        object : SeatOccupancyListener {
            override fun onSeatOccupancyUpdated(occupied: Boolean?) { setSeat(slot, occupied) }
            override fun onFailed(e: Exception) { Log.w(TAG, "seat ${slot.value} subscription failed", e) }
        }
    }

    /** SDK가 준 값 그대로. 방향이 바뀌면 여기서 다시 계산한다. */
    private val raw = HashMap<SeatSlot, Boolean?>()
    // 에뮬레이터 2.1.0에서는 착석 값이 반대로 온다. 기본 방향은 BuildConfig로 정하고, 달리기 시작할 때 운전석으로 확인한다.
    private var invert = BuildConfig.INVERT_SEAT_OCCUPANCY

    @Synchronized
    private fun setSeat(slot: SeatSlot, value: Boolean?) {
        raw[slot] = value
        publishSeats()
    }

    /** 방향은 켜져 있는 동안 한 번만 바꾼다(운전석 값이 잠깐 튀어도 주행마다 뒤집히지 않게). */
    private var flipped = false

    private fun read(): Seats = slots.fold(Seats()) { s, slot -> slot.set(s, raw[slot]?.let { if (invert) !it else it }) }

    /**
     * 달리는 동안 운전석은 반드시 차 있다. 그렇지 않게 읽히면 착석 값의 방향을 한 번 바로잡는다.
     * 운전석이 찬 것으로 읽힐 때에만 좌석 값을 내보낸다. 방향을 믿을 수 없을 때는 "못 읽음"으로 둔다
     * (빈 자리를 사람이 탄 것으로 적는 것보다 적지 않는 편이 낫다).
     */
    private fun publishSeats() {
        val now = read()
        seats.value = if (now.driver == true) now else Seats()
    }

    /** 기어가 P를 벗어나는 순간에만 방향을 확인한다. 주행 중에 운전석 값이 한 번 튀어도 뒤집지 않는다. */
    @Synchronized
    override fun calibrateSeats() {
        if (read().driver == false && !flipped) {
            Log.i(TAG, "driver seat read empty when leaving P; flipping seat occupancy direction once")
            invert = !invert
            flipped = true
        }
        publishSeats()
    }

    private fun setOdo(km: Float?) {
        if (km != null && !km.isNaN() && km >= 0f) odometerKm.value = round2(km)
    }

    private fun setBattery(wh: Float?) {
        if (wh != null && !wh.isNaN() && wh >= 0f) batteryWh.value = wh.toDouble()
    }

    override fun start() {
        if (started) return
        try {
            vehicle.initialize()
            started = true
        } catch (t: Throwable) {
            Log.w(TAG, "vehicle init failed", t)
            return
        }
        safe("gear") {
            vehicle.getGear().getCurrentGearSelectionState({ gear.value = mapGear(it) }, {})
            vehicle.getGear().registerGearSelection(gearListener)
        }
        safe("odometer") {
            vehicle.getOdometer().getCurrentDistanceDriven({ setOdo(it) }, {})
            vehicle.getOdometer().registerDistanceDriven(odoListener)
        }
        safe("battery") {
            vehicle.getEvBattery().getEvBatteryLevel({ setBattery(it) }, {})
            vehicle.getEvBattery().registerEvBatteryLevel(batteryListener)
        }
        slots.forEach { slot ->
            safe("seat ${slot.value}") {
                val area = SeatArea(slot.value)
                vehicle.getSeat().getCurrentSeatOccupancy(area, { setSeat(slot, it) }, {})
                vehicle.getSeat().registerSeatOccupancy(area, seatListeners.getValue(slot))
            }
        }
        try {
            val c = Car.createCar(app)
            car = c
            props = c?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        } catch (t: Throwable) {
            Log.i(TAG, "android.car unavailable: ${t.message}")
        }
    }

    override fun stop() {
        if (!started) return
        safe("unregister") {
            vehicle.getGear().unregisterGearSelection(gearListener)
            vehicle.getOdometer().unregisterDistanceDriven(odoListener)
            vehicle.getEvBattery().unregisterEvBatteryLevel(batteryListener)
            slots.forEach { vehicle.getSeat().unregisterSeatOccupancy(SeatArea(it.value), seatListeners.getValue(it)) }
        }
        safe("release") { vehicle.release() }
        try { car?.disconnect() } catch (_: Throwable) {}
        car = null
        props = null
        started = false
    }

    override fun outsideTempC(): Float? = try {
        props?.getFloatProperty(VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE, 0)?.takeIf { !it.isNaN() && it in -60f..70f }
    } catch (t: Throwable) {
        null
    }

    private inline fun safe(what: String, block: () -> Unit) {
        try { block() } catch (t: Throwable) { Log.w(TAG, "$what failed", t) }
    }

    private fun mapGear(g: VehicleGear?): Gear = when (g) {
        null, VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
        VehicleGear.GEAR_PARK -> Gear.PARK
        else -> Gear.MOVING
    }

    private companion object {
        const val TAG = "DaysCar"
        /** Float로 온 km의 꼬리 오차를 지운다(12345.67 → 12345.67) */
        fun round2(km: Float): Double = Math.round(km.toDouble() * 100) / 100.0
    }
}
