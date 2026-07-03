package com.example.newapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.*

// --- 1. 数据模型 (严格匹配文档字段) ---
@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginResponse(val success: Boolean, val token: String? = null, val message: String? = null)

@Serializable
data class SummaryResponse(
    val snapshot_count: Int,
    val metric_count: Int,
    val failing_metric_count: Int,
    val compliance_rate: Float? = null,
    val latest_snapshot_at: String? = null
)

@Serializable
data class Snapshot(
    val snapshot_id: String,
    val month: Int? = null,
    val created_at: String,
    val standard_total_score: Float? = null
)

@Serializable
data class SnapshotsResponse(
    val items: List<Snapshot>
)

// --- 2. API 接口定义 ---
interface MetricsApiService {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("api/external/metrics/summary")
    suspend fun getSummary(
        @Header("Authorization") token: String,
        @Query("month") month: Int
    ): SummaryResponse

    @GET("api/external/metrics/snapshots")
    suspend fun getSnapshots(
        @Header("Authorization") token: String,
        @Query("month") month: Int,
        @Query("limit") limit: Int = 20
    ): SnapshotsResponse
}

object NetworkModule {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder().build()
    
    val service: MetricsApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://cs.fanxiaolong.uk/") // 使用你提供的线上域名
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MetricsApiService::class.java)
    }
}

// --- 3. 页面导航管理 ---
@Composable
fun AppNavigation() {
    var token by remember { mutableStateOf<String?>(null) }
    
    if (token == null) {
        LoginScreen(onLoginSuccess = { token = it })
    } else {
        DashboardScreen(token = token!!)
    }
}

// --- 4. 登录界面 ---
@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit) {
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
                Text("METRICS ACCESS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
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

                Spacer(Modifier.height(40.dp))
                Button(
                    onClick = {
                        scope.launch {
                            loading = true
                            error = null
                            try {
                                val res = NetworkModule.service.login(LoginRequest(username, password))
                                if (res.success && res.token != null) onLoginSuccess(res.token)
                                else error = res.message ?: "Invalid Credentials"
                            } catch (e: Exception) {
                                error = "Connection failed: ${e.localizedMessage}"
                            } finally { loading = false }
                        }
                    },
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(token: String) {
    var summary by remember { mutableStateOf<SummaryResponse?>(null) }
    var snapshots by remember { mutableStateOf<List<Snapshot>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val auth = "Bearer $token"
            summary = NetworkModule.service.getSummary(auth, 7)
            snapshots = NetworkModule.service.getSnapshots(auth, 7).items
        } catch (e: Exception) {
            errorMessage = e.message ?: e.toString()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("MONITORING CENTER", fontWeight = FontWeight.Bold, letterSpacing = 1.sp) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent, titleContentColor = Color.White)
            )
        },
        containerColor = Color(0xFF0A0E29)
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.Cyan)
            }
        } else if (errorMessage != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Error: $errorMessage", color = Color.Red, modifier = Modifier.padding(16.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    summary?.let {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            MetricSummaryCard("COMPLIANCE", "${it.compliance_rate}%", Modifier.weight(1f), Color(0xFF00E5FF))
                            MetricSummaryCard("ALERTS", "${it.failing_metric_count}", Modifier.weight(1f), Color(0xFFFF5252))
                        }
                    }
                }

                item {
                    Text("LATEST SNAPSHOTS", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                items(snapshots) { snapshot ->
                    SnapshotCard(snapshot)
                }
                
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
fun MetricSummaryCard(title: String, value: String, modifier: Modifier, color: Color) {
    Card(
        modifier = modifier.height(110.dp),
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
fun SnapshotCard(snapshot: Snapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
