package com.example.newapp

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.*
import java.util.Calendar

// --- 1. 数据模型 ---
@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginResponse(val success: Boolean, val token: String? = null, val message: String? = null)

@Serializable
data class CategoryScore(
    val category: String,
    val base_score: Float? = null,
    val manual_score: Float? = null,
    val final_score: Float? = null
)

@Serializable
data class LabelI18n(
    val zh: String? = null,
    val en: String? = null
)

@Serializable
data class TargetConfig(
    val label_i18n: LabelI18n? = null
)

@Serializable
data class MetricSchema(
    val source_title: String? = null,
    val main_metric_label: String? = null,
    val is_sub_metric: Boolean = false,
    val target_config: TargetConfig? = null
)

@Serializable
data class Metric(
    val id: Int? = null,
    val metric_label: String,
    val category: String,
    val target_value: String? = null,
    val raw_value: String? = null,
    val numeric_value: Float? = null,
    val is_failing: Boolean = false,
    val gap: String? = null,
    val weight: Float? = null,
    val earned_score: Float? = null,
    val schema: MetricSchema? = null,
    val is_derived_overall: Boolean? = null
)

@Serializable
data class Snapshot(
    val snapshot_id: String,
    val month: Int? = null,
    val created_at: String,
    val standard_total_score: Float? = null,
    val image_path: String? = null,
    val excel_path: String? = null
)

@Serializable
data class SlaStatus(
    val raw_text: String? = null,
    val remaining_text: String? = null,
    val overdue_text: String? = null,
    val consume_percent: Float? = null,
    val sla_days: Int? = null,
    val status_label: String? = null,
    val is_overdue: Boolean = false
)

@Serializable
data class ExpiringTicket(
    val collection: String,
    val collection_label_i18n: LabelI18n? = null,
    val display_collection_label: String? = null,
    val title: String? = null,
    val title_i18n: LabelI18n? = null,
    val display_title: String? = null,
    val ticket_id: String,
    val network_name: String? = null,
    val status: String? = null,
    val owner: String? = null,
    val product_line: String? = null,
    val product: String? = null,
    val customer_name: String? = null,
    val due_date: String? = null,
    val sla_days: Int? = null,
    val urgency: String? = null,
    val sla_text: String? = null,
    val sla_text_i18n: LabelI18n? = null,
    val display_sla_text: String? = null,
    val sla_status: SlaStatus? = null,
    val raw: JsonElement? = null
)

@Serializable
data class AlertData(
    val expiring_tickets: List<ExpiringTicket> = emptyList()
)

@Serializable
data class AlertsResponse(
    val snapshot: Snapshot? = null,
    val expiring_ticket_count: Int = 0,
    val special_metric_alert_count: Int = 0,
    val expiring_tickets: List<ExpiringTicket> = emptyList()
)

@Serializable
data class SummaryResponse(
    val snapshot_count: Int = 0,
    val metric_count: Int = 0,
    val failing_metric_count: Int = 0,
    val compliance_rate: Float? = null,
    val first_snapshot_at: String? = null,
    val latest_snapshot_at: String? = null,
    val latest_snapshot: Snapshot? = null,
    val latest_metrics_total: Int = 0,
    val latest_category_scores: List<CategoryScore> = emptyList(),
    val latest_failing_metrics: List<Metric> = emptyList(),
    val latest_expiring_ticket_count: Int = 0,
    val latest_special_metric_alert_count: Int = 0,
    val latest_alerts: AlertData? = null
)

@Serializable
data class TrendPoint(
    val snapshot_id: String,
    val month: Int,
    val snapshot_created_at: String,
    val stored_at: String? = null,
    val category: String,
    val metric_label: String,
    val display_metric_label: String? = null,
    val target_value: String? = null,
    val raw_value: String? = null,
    val numeric_value: Float? = null,
    val is_failing: Boolean,
    val gap: String? = null
)

@Serializable
data class TrendResponse(
    val point_count: Int,
    val points: List<TrendPoint>
)

@Serializable
data class SnapshotsResponse(val items: List<Snapshot>)

@Serializable
data class SnapshotDetailResponse(
    val snapshot: Snapshot,
    val category_scores: List<CategoryScore> = emptyList(),
    val metrics: List<Metric> = emptyList()
)

@Serializable
data class MetricsListResponse(
    val items: List<Metric>
)

// --- 2. API 接口定义 ---
interface MetricsApiService {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("api/external/metrics/summary")
    suspend fun getSummary(
        @Header("Authorization") token: String,
        @Query("month") month: Int,
        @Query("include_overall") includeOverall: Boolean? = true
    ): SummaryResponse

    @GET("api/external/metrics/snapshots")
    suspend fun getSnapshots(
        @Header("Authorization") token: String,
        @Query("month") month: Int,
        @Query("limit") limit: Int = 20
    ): SnapshotsResponse

    @GET("api/external/metrics/snapshots/{snapshot_id}")
    suspend fun getSnapshotDetail(
        @Header("Authorization") token: String,
        @Path("snapshot_id") snapshotId: String,
        @Query("month") month: Int,
        @Query("include_overall") includeOverall: Boolean? = true
    ): SnapshotDetailResponse

    @GET("api/external/metrics")
    suspend fun getMetrics(
        @Header("Authorization") token: String,
        @Query("month") month: Int,
        @Query("category") category: String? = null,
        @Query("snapshot_id") snapshotId: String? = null,
        @Query("include_overall") includeOverall: Boolean? = true,
        @Query("limit") limit: Int = 2000
    ): MetricsListResponse

    @GET("api/external/metrics/failing")
    suspend fun getFailingMetrics(
        @Header("Authorization") token: String,
        @Query("month") month: Int,
        @Query("snapshot_id") snapshotId: String? = null,
        @Query("limit") limit: Int = 2000
    ): MetricsListResponse

    @GET("api/external/metrics/alerts")
    suspend fun getAlerts(
        @Header("Authorization") token: String,
        @Query("month") month: Int
    ): AlertsResponse

    @GET("api/external/metrics/trend")
    suspend fun getTrend(
        @Header("Authorization") token: String,
        @Query("metric_label") metricLabel: String,
        @Query("category") category: String,
        @Query("days") days: Int = 30,
        @Query("lang") lang: String
    ): TrendResponse
}

object NetworkModule {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder().build()
    
    val service: MetricsApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://cs.fanxiaolong.uk/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MetricsApiService::class.java)
    }
}

// --- 3. 页面路由与状态 ---
sealed class Screen {
    object Login : Screen()
    object Dashboard : Screen()
    data class SnapshotDetail(val snapshotId: String, val month: Int) : Screen()
    data class MetricsList(val title: String, val filterType: String, val month: Int, val snapshotId: String?, val category: String? = null) : Screen()
    data class AlertsList(val month: Int) : Screen()
    data class MetricTrend(val metricLabel: String, val category: String, val previousScreen: Screen? = null) : Screen()
}

@Composable
fun AppNavigation() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Login) }
    var token by remember { mutableStateOf<String?>(null) }
    var dashboardMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH) + 1) }
    var language by remember { mutableStateOf("zh") }
    
    BackHandler(enabled = currentScreen != Screen.Login && currentScreen != Screen.Dashboard) {
        if (currentScreen is Screen.MetricTrend && (currentScreen as Screen.MetricTrend).previousScreen != null) {
            currentScreen = (currentScreen as Screen.MetricTrend).previousScreen!!
        } else {
            currentScreen = Screen.Dashboard
        }
    }

    if (token == null) {
        LoginScreen(onLoginSuccess = { 
            token = it
            currentScreen = Screen.Dashboard 
        })
    } else {
        when (val screen = currentScreen) {
            is Screen.Login -> currentScreen = Screen.Dashboard
            is Screen.Dashboard -> DashboardScreen(
                token = token!!,
                selectedMonth = dashboardMonth,
                language = language,
                onLanguageChange = { language = it },
                onMonthSelected = { dashboardMonth = it },
                onNavigateToSnapshot = { id, m -> currentScreen = Screen.SnapshotDetail(id, m) },
                onNavigateToMetrics = { title, filterType, m, sid, cat -> currentScreen = Screen.MetricsList(title, filterType, m, sid, cat) },
                onNavigateToAlerts = { m -> currentScreen = Screen.AlertsList(m) },
                onNavigateToTrend = { label, cat -> currentScreen = Screen.MetricTrend(label, cat, currentScreen) }
            )
            is Screen.SnapshotDetail -> SnapshotDetailScreen(
                token = token!!,
                snapshotId = screen.snapshotId,
                month = screen.month,
                language = language,
                onBack = { currentScreen = Screen.Dashboard },
                onNavigateToTrend = { label, cat -> currentScreen = Screen.MetricTrend(label, cat, currentScreen) }
            )
            is Screen.MetricsList -> MetricsListScreen(
                token = token!!,
                title = screen.title,
                filterType = screen.filterType,
                month = screen.month,
                snapshotId = screen.snapshotId,
                category = screen.category,
                language = language,
                onBack = { currentScreen = Screen.Dashboard },
                onNavigateToTrend = { label, cat -> currentScreen = Screen.MetricTrend(label, cat, currentScreen) }
            )
            is Screen.AlertsList -> AlertsListScreen(
                token = token!!,
                month = screen.month,
                language = language,
                onBack = { currentScreen = Screen.Dashboard }
            )
            is Screen.MetricTrend -> MetricTrendScreen(
                token = token!!,
                metricLabel = screen.metricLabel,
                category = screen.category,
                language = language,
                onBack = { 
                    if (screen.previousScreen != null) {
                        currentScreen = screen.previousScreen
                    } else {
                        currentScreen = Screen.Dashboard 
                    }
                }
            )
        }
    }
}

// --- 4. 登录界面 ---
@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
    
    var rememberMe by remember { mutableStateOf(prefs.getBoolean("remember_me", false)) }
    var autoLogin by remember { mutableStateOf(prefs.getBoolean("auto_login", false)) }
    var username by remember { mutableStateOf(if (rememberMe) prefs.getString("username", "admin") ?: "admin" else "admin") }
    var password by remember { mutableStateOf(if (rememberMe) prefs.getString("password", "") ?: "" else "") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    val doLogin = {
        scope.launch {
            loading = true
            error = null
            try {
                val res = NetworkModule.service.login(LoginRequest(username, password))
                if (res.success && res.token != null) {
                    val editor = prefs.edit()
                    editor.putBoolean("remember_me", rememberMe)
                    editor.putBoolean("auto_login", autoLogin)
                    if (rememberMe) {
                        editor.putString("username", username)
                        editor.putString("password", password)
                    } else {
                        editor.remove("username")
                        editor.remove("password")
                    }
                    editor.apply()
                    onLoginSuccess(res.token)
                } else {
                    error = res.message ?: "Invalid Credentials"
                    autoLogin = false
                    prefs.edit().putBoolean("auto_login", false).apply()
                }
            } catch (e: Exception) {
                error = "Connection failed: ${e.localizedMessage}"
                autoLogin = false
                prefs.edit().putBoolean("auto_login", false).apply()
            } finally { loading = false }
        }
    }

    LaunchedEffect(Unit) {
        if (autoLogin && username.isNotEmpty() && password.isNotEmpty()) {
            doLogin()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A0E29), Color(0xFF161B3D)))),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color.Cyan, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text("TOOLS PLATFORM", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Spacer(Modifier.height(32.dp))
                
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                if (error != null) Text(error!!, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rememberMe, 
                            onCheckedChange = { rememberMe = it; if (!it) autoLogin = false },
                            colors = CheckboxDefaults.colors(checkedColor = Color.Cyan, checkmarkColor = Color(0xFF0A0E29))
                        )
                        Text("Remember Me", color = Color.LightGray, fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = autoLogin, 
                            onCheckedChange = { autoLogin = it; if (it) rememberMe = true },
                            colors = CheckboxDefaults.colors(checkedColor = Color.Cyan, checkmarkColor = Color(0xFF0A0E29))
                        )
                        Text("Auto Login", color = Color.LightGray, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = { doLogin() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    enabled = !loading
                ) {
                    if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    else Text("SIGN IN", fontWeight = FontWeight.Bold, color = Color(0xFF0A0E29))
                }
            }
        }
    }
}

// --- 5. 监控大盘界面 ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    token: String,
    selectedMonth: Int,
    language: String,
    onLanguageChange: (String) -> Unit,
    onMonthSelected: (Int) -> Unit,
    onNavigateToSnapshot: (String, Int) -> Unit,
    onNavigateToMetrics: (String, String, Int, String?, String?) -> Unit,
    onNavigateToAlerts: (Int) -> Unit,
    onNavigateToTrend: (String, String) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = selectedMonth - 1, pageCount = { 12 })
    var scrollTrigger by remember { mutableStateOf(0L) }

    LaunchedEffect(selectedMonth) {
        if (pagerState.currentPage != selectedMonth - 1) {
            pagerState.animateScrollToPage(selectedMonth - 1)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (selectedMonth != pagerState.currentPage + 1) {
            onMonthSelected(pagerState.currentPage + 1)
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(Color(0xFF0A0E29))) {
                CenterAlignedTopAppBar(
                    title = { 
                        Text(
                            text = if (language == "zh") "工具平台" else "TOOLS PLATFORM", 
                            fontWeight = FontWeight.Bold, 
                            letterSpacing = 1.sp,
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures(onDoubleTap = { scrollTrigger = System.currentTimeMillis() })
                            }
                        ) 
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent, titleContentColor = Color.White),
                    actions = {
                        TextButton(onClick = { onLanguageChange(if (language == "zh") "en" else "zh") }) {
                            Text(if (language == "zh") "EN" else "中", color = Color.Cyan)
                        }
                    }
                )
                ScrollableTabRow(
                    selectedTabIndex = selectedMonth - 1,
                    containerColor = Color.Transparent,
                    contentColor = Color.Cyan,
                    edgePadding = 16.dp,
                    divider = {}
                ) {
                    (1..12).forEach { month ->
                        Tab(
                            selected = selectedMonth == month,
                            onClick = { onMonthSelected(month) },
                            text = { Text("${month}月", color = if (selectedMonth == month) Color.Cyan else Color.Gray, fontWeight = if(selectedMonth == month) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFF0A0E29)
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) { page ->
            DashboardMonthPage(
                token = token,
                month = page + 1,
                language = language,
                onNavigateToSnapshot = onNavigateToSnapshot,
                onNavigateToMetrics = onNavigateToMetrics,
                onNavigateToAlerts = onNavigateToAlerts,
                onNavigateToTrend = onNavigateToTrend,
                scrollTrigger = scrollTrigger
            )
        }
    }
}

@Composable
fun DashboardMonthPage(
    token: String,
    month: Int,
    language: String,
    onNavigateToSnapshot: (String, Int) -> Unit,
    onNavigateToMetrics: (String, String, Int, String?, String?) -> Unit,
    onNavigateToAlerts: (Int) -> Unit,
    onNavigateToTrend: (String, String) -> Unit,
    scrollTrigger: Long = 0L
) {
    var summary by remember(month) { mutableStateOf<SummaryResponse?>(null) }
    var isLoading by remember(month) { mutableStateOf(true) }
    var errorMessage by remember(month) { mutableStateOf<String?>(null) }

    LaunchedEffect(month) {
        isLoading = true
        errorMessage = null
        try {
            val auth = "Bearer $token"
            val res = NetworkModule.service.getSummary(auth, month)
            
            // Fetch the full failing metrics for the latest snapshot to ensure derived metrics are included
            val failingRes = res.latest_snapshot?.snapshot_id?.let { sid ->
                NetworkModule.service.getFailingMetrics(auth, month, snapshotId = sid)
            }
            summary = if (failingRes != null) {
                res.copy(latest_failing_metrics = failingRes.items)
            } else {
                res
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: e.toString()
        } finally {
            isLoading = false
        }
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.Cyan)
        }
    } else if (errorMessage != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Error: $errorMessage", color = Color.Red, modifier = Modifier.padding(16.dp))
        }
    } else {
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        
        LaunchedEffect(scrollTrigger) {
            if (scrollTrigger > 0L) {
                listState.animateScrollToItem(0)
            }
        }
        
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            
            summary?.let {
                item {
                    Column(modifier = Modifier.padding(bottom = 16.dp)) {
                        Text(
                            if (language == "zh") "已分析 ${it.snapshot_count} 份快照中的 ${it.metric_count} 个指标" else "Analyzed ${it.metric_count} metrics across ${it.snapshot_count} snapshots",
                            color = Color.Gray, fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        
                        val overallFailing = it.latest_failing_metrics
                            .filter { m -> m.category == "整体" || m.category == "OVERALL" }
                            .sortedBy { m -> m.is_derived_overall == true }
                            .distinctBy { m -> m.metric_label }
                            
                        if (overallFailing.isNotEmpty()) {
                            val failingDesc = overallFailing.joinToString("，") { m -> 
                                val label = if (language == "zh") m.schema?.target_config?.label_i18n?.zh ?: m.metric_label else m.schema?.target_config?.label_i18n?.en ?: m.metric_label
                                if (language == "zh") {
                                    "${label}(当前: ${m.raw_value ?: "N/A"}, 目标: ${m.target_value ?: "N/A"})"
                                } else {
                                    "${label}(Actual: ${m.raw_value ?: "N/A"}, Target: ${m.target_value ?: "N/A"})"
                                }
                            }
                            Text(
                                if (language == "zh") "本月最新快照中，整体指标 $failingDesc 未达标。" else "In latest snapshot, overall metrics $failingDesc failed.",
                                color = Color(0xFFFF5252).copy(alpha = 0.8f), fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
                
                item {
                    summary?.let {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            val snapshotId = it.latest_snapshot?.snapshot_id
                            val latestTotal = it.latest_metrics_total
                            val latestFailing = it.latest_failing_metrics.size
                            val latestCompliance = if (latestTotal > 0) ((latestTotal - latestFailing).toFloat() / latestTotal * 100).toInt() else null

                            MetricSummaryCard(if (language == "zh") "达标率" else "COMPLIANCE", "${latestCompliance ?: "N/A"}%", Modifier.weight(1f), Color(0xFF00E5FF), onClick = { onNavigateToMetrics(if (language == "zh") "达标指标" else "COMPLIANT METRICS", "PASSING", month, snapshotId, null) })
                            MetricSummaryCard(if (language == "zh") "异常" else "ALERTS", "$latestFailing", Modifier.weight(1f), Color(0xFFFF5252), onClick = { onNavigateToMetrics(if (language == "zh") "异常指标" else "FAILING METRICS", "FAILING", month, snapshotId, null) })
                            MetricSummaryCard(if (language == "zh") "临期预警" else "EXPIRING", "${it.latest_expiring_ticket_count}", Modifier.weight(1f), Color(0xFFFFD700), onClick = { onNavigateToAlerts(month) })
                        }
                    }
                }

                summary?.latest_category_scores?.takeIf { it.isNotEmpty() }?.let { scores ->
                    item {
                        Text(if (language == "zh") "分类得分" else "CATEGORY SCORES", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(scores) { score ->
                                val displayCat = if (language == "en" && score.category == "整体") "OVERALL" else score.category
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B3D)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.clickable {
                                        onNavigateToMetrics(if (language == "zh") "$displayCat 指标" else "$displayCat METRICS", "ALL", month, summary?.latest_snapshot?.snapshot_id, score.category)
                                    }
                                ) {
                                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(displayCat, color = Color.Gray, fontSize = 12.sp)
                                        Spacer(Modifier.height(4.dp))
                                        Text("${score.final_score ?: "N/A"}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                summary?.latest_failing_metrics?.takeIf { it.isNotEmpty() }?.let { failingMetrics ->
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (language == "zh") "各分类近期异常" else "RECENT ALERTS BY CATEGORY", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            TextButton(onClick = { onNavigateToMetrics(if (language == "zh") "异常指标" else "FAILING METRICS", "FAILING", month, summary?.latest_snapshot?.snapshot_id, null) }) {
                                Text(if (language == "zh") "查看全部" else "VIEW ALL", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                    
                    val groupedMetrics = failingMetrics.groupBy { it.category }
                        .toList()
                        .sortedWith(compareBy({ if (it.first == "整体") 0 else 1 }, { it.first }))
                    groupedMetrics.forEach { (category, catMetrics) ->
                        item {
                            val displayCat = if (language == "en" && category == "整体") "OVERALL" else category
                            Text(displayCat, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        }
                        items(catMetrics.take(3)) { metric ->
                            MetricRowCard(metric, language, token = token, onNavigateToTrend = onNavigateToTrend)
                        }
                    }
                }
                
                summary?.latest_alerts?.expiring_tickets?.takeIf { it.isNotEmpty() }?.let { tickets ->
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (language == "zh") "临期任务预警" else "EXPIRING TICKETS", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            TextButton(onClick = { onNavigateToAlerts(month) }) {
                                Text(if (language == "zh") "查看全部" else "VIEW ALL", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                    items(tickets.take(2)) { ticket ->
                        TicketRowCard(ticket, language)
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

// --- 6. 辅助组件 ---
@Composable
fun MetricSummaryCard(title: String, value: String, modifier: Modifier, color: Color, onClick: (() -> Unit)? = null) {
    Card(
        modifier = if (onClick != null) modifier.height(110.dp).clickable { onClick() } else modifier.height(110.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B3D)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(value, color = color, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text(title, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun SnapshotCard(snapshot: Snapshot, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B3D)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("ID: ${snapshot.snapshot_id}", color = Color.White, fontWeight = FontWeight.Bold)
                Text(snapshot.created_at, color = Color.Gray, fontSize = 12.sp)
            }
            Text(
                "${snapshot.standard_total_score ?: "N/A"}", 
                color = if((snapshot.standard_total_score ?: 0f) >= 98f) Color(0xFF00E5FF) else Color(0xFFFFD700),
                fontWeight = FontWeight.Black, 
                fontSize = 22.sp
            )
        }
    }
}

@Composable
fun MetricRowCard(metric: Metric, language: String, token: String? = null, onNavigateToTrend: ((String, String) -> Unit)? = null) {
    var expanded by remember { mutableStateOf(false) }
    var sparklineData by remember { mutableStateOf<List<Float>?>(null) }
    var sparklineLoading by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        if (expanded && token != null && sparklineData == null) {
            sparklineLoading = true
            try {
                val res = NetworkModule.service.getTrend(
                    token = "Bearer $token",
                    metricLabel = metric.metric_label,
                    category = metric.category,
                    days = 7,
                    lang = if (language == "zh") "zh-CN" else "en-US"
                )
                sparklineData = res.points
                    .sortedBy { it.snapshot_created_at }
                    .distinctBy { it.snapshot_created_at.substringBefore(" ") }
                    .mapNotNull { it.numeric_value }
            } catch (e: Exception) {
                // ignore
            } finally {
                sparklineLoading = false
            }
        }
    }

    val displayLabel = if (language == "zh") {
        metric.schema?.target_config?.label_i18n?.zh ?: metric.metric_label
    } else {
        metric.schema?.target_config?.label_i18n?.en ?: metric.metric_label
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B3D)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val displayCategory = if (language == "en" && metric.category == "整体") "OVERALL" else metric.category
                    Text(displayCategory, color = Color.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (metric.schema?.is_sub_metric == true) {
                        Spacer(Modifier.width(8.dp))
                        Text(if (language == "zh") "子指标" else "SUB-METRIC", color = Color(0xFFB388FF), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(Color(0xFF311B92), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }
                if (metric.is_failing) {
                    Text(if (language == "zh") "异常" else "FAILING", color = Color(0xFFFF5252), fontSize = 10.sp, fontWeight = FontWeight.Black)
                } else {
                    Text(if (language == "zh") "正常" else "OK", color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(4.dp))
            
            if (metric.schema?.is_sub_metric == true && !metric.schema.main_metric_label.isNullOrEmpty()) {
                Text(if (language == "zh") "主指标: ${metric.schema.main_metric_label}" else "Main: ${metric.schema.main_metric_label}", color = Color.Gray, fontSize = 11.sp)
                Spacer(Modifier.height(2.dp))
            }
            
            Text(displayLabel, color = Color.White, fontSize = 14.sp)
            
            if (!metric.schema?.source_title.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(metric.schema.source_title, color = Color.DarkGray, fontSize = 10.sp)
            }
            
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Target: ${metric.target_value ?: "-"}", color = Color.Gray, fontSize = 12.sp)
                Text("Actual: ${metric.raw_value ?: "-"}", color = if(metric.is_failing) Color(0xFFFF5252) else Color.Gray, fontSize = 12.sp)
            }
            if (!metric.gap.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Gap: ${metric.gap}", color = Color(0xFFFFD700), fontSize = 12.sp)
            }
            if (metric.weight != null || metric.earned_score != null) {
                Spacer(Modifier.height(4.dp))
                Text("Weight: ${metric.weight ?: "-"} | Earned: ${metric.earned_score ?: "-"}", color = Color.DarkGray, fontSize = 11.sp)
            }
            if (expanded && onNavigateToTrend != null) {
                Spacer(Modifier.height(12.dp))
                
                if (sparklineLoading) {
                    Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.Cyan, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else if (!sparklineData.isNullOrEmpty() && sparklineData!!.size > 1) {
                    val points = sparklineData!!
                    val maxVal = points.maxOrNull() ?: 1f
                    val minVal = points.minOrNull() ?: 0f
                    val range = if (maxVal == minVal) 1f else maxVal - minVal
                    
                    Canvas(modifier = Modifier.fillMaxWidth().height(60.dp).padding(top = 16.dp, bottom = 4.dp)) {
                        val stepX = size.width / (points.size - 1).coerceAtLeast(1)
                        val path = androidx.compose.ui.graphics.Path()
                        val textPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.LTGRAY
                            textSize = 28f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                        
                        points.forEachIndexed { index, value ->
                            val x = index * stepX
                            val y = size.height - ((value - minVal) / range) * size.height
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, color = Color(0xFF00E5FF), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
                        
                        points.forEachIndexed { index, value ->
                            val x = index * stepX
                            val y = size.height - ((value - minVal) / range) * size.height
                            drawCircle(color = Color.White, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
                            drawCircle(color = Color(0xFF00E5FF), radius = 2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
                            
                            val valStr = if (value % 1.0f == 0f) value.toInt().toString() else String.format("%.1f", value)
                            drawContext.canvas.nativeCanvas.drawText(valStr, x, y - 8.dp.toPx(), textPaint)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onNavigateToTrend(metric.metric_label, metric.category) },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = "Trend", tint = Color(0xFF0A0E29), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (language == "zh") "查看近期趋势 (30天)" else "View 30-Day Trend", color = Color(0xFF0A0E29), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// --- 7. 详情页与列表页 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapshotDetailScreen(token: String, snapshotId: String, month: Int, language: String, onBack: () -> Unit, onNavigateToTrend: (String, String) -> Unit) {
    var detail by remember { mutableStateOf<SnapshotDetailResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var scrollTrigger by remember { mutableStateOf(0L) }

    LaunchedEffect(snapshotId) {
        try {
            detail = NetworkModule.service.getSnapshotDetail("Bearer $token", snapshotId, month)
        } catch (e: Exception) {
            errorMessage = e.message ?: e.toString()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = if (language == "zh") "快照 $snapshotId" else "SNAPSHOT $snapshotId", 
                        color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { scrollTrigger = System.currentTimeMillis() })
                        }
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0E29))
            )
        },
        containerColor = Color(0xFF0A0E29)
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.Cyan) }
        } else if (errorMessage != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Error: $errorMessage", color = Color.Red) }
        } else {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            
            LaunchedEffect(scrollTrigger) {
                if (scrollTrigger > 0L) {
                    if (listState.firstVisibleItemIndex > 10) listState.scrollToItem(10)
                    listState.animateScrollToItem(0)
                }
            }
            
            detail?.let { res ->
                val deduplicatedMetrics = remember(res.metrics) {
                    res.metrics.sortedBy { it.is_derived_overall == true }.distinctBy { it.metric_label + "_" + it.category }
                }
                
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF161B3D)), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(24.dp)) {
                                Text(if (language == "zh") "总得分" else "OVERALL SCORE", color = Color.Gray, fontSize = 12.sp)
                                Text("${res.snapshot.standard_total_score ?: "N/A"}", color = Color.Cyan, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                                Spacer(Modifier.height(8.dp))
                                Text(if (language == "zh") "时间: ${res.snapshot.created_at}" else "Date: ${res.snapshot.created_at}", color = Color.White, fontSize = 14.sp)
                                if (res.snapshot.image_path != null) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("Image: ${res.snapshot.image_path}", color = Color.Gray, fontSize = 11.sp)
                                }
                                if (res.snapshot.excel_path != null) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("Excel: ${res.snapshot.excel_path}", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    
                    if (res.category_scores.isNotEmpty()) {
                        item { Text(if (language == "zh") "分类得分" else "CATEGORY SCORES", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        items(res.category_scores) { cat ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF161B3D)), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    val displayCat = if (language == "en" && cat.category == "整体") "OVERALL" else cat.category
                                    Text(displayCat, color = Color.White, fontWeight = FontWeight.Medium)
                                    Text("${cat.final_score ?: "-"}", color = Color.Cyan, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (deduplicatedMetrics.isNotEmpty()) {
                        item { Text(if (language == "zh") "全部指标" else "ALL METRICS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
                        items(deduplicatedMetrics) { metric ->
                            MetricRowCard(metric, language, token = token, onNavigateToTrend = onNavigateToTrend)
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricsListScreen(token: String, title: String, filterType: String, month: Int, snapshotId: String?, category: String?, language: String, onBack: () -> Unit, onNavigateToTrend: (String, String) -> Unit) {
    var metrics by remember { mutableStateOf<List<Metric>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var scrollTrigger by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        try {
            val auth = "Bearer $token"
            val res = if (filterType == "FAILING") {
                NetworkModule.service.getFailingMetrics(auth, month, snapshotId = snapshotId)
            } else {
                NetworkModule.service.getMetrics(auth, month, category = category, snapshotId = snapshotId)
            }
            val filtered = if (filterType == "PASSING") res.items.filter { !it.is_failing } else res.items
            metrics = filtered.sortedBy { it.is_derived_overall == true }.distinctBy { it.metric_label + "_" + it.category }
        } catch (e: Exception) {
            errorMessage = e.message ?: e.toString()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { scrollTrigger = System.currentTimeMillis() })
                        }
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0E29))
            )
        },
        containerColor = Color(0xFF0A0E29)
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.Cyan) }
        } else if (errorMessage != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Error: $errorMessage", color = Color.Red) }
        } else {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            
            LaunchedEffect(scrollTrigger) {
                if (scrollTrigger > 0L) {
                    if (listState.firstVisibleItemIndex > 10) listState.scrollToItem(10)
                    listState.animateScrollToItem(0)
                }
            }
            
            val deduplicatedMetrics = remember(metrics) { metrics }
            
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text(if (language == "zh") "共显示 ${deduplicatedMetrics.size} 个第 $month 月的指标" else "Showing ${deduplicatedMetrics.size} metrics for Month $month", color = Color.Gray, fontSize = 12.sp) }
                
                val grouped = deduplicatedMetrics.groupBy { it.category }
                    .toList()
                    .sortedWith(compareBy({ if (it.first == "整体") 0 else 1 }, { it.first }))
                grouped.forEach { (cat, list) ->
                    item {
                        val displayCat = if (language == "en" && cat == "整体") "OVERALL" else cat
                        Text(displayCat, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(list) { metric ->
                        MetricRowCard(metric, language, token = token, onNavigateToTrend = onNavigateToTrend)
                    }
                }
                
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
fun TicketRowCard(ticket: ExpiringTicket, language: String = "zh") {
    var expanded by remember { mutableStateOf(false) }

    val displayCollection = if (language == "zh") {
        ticket.collection_label_i18n?.zh ?: ticket.display_collection_label ?: ticket.collection
    } else {
        ticket.collection_label_i18n?.en ?: ticket.display_collection_label ?: ticket.collection
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B3D)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(displayCollection.uppercase(), color = Color.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                val isOverdue = ticket.sla_status?.is_overdue ?: (ticket.urgency == "overdue")
                val urgencyColor = if (isOverdue) Color(0xFFFF5252) else Color(0xFFFFD700)
                val urgencyText = if (isOverdue) {
                    if (language == "zh") "已超时" else "OVERDUE"
                } else {
                    if (language == "zh") "临期" else "EXPIRING"
                }
                Text(urgencyText, color = urgencyColor, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(4.dp))
            val displayTitle = if (language == "en") {
                ticket.title_i18n?.en ?: ticket.display_title ?: ticket.title ?: "Ticket: ${ticket.ticket_id}"
            } else {
                ticket.title_i18n?.zh ?: ticket.display_title ?: ticket.title ?: "Ticket: ${ticket.ticket_id}"
            }
            Text(displayTitle, color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text("ID: ${ticket.ticket_id}", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(if (language == "zh") "到期: ${ticket.due_date ?: "-"}" else "Due: ${ticket.due_date ?: "-"}", color = Color.Gray, fontSize = 12.sp)
            if (ticket.owner != null) {
                Spacer(Modifier.height(4.dp))
                Text(if (language == "zh") "负责人: ${ticket.owner}" else "Owner: ${ticket.owner}", color = Color.DarkGray, fontSize = 11.sp)
            }
            if (ticket.network_name != null || ticket.product != null) {
                Spacer(Modifier.height(4.dp))
                val net = ticket.network_name ?: "-"
                val prod = ticket.product ?: "-"
                Text(if (language == "zh") "网络: $net | 产品: $prod" else "Net: $net | Prod: $prod", color = Color(0xFFB388FF), fontSize = 11.sp)
            }
            if (ticket.sla_text != null || ticket.sla_text_i18n != null) {
                Spacer(Modifier.height(4.dp))
                val isOverdue = ticket.sla_status?.is_overdue ?: (ticket.urgency == "overdue")
                val slaColor = if (isOverdue) Color(0xFFFF5252) else Color(0xFFFFD700)
                val displaySlaText = if (language == "en") {
                    ticket.sla_text_i18n?.en ?: ticket.display_sla_text ?: ticket.sla_text ?: ""
                } else {
                    ticket.sla_text_i18n?.zh ?: ticket.display_sla_text ?: ticket.sla_text ?: ""
                }
                Text(displaySlaText, color = slaColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            
            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.DarkGray)
                
                val details = listOfNotNull(
                    ticket.status?.let { (if (language == "zh") "状态" else "Status") to it },
                    ticket.customer_name?.let { (if (language == "zh") "客户" else "Customer") to it },
                    ticket.product_line?.let { (if (language == "zh") "产品线" else "Product Line") to it },
                    ticket.sla_days?.let { (if (language == "zh") "剩余 SLA 天数" else "SLA Days Left") to it.toString() }
                )
                
                if (details.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth()) {
                        for (i in details.indices step 2) {
                            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(details[i].first, color = Color.Gray, fontSize = 10.sp)
                                    Text(details[i].second, color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                                if (i + 1 < details.size) {
                                    Column(Modifier.weight(1f)) {
                                        Text(details[i + 1].first, color = Color.Gray, fontSize = 10.sp)
                                        Text(details[i + 1].second, color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                
                if (ticket.raw != null && ticket.raw is JsonObject && ticket.raw.jsonObject.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(if (language == "zh") "附加信息" else "Additional Info", color = Color(0xFFB388FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0A0E29), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        ticket.raw.jsonObject.forEach { (key, value) ->
                            if (value is JsonObject) {
                                Text(key, color = Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                                Column(Modifier.padding(start = 8.dp)) {
                                    value.forEach { (subKey, subVal) ->
                                        JsonRow(subKey, subVal)
                                    }
                                }
                            } else {
                                JsonRow(key, value)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun JsonRow(key: String, value: JsonElement) {
    val cleanValue = if (value is JsonPrimitive) {
        if (value.isString) value.content else value.content
    } else value.toString()
    
    if (cleanValue.isNotBlank() && cleanValue != "null") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(key, color = Color.Gray, fontSize = 10.sp, modifier = Modifier.weight(0.4f))
            Text(cleanValue, color = Color.LightGray, fontSize = 10.sp, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End, lineHeight = 14.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsListScreen(token: String, month: Int, language: String, onBack: () -> Unit) {
    var tickets by remember { mutableStateOf<List<ExpiringTicket>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var scrollTrigger by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        try {
            val res = NetworkModule.service.getAlerts("Bearer $token", month)
            tickets = res.expiring_tickets
        } catch (e: Exception) {
            errorMessage = e.message ?: e.toString()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = if (language == "zh") "临期预警" else "EXPIRING ALERTS", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { scrollTrigger = System.currentTimeMillis() })
                        }
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0E29))
            )
        },
        containerColor = Color(0xFF0A0E29)
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.Cyan) }
        } else if (errorMessage != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Error: $errorMessage", color = Color.Red) }
        } else {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            
            LaunchedEffect(scrollTrigger) {
                if (scrollTrigger > 0L) {
                    if (listState.firstVisibleItemIndex > 10) listState.scrollToItem(10)
                    listState.animateScrollToItem(0)
                }
            }
            
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text(if (language == "zh") "共显示 ${tickets.size} 个第 $month 月的任务" else "Showing ${tickets.size} tickets for Month $month", color = Color.Gray, fontSize = 12.sp) }
                
                val grouped = tickets.groupBy { it.collection }
                    .toList()
                grouped.forEach { (collection, list) ->
                    val firstTicket = list.firstOrNull()
                    val displayCollection = if (language == "zh") {
                        firstTicket?.collection_label_i18n?.zh ?: firstTicket?.display_collection_label ?: collection
                    } else {
                        firstTicket?.collection_label_i18n?.en ?: firstTicket?.display_collection_label ?: collection
                    }
                    item {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text(displayCollection.uppercase(), color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            
                            val overdueCount = list.count { it.sla_status?.is_overdue == true }
                            val expiringCount = list.size - overdueCount
                            
                            Text(
                                if (language == "zh") "${expiringCount} 临期 | ${overdueCount} 超期" 
                                else "${expiringCount} Expiring | ${overdueCount} Overdue",
                                color = Color.Gray, 
                                fontSize = 11.sp
                            )
                        }
                    }
                    items(list) { ticket ->
                        TicketRowCard(ticket, language)
                    }
                }
                
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricTrendScreen(token: String, metricLabel: String, category: String, language: String, onBack: () -> Unit) {
    var trend by remember { mutableStateOf<TrendResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var scrollTrigger by remember { mutableStateOf(0L) }

    LaunchedEffect(metricLabel, category) {
        try {
            trend = NetworkModule.service.getTrend(
                token = "Bearer $token",
                metricLabel = metricLabel,
                category = category,
                lang = if (language == "zh") "zh-CN" else "en-US"
            )
        } catch (e: Exception) {
            errorMessage = e.message ?: e.toString()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = metricLabel, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { scrollTrigger = System.currentTimeMillis() })
                        }
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0E29))
            )
        },
        containerColor = Color(0xFF0A0E29)
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.Cyan) }
        } else if (errorMessage != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Error: $errorMessage", color = Color.Red) }
        } else {
            trend?.let { res ->
                val filteredPoints = remember(res.points) {
                    res.points
                        .sortedByDescending { it.snapshot_created_at }
                        .distinctBy { it.snapshot_created_at.substringBefore(" ") }
                }

                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                
                LaunchedEffect(scrollTrigger) {
                    if (scrollTrigger > 0L) {
                        if (listState.firstVisibleItemIndex > 10) listState.scrollToItem(10)
                        listState.animateScrollToItem(0)
                    }
                }
                
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    item {
                        Text(
                            text = if (language == "zh") "共显示 ${filteredPoints.size} 天的最新记录" else "Showing latest records for ${filteredPoints.size} days",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    
                    items(filteredPoints) { point ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B3D)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    val valColor = if (point.is_failing) Color(0xFFFF5252) else Color.Cyan
                                    val valText = point.raw_value ?: point.numeric_value?.toString() ?: "-"
                                    Text(valText, color = valColor, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                                    
                                    if (point.is_failing) {
                                        Text(if (language == "zh") "异常" else "FAILING", color = Color(0xFFFF5252), fontSize = 10.sp, fontWeight = FontWeight.Black)
                                    } else {
                                        Text(if (language == "zh") "正常" else "OK", color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                                
                                Spacer(Modifier.height(12.dp))
                                
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (language == "zh") "快照时间" else "Snapshot Time", color = Color.Gray, fontSize = 11.sp)
                                    Text(point.snapshot_created_at, color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (language == "zh") "入库时间" else "Stored Time", color = Color.Gray, fontSize = 11.sp)
                                    Text(point.stored_at ?: (if (language == "zh") "无记录" else "N/A"), color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                                
                                if (!point.gap.isNullOrEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text("Gap: ${point.gap}", color = Color(0xFFFFD700), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppNavigation()
            }
        }
    }
}
