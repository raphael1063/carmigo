package com.lrlr.carmigo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import com.lrlr.carmigo.ui.StationPopup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import com.lrlr.carmigo.ui.Fading
import com.lrlr.carmigo.ui.Motion
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lrlr.carmigo.car.Gear
import com.lrlr.carmigo.core.LastDrive
import com.lrlr.carmigo.data.Books
import com.lrlr.carmigo.ui.DaysTheme
import com.lrlr.carmigo.ui.DemoPopup
import com.lrlr.carmigo.ui.DrivingScreen
import com.lrlr.carmigo.ui.ExportPopup
import com.lrlr.carmigo.ui.FarewellScreen
import com.lrlr.carmigo.ui.Ivi
import com.lrlr.carmigo.ui.LastConfirmPopup
import com.lrlr.carmigo.ui.MomentPopup
import com.lrlr.carmigo.ui.MomentsTab
import com.lrlr.carmigo.ui.NumbersTab
import com.lrlr.carmigo.ui.OnboardingScreen
import com.lrlr.carmigo.ui.ParkFirstScreen
import com.lrlr.carmigo.ui.TopBar
import com.lrlr.carmigo.ui.WipeConfirmPopup
import com.lrlr.carmigo.ui.WipedScreen

class MainActivity : ComponentActivity() {

    private enum class Overlay { NONE, LAST, EXPORT, WIPE, STATION }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readDemoExtras(intent)
    }

    /** 시연용(디버그 빌드에서만 듣는다): adb shell am start ... --ei demo_arrival_seconds 20 */
    private fun readDemoExtras(intent: Intent?) {
        val seconds = intent?.getIntExtra("demo_arrival_seconds", 0) ?: 0
        if (seconds > 0) (application as DaysApp).controller.setDemoArrivalSeconds(seconds)
    }

    override fun onStart() {
        super.onStart()
        (application as DaysApp).controller.onShown()
    }

    override fun onStop() {
        (application as DaysApp).controller.onHidden()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        (application as DaysApp).controller.checkSavedOnResume()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val c = (application as DaysApp).controller
        readDemoExtras(intent)
        // 권한 창은 띄우지 않는다. 차량 신호 권한은 설치 때 정해지고, 알림 권한은 없어도 기록에 지장이 없다.
        // (첫 실행의 권한 창이 주행 제한 화면과 겹쳐 앱을 가리는 일이 있었다)

        setContent {
            DaysTheme {
                val state by c.state.collectAsStateWithLifecycle()
                val now by c.now.collectAsStateWithLifecycle()
                val gear by c.car.gear.collectAsStateWithLifecycle()
                val odo by c.car.odometerKm.collectAsStateWithLifecycle()
                val restricted by c.ux.restricted.collectAsStateWithLifecycle()
                val sending by c.sending.collectAsStateWithLifecycle()
                val rules = c.rules

                var tab by rememberSaveable { mutableStateOf(0) }
                var overlay by rememberSaveable { mutableStateOf(Overlay.NONE) }
                var openId by rememberSaveable { mutableStateOf<String?>(null) }
                var editing by rememberSaveable { mutableStateOf(false) }
                var demoOpen by rememberSaveable { mutableStateOf(false) }
                // 알림 권한은 주차 중에 운전자가 버튼을 눌렀을 때만 묻는다(첫 실행에는 묻지 않는다)
                var notifyOn by remember { mutableStateOf(c.notifier.enabled()) }
                val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifyOn = c.notifier.enabled() }
                LifecycleResumeEffect(Unit) { notifyOn = c.notifier.enabled(); onPauseOrDispose {} }
                // 위치 권한은 "지금 위치에서 가장 가까운 곳"을 눌렀을 때만 묻는다. 허용하지 않아도 이름으로 고를 수 있다.
                var locating by remember { mutableStateOf(false) }
                fun locate() {
                    locating = true
                    c.stations.locate { st -> locating = false; if (st != null) { c.setStation(st.id); overlay = Overlay.NONE } }
                }
                val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) locate() }

                val setup = state.setup
                val driving = state.drive != null || restricted || gear == Gear.MOVING
                // 달리기 시작하면 열려 있던 창을 모두 닫는다
                LaunchedEffect(driving) { if (driving) { overlay = Overlay.NONE; openId = null; editing = false } }
                // 받아 가기 화면이 열려 있는 동안에만 휴대폰의 저장을 확인한다
                // 작별 화면에서도 확인한다(받아 가기 창을 닫은 뒤에 휴대폰에서 눌렀을 수 있다)
                val watching = overlay == Overlay.EXPORT || (state.last == LastDrive.DONE && state.drive == null)
                DisposableEffect(watching) {
                    if (watching) c.watchSaved()
                    onDispose { c.stopWatchingSaved() }
                }

                Box(Modifier.fillMaxSize().background(Ivi.colors.backgroundPrimary).safeDrawingPadding()) {
                    val wiped = state.wiped
                    when {
                        setup == null && driving -> ParkFirstScreen()
                        setup == null && wiped != null -> WipedScreen(wiped, rules, c::dismissWiped)
                        setup != null && editing && !driving && state.last != LastDrive.DONE -> {
                            BackHandler { editing = false }
                            OnboardingScreen(
                                odo ?: state.lastOdoKm, rules.date(rules.day(now)),
                                onStart = { d, km, name -> c.correctSetup(d, km, name); editing = false },
                                initialName = setup.carName,
                                initialDate = rules.date(setup.receivedDay),
                                initialKm = setup.receivedOdoKm,
                                maxDate = rules.date(setup.installedDay),
                                maxKm = setup.installedOdoKm ?: odo ?: state.lastOdoKm,
                                onCancel = { editing = false },
                            )
                        }
                        setup == null -> OnboardingScreen(odo ?: state.lastOdoKm, rules.date(rules.day(now)), c::begin, onSecret = { demoOpen = true })
                        else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                            val compact = maxWidth < 1400.dp
                            val done = state.last == LastDrive.DONE
                            Column(Modifier.fillMaxSize()) {
                                TopBar(
                                    state, rules, now,
                                    tab = if (driving || done) null else tab,
                                    onTab = { tab = it },
                                    lastEnabled = !driving && !done,
                                    onLast = { if (state.last == LastDrive.ARMED) c.disarmLast() else overlay = Overlay.LAST },
                                    compact = compact,
                                    minimal = driving,
                                    showLast = if (driving) state.last != LastDrive.DRIVING else !done,
                                    onDemo = { demoOpen = true },
                                )
                                // 화면이 바뀔 때는 짧게 겹쳐 바뀐다. 주행 화면으로 갈 때는 움직임 없이 바로 바꾼다.
                                val screen = when { driving -> 0; done -> 1; tab == 0 -> 2; else -> 3 }
                                AnimatedContent(
                                    targetState = screen,
                                    transitionSpec = {
                                        if (targetState == 0) EnterTransition.None togetherWith ExitTransition.None
                                        // 주행 화면에서 돌아올 때는 기다리지 않고 바로 다음 화면을 띄운다
                                        else if (initialState == 0) fadeIn(tween(Motion.EXIT)) togetherWith ExitTransition.None
                                        else fadeIn(tween(Motion.ENTER)) togetherWith fadeOut(tween(Motion.EXIT))
                                    },
                                    label = "screen",
                                ) { shown ->
                                    when (shown) {
                                        0 -> DrivingScreen(state, rules, now)
                                        1 -> FarewellScreen(
                                            state, rules,
                                            onExport = { c.sendBook(); overlay = Overlay.EXPORT },
                                            onUndo = { c.undoLast() },
                                            onWipe = { overlay = Overlay.WIPE },
                                            onOpen = { openId = it.id; c.markSeen(it.id) },
                                        )
                                        2 -> MomentsTab(
                                            state, rules, now,
                                            onOpen = { openId = it.id; c.markSeen(it.id) },
                                            onExport = { c.sendBook(); overlay = Overlay.EXPORT },
                                        )
                                        else -> NumbersTab(
                                            state, rules, now, onEdit = { editing = true }, notifyOn = notifyOn,
                                            onNotify = { if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                            onWeather = { overlay = Overlay.STATION },
                                        )
                                    }
                                }
                            }
                            // 달리기 시작하면 창은 움직임 없이 바로 사라진다
                            if (!driving) {
                                Fading(state.moments.firstOrNull { it.id == openId }) { m ->
                                    BackHandler(openId != null) { openId = null }
                                    MomentPopup(m, rules) { openId = null }
                                }
                                Fading(overlay.takeIf { it != Overlay.NONE }) { o ->
                                    BackHandler(overlay != Overlay.NONE) { overlay = Overlay.NONE }
                                    when (o) {
                                        Overlay.LAST -> LastConfirmPopup({ overlay = Overlay.NONE }, { c.armLast(); overlay = Overlay.NONE })
                                        Overlay.EXPORT -> ExportPopup(
                                            state, sending,
                                            url = state.export?.let { Books.url(BuildConfig.BOOK_SERVER, it) },
                                            onClose = { overlay = Overlay.NONE },
                                            onRetry = { c.sendBook() },
                                            onWipe = { overlay = Overlay.WIPE },
                                        )
                                        Overlay.WIPE -> WipeConfirmPopup({ overlay = Overlay.NONE }, { c.wipe(); overlay = Overlay.NONE })
                                        Overlay.STATION -> StationPopup(
                                            c.stations.all, state.setup?.stationId, locating = locating,
                                            onLocate = {
                                                if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate()
                                                else askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                            },
                                            onPick = { c.setStation(it); overlay = Overlay.NONE },
                                            onClose = { overlay = Overlay.NONE },
                                        )
                                        Overlay.NONE -> {}
                                    }
                                }
                            }
                        }
                    }
                    // 시연 모드 창은 첫 실행 화면에서도 열 수 있다. 달리는 중에는 열지 않는다.
                    if (demoOpen && !driving) {
                        BackHandler { demoOpen = false }
                        DemoPopup(
                            state, c.canDemoDrive(state),
                            onLoad = { c.demoLoad(); tab = 0; editing = false; overlay = Overlay.NONE },
                            onDrive = { c.demoDrive(); demoOpen = false },
                            onEnd = { c.demoEnd(); demoOpen = false; overlay = Overlay.NONE },
                            onClose = { demoOpen = false },
                        )
                    }
                }
            }
        }
    }
}
