package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.theme.*
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import java.util.*

fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EsportsApp(viewModel: EsportsViewModel) {
    val context = LocalContext.current
    val userState by viewModel.currentUser.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val loginError by viewModel.loginError.collectAsStateWithLifecycle()

    var activeScreen by remember { mutableStateOf("home") }
    var previousScreen by remember { mutableStateOf("home") }
    var inAdminMode by remember { mutableStateOf(false) }

    val showMessage by viewModel.depositRequestSuccess.collectAsStateWithLifecycle()

    LaunchedEffect(showMessage) {
        showMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearDepositStatusText()
        }
    }

    if (userState == null) {
        // Authentication Gate
        LoginRegisterScreen(
            viewModel = viewModel,
            isLoading = isLoading,
            loginError = loginError
        )
    } else {
        val user = userState!!
        
        Scaffold(
            bottomBar = {
                if (!inAdminMode) {
                    Column {
                        HorizontalDivider(
                            color = DarkAccent.copy(alpha = 0.8f),
                            thickness = 1.dp
                        )
                        NavigationBar(
                            containerColor = ElegantNavBg,
                            tonalElevation = 8.dp,
                            modifier = Modifier.navigationBarsPadding()
                        ) {
                        val navItems = listOf(
                            NavigationItem("home", "Home", Icons.Default.Home, Icons.Outlined.Home),
                            NavigationItem("games", "Games", Icons.Default.SportsEsports, Icons.Outlined.SportsEsports),
                            NavigationItem("store", "Store", Icons.Default.ShoppingBag, Icons.Outlined.ShoppingBag),
                            NavigationItem("rewards", "Rewards", Icons.Default.Star, Icons.Outlined.Star),
                            NavigationItem("profile", "Profile", Icons.Default.Person, Icons.Outlined.Person)
                        )

                        navItems.forEach { item ->
                            val isSelected = activeScreen == item.id
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    previousScreen = activeScreen
                                    activeScreen = item.id
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) item.activeIcon else item.inactiveIcon,
                                        contentDescription = item.label,
                                        tint = if (isSelected) NeonGold else GrayText
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        color = if (isSelected) NeonGold else GrayText,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = CharcoalCard
                                )
                            )
                        }
                    }
                }
            }
        }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CharcoalBg)
                    .padding(innerPadding)
            ) {
                if (inAdminMode && user.isAdmin) {
                    AdminDashboardScreen(
                        viewModel = viewModel,
                        onBack = { inAdminMode = false }
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header Box details
                        HeaderBox(
                            user = user,
                            onAdminClick = { if (user.isAdmin) inAdminMode = true },
                            onNotificationClick = {
                                Toast.makeText(context, "Notifications up to date!", Toast.LENGTH_SHORT).show()
                            }
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            when (activeScreen) {
                                "home" -> HomeScreen(viewModel = viewModel, onNavigate = { activeScreen = it })
                                "games" -> GamesScreen(viewModel = viewModel)
                                "store" -> StoreScreen(viewModel = viewModel)
                                "rewards" -> RewardsScreen(viewModel = viewModel)
                                "profile" -> ProfileScreen(viewModel = viewModel, onLogout = { viewModel.logout() })
                            }
                        }
                    }
                }
            }
        }
    }
}

data class NavigationItem(
    val id: String,
    val label: String,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector
)

// ============================================
// COMPONENT 1: HEADER BOX
// ============================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeaderBox(
    user: UserEntity,
    onAdminClick: () -> Unit,
    onNotificationClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            brush = Brush.radialGradient(listOf(NeonOrange, NeonGold)),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.name.take(1).uppercase(),
                        color = CharcoalBg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = user.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "UID: ${if (user.gameUid.isNotBlank()) user.gameUid else user.emailKey.take(8)}",
                        color = Color.Gray,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Wallet Chips
                Card(
                    onClick = { /* Wallet view action */ },
                    colors = CardDefaults.cardColors(containerColor = CharcoalBg),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = "Wallet",
                            tint = NeonGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Rs.${String.format("%.1f", user.mainWallet + user.bonusWallet + user.winningWallet)}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = CharcoalBg),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = "Coins",
                            tint = NeonOrange,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${user.coins.toInt()}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (user.isAdmin) {
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onAdminClick,
                        modifier = Modifier
                            .background(NeonGold.copy(alpha = 0.2f), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = "Admin Console",
                            tint = NeonGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))
                IconButton(onClick = onNotificationClick) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Alerts",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

// ============================================
// SCREEN 1: LOGIN & REGISTER GATE
// ============================================
@Composable
fun LoginRegisterScreen(
    viewModel: EsportsViewModel,
    isLoading: Boolean,
    loginError: String?
) {
    val context = LocalContext.current
    var isRegisterMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var gameUid by remember { mutableStateOf("") }
    var referCode by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo Icon Container
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(
                        brush = Brush.verticalGradient(listOf(NeonOrange, NeonGold)),
                        shape = RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SportsEsports,
                    contentDescription = "Anu battle",
                    tint = CharcoalBg,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "ANU BATTLE",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Dynamic Live Esports Arena",
                color = NeonGold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isRegisterMode) "Create New Account" else "Sign In to Lobbies",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    if (isRegisterMode) {
                        // 1. Name
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("In-Game Handle / Name") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGold,
                                unfocusedBorderColor = GrayText,
                                focusedLabelColor = NeonGold,
                                unfocusedLabelColor = GrayText,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_name")
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // 2. Email
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email Address") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGold,
                                unfocusedBorderColor = GrayText,
                                focusedLabelColor = NeonGold,
                                unfocusedLabelColor = GrayText,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_email")
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // 3. Password
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGold,
                                unfocusedBorderColor = GrayText,
                                focusedLabelColor = NeonGold,
                                unfocusedLabelColor = GrayText,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                val description = if (passwordVisible) "Hide password" else "Show password"
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(imageVector = image, description, tint = NeonGold)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // 4. Confirm Password
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirm Password") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGold,
                                unfocusedBorderColor = GrayText,
                                focusedLabelColor = NeonGold,
                                unfocusedLabelColor = GrayText,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                val image = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                    Icon(imageVector = image, "Toggle confirmation visibility", tint = NeonGold)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // 5. Game UID
                        OutlinedTextField(
                            value = gameUid,
                            onValueChange = { gameUid = it },
                            label = { Text("Game UID") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGold,
                                unfocusedBorderColor = GrayText,
                                focusedLabelColor = NeonGold,
                                unfocusedLabelColor = GrayText,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // 6. Refer Code (Optional)
                        OutlinedTextField(
                            value = referCode,
                            onValueChange = { referCode = it },
                            label = { Text("Refer Code (Optional)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGold,
                                unfocusedBorderColor = GrayText,
                                focusedLabelColor = NeonGold,
                                unfocusedLabelColor = GrayText,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                        )

                    } else {
                        // Login Mode
                        // Email Field
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email Address") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGold,
                                unfocusedBorderColor = GrayText,
                                focusedLabelColor = NeonGold,
                                unfocusedLabelColor = GrayText,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_email")
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Password Field
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGold,
                                unfocusedBorderColor = GrayText,
                                focusedLabelColor = NeonGold,
                                unfocusedLabelColor = GrayText,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(imageVector = image, "Toggle visibility", tint = NeonGold)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                    }

                    if (loginError != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = loginError,
                            color = Color.Red,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (isLoading) {
                        CircularProgressIndicator(color = NeonGold)
                    } else {
                        Button(
                            onClick = {
                                if (isRegisterMode) {
                                    if (name.isBlank() || email.isBlank() || password.isBlank() || gameUid.isBlank()) {
                                        viewModel.setLoginError("Please fill out all required fields!")
                                        return@Button
                                    }
                                    if (password != confirmPassword) {
                                        viewModel.setLoginError("Passwords do not match!")
                                        return@Button
                                    }
                                    viewModel.register(email, name, password, gameUid, referCode)
                                } else {
                                    if (email.isBlank() || password.isBlank()) {
                                        viewModel.setLoginError("Please enter your email and password!")
                                        return@Button
                                    }
                                    viewModel.login(email, password)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("auth_action")
                        ) {
                            Text(
                                text = if (isRegisterMode) "REGISTER" else "LOGIN",
                                color = CharcoalBg,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = {
                isRegisterMode = !isRegisterMode
                viewModel.setLoginError(null)
            }) {
                Text(
                    text = if (isRegisterMode) "Already registered? Login Here" else "Join the arena! Register Here",
                    color = NeonGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ============================================
// TAB SCREEN: HOME TABS / BANNER ACTIONS
// ============================================
@Composable
fun HomeScreen(viewModel: EsportsViewModel, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val user by viewModel.currentUser.collectAsStateWithLifecycle()
    val tournaments by viewModel.tournaments.collectAsStateWithLifecycle()
    
    val openMatches = tournaments.filter { it.status == "OPEN" }
    
    val promos by viewModel.promoSliders.collectAsStateWithLifecycle(initialValue = emptyList())
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        // Broadcast Banners Layout
        item {
            if (promos.isNotEmpty()) {
                val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { promos.size })
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().height(140.dp).padding(vertical = 4.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) { page ->
                    val promo = promos[page]
                    Card(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp).clickable {
                            if (promo.actionUrl.isNotBlank()) {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(promo.actionUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Invalid link", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CharcoalCard)
                    ) {
                        coil.compose.AsyncImage(
                            model = coil.request.ImageRequest.Builder(context)
                                .data(promo.imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = promo.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                }
            } else {
                // Fallback Banner
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.linearGradient(
                                        listOf(CharcoalCard, NeonOrange.copy(alpha = 0.15f))
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "OFFICIAL BANNER",
                                color = NeonOrange,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Anu Battle Tournament Series Launch!",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Watch daily rewarded videos, gather coin multipliers, and claim free entries into Premium Battle Royales.",
                                color = GrayText,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Fast Action Buttons grid
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                FastGridButton(
                    title = "Tournaments",
                    icon = Icons.Default.SportsEsports,
                    color = NeonGold,
                    onClick = { onNavigate("games") }
                )
                FastGridButton(
                    title = "Gold Store",
                    icon = Icons.Default.ShoppingBag,
                    color = NeonOrange,
                    onClick = { onNavigate("store") }
                )
                FastGridButton(
                    title = "Challenge Tasks",
                    icon = Icons.Default.Task,
                    color = MintGreen,
                    onClick = { onNavigate("rewards") }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // Active Lobby stats heading
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Open Battle Arenas",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { onNavigate("games") }) {
                    Text(text = "See All", color = NeonGold, fontSize = 13.sp)
                }
            }
        }

        if (openMatches.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No open lobbies available at the moment.",
                            color = GrayText,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(openMatches.take(3)) { match ->
                TournamentCard(
                    tournament = match,
                    user = user,
                    cooldown = 0,
                    onWatchAdClick = {},
                    onRegisterClick = { onNavigate("games") }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        item { Spacer(modifier = Modifier.height(30.dp)) }
    }
}

@Composable
fun RowScope.FastGridButton(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .weight(1f)
            .padding(4.dp)
            .height(84.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalCard)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ============================================
// TAB SCREEN: TOURNEY GAMES LOBBY (COMPOSABLE FILTERED GRID)
// ============================================
@Composable
fun GamesScreen(viewModel: EsportsViewModel) {
    val context = LocalContext.current
    val tournaments by viewModel.tournaments.collectAsStateWithLifecycle()
    val cooldowns by viewModel.cooldowns.collectAsStateWithLifecycle()
    val user by viewModel.currentUser.collectAsStateWithLifecycle()
    
    var gameFilter by remember { mutableStateOf("All") }
    var statusFilter by remember { mutableStateOf("All") }

    val games = listOf("All", "BGMI", "Free Fire", "Lone Wolf", "Call of Duty")
    val statuses = listOf("All", "OPEN", "UPCOMING", "LIVE", "COMPLETED")

    val filteredList = tournaments.filter { match ->
        val gMatch = gameFilter == "All" || match.gameType.lowercase().contains(gameFilter.lowercase())
        val sMatch = statusFilter == "All" || match.status == statusFilter
        gMatch && sMatch
    }

    // Modal view for Tournament Registration
    var selectedMatchForRegister by remember { mutableStateOf<TournamentEntity?>(null) }
    var adsWatchedForRegister by remember { mutableStateOf(0) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        // Categories horizontal scrolls
        item {
            Text(
                text = "Select Tournament Game",
                color = GrayText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                games.forEach { game ->
                    val selected = gameFilter == game
                    Card(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clickable { gameFilter = game },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) NeonGold else CharcoalCard
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = game,
                            color = if (selected) CharcoalBg else Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                statuses.forEach { status ->
                    val selected = statusFilter == status
                    Card(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clickable { statusFilter = status },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) NeonOrange else CharcoalCard
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = status,
                            color = if (selected) CharcoalBg else Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (filteredList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No games matched current filter parameters.",
                            color = GrayText,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredList) { match ->
                val remainingCd = cooldowns[match.id] ?: 0
                TournamentCard(
                    tournament = match,
                    user = user,
                    cooldown = remainingCd,
                    onWatchAdClick = {
                        if (remainingCd > 0) {
                            Toast.makeText(context, "Please wait for cooldown timer to complete!", Toast.LENGTH_SHORT).show()
                        } else {
                            val activity = context.findActivity()
                            if (activity != null) {
                                viewModel.showUnityRewardedAd(activity, match.id)
                            } else {
                                viewModel.simulateAdWatch(match.id)
                                Toast.makeText(context, "Rewarded Ad Complete! 5.0 Coins added.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onRegisterClick = {
                        adsWatchedForRegister = 0
                        selectedMatchForRegister = match
                    }
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }

    // Join Tournament dialog modals
    selectedMatchForRegister?.let { match ->
        Dialog(onDismissRequest = { selectedMatchForRegister = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Join Arena Lobby",
                        color = NeonGold,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = match.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (match.adsRequired > 0) {
                        Text(
                            text = "Required Views to Join: ${match.adsRequired}",
                            color = GrayText,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val activeCooldown = cooldowns[match.id] ?: 0
                            Button(
                                onClick = {
                                    if (activeCooldown > 0) {
                                        Toast.makeText(context, "Please wait for cooldown timer to complete!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val activity = context.findActivity()
                                        if (activity != null) {
                                            viewModel.showUnityRewardedAd(activity, match.id) { success ->
                                                if (success) {
                                                    adsWatchedForRegister++
                                                    Toast.makeText(context, "Ad View Tracked! (${adsWatchedForRegister}/${match.adsRequired} completed)", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } else {
                                            viewModel.simulateAdWatch(match.id)
                                            adsWatchedForRegister++
                                            Toast.makeText(context, "Ad View Tracked! (${adsWatchedForRegister}/${match.adsRequired} completed)", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (activeCooldown > 0) Color.LightGray else NeonOrange
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = if (activeCooldown > 0) {
                                        val min = activeCooldown / 60
                                        val sec = activeCooldown % 60
                                        String.format("Next Ad: %02d:%02d", min, sec)
                                    } else "Watch Video"
                                )
                            }
                            Text(
                                text = "$adsWatchedForRegister / ${match.adsRequired}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Text(
                        text = "Entry Fee: Rs.${match.entryFee}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { selectedMatchForRegister = null }) {
                            Text(text = "CANCEL", color = GrayText)
                        }
                        Button(
                            onClick = {
                                viewModel.joinTournament(
                                    tournament = match,
                                    adCountWatched = adsWatchedForRegister,
                                    onSuccess = {
                                        Toast.makeText(context, "Successfully joined tournament lobby!", Toast.LENGTH_LONG).show()
                                        selectedMatchForRegister = null
                                    },
                                    onError = { er ->
                                        Toast.makeText(context, er, Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
                        ) {
                            Text(text = "CONFIRM JOIN", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// ============================================
// WIDGET: INDIVIDUAL TOURNAMENT CARD
// ============================================
@Composable
fun TournamentCard(
    tournament: TournamentEntity,
    user: UserEntity?,
    cooldown: Int,
    onWatchAdClick: () -> Unit,
    onRegisterClick: () -> Unit
) {
    val context = LocalContext.current
    var isPassVisible by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val now = System.currentTimeMillis()
    
    // Check credentials schedule condition: Mode 1 (Always unlocked), Mode 2 (Locked until exactly 10 mins prior)
    val timeToMatch = tournament.scheduleTimeMillis - now
    val isScheduleUnlockReady = timeToMatch <= 600000 // Less than or equal to 10 minutes (600,000ms)

    val isUserJoined = user != null && user.joinedTournaments.contains(tournament.id)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        border = BorderStroke(1.dp, NeonGold.copy(alpha = 0.2f))
    ) {
        Column {
            // Header Image Placeholder or status color
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                if (tournament.bannerUrl.isNotBlank()) {
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(context)
                            .data(tournament.bannerUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Tournament Banner",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Dim overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(
                                colors = listOf(Color.Transparent, CharcoalCard.copy(alpha = 0.9f)),
                                startY = 40f
                            ))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    listOf(NeonOrange.copy(alpha = 0.3f), CharcoalCard)
                                )
                            )
                    )
                }

                Column(
                    modifier = Modifier.fillMaxSize().padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    val badgeColor = when (tournament.status) {
                        "COMPLETED" -> Color.Gray
                        "UPCOMING" -> NeonOrange
                        "LIVE" -> Color.Red
                        else -> MintGreen
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = badgeColor.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = tournament.status,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    Column {
                        Text(
                            text = tournament.title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${tournament.gameType} Tournament • Map: ${tournament.mapType}",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Stats row details
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "PRIZE POOL", color = GrayText, fontSize = 10.sp)
                        Text(
                            text = "Rs.${tournament.prizePool}",
                            color = NeonGold,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "ENTRY FEE", color = GrayText, fontSize = 10.sp)
                        Text(
                            text = if (tournament.entryFee == 0.0) "FREE" else "Rs.${tournament.entryFee}",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "SLOTS FILLED", color = GrayText, fontSize = 10.sp)
                        Text(
                            text = "${tournament.slotsFilled}/${tournament.totalSlots}",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Schedule display
                val sdf = SimpleDateFormat("EE dd MMM - hh:mm a", Locale.getDefault())
                val dateString = sdf.format(Date(tournament.scheduleTimeMillis))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Schedule",
                        tint = GrayText,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Schedule: $dateString",
                        color = GrayText,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Description
                if (tournament.description.isNotBlank()) {
                    Text(
                        text = "Rules & Details: ${tournament.description}",
                        color = GrayText,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Register Actions row
                if (tournament.status == "OPEN" || tournament.status == "UPCOMING") {
                    if (isUserJoined) {
                        // Already joined message
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MintGreen.copy(alpha = 0.1f)),
                            border = BorderStroke(1.dp, MintGreen.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "You have successfully registered for this tournament.",
                                color = MintGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        // Anti-Ban Cooldown Button representation
                        if (tournament.adsRequired > 0) {
                            Button(
                                onClick = onWatchAdClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (cooldown > 0) MaterialTheme.colorScheme.surfaceVariant else NeonOrange.copy(alpha = 0.2f)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                enabled = cooldown == 0,
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("ad_cooldown_btn")
                            ) {
                                if (cooldown > 0) {
                                    val min = cooldown / 60
                                    val sec = cooldown % 60
                                    Text(
                                        text = String.format("Next Ad: %02d:%02d", min, sec),
                                        color = GrayText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Watch",
                                        tint = NeonOrange,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Unlock (Ad Required)",
                                        color = NeonOrange,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Button(
                            onClick = onRegisterClick,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("lobby_register_btn")
                        ) {
                            Text(
                                text = "JOIN NOW",
                                color = CharcoalBg,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                } // closes else
                } // closes if (tournament.status ...)

                // Credentials unlock copy panel
                if ((tournament.status == "OPEN" || tournament.status == "LIVE") && isUserJoined) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CharcoalBg),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Room Credentials",
                                        tint = if (isScheduleUnlockReady) NeonGold else GrayText,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isScheduleUnlockReady) "Room Credentials Unlocked" else "Lobby credentials locked",
                                        color = if (isScheduleUnlockReady) Color.White else GrayText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (isScheduleUnlockReady) {
                                    TextButton(
                                        onClick = { isPassVisible = !isPassVisible },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            text = if (isPassVisible) "HIDE" else "SHOW",
                                            color = NeonGold,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    val diffMinutes = (timeToMatch / 60000)
                                    Text(
                                        text = "Unlocks in ${diffMinutes}m",
                                        color = GrayText,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            if (isScheduleUnlockReady && isPassVisible) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Room ID: ${tournament.roomId}",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(tournament.roomId))
                                            Toast.makeText(context, "Room ID Copied!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy ID",
                                            tint = NeonGold,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Password: ${tournament.roomPassword}",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(tournament.roomPassword))
                                            Toast.makeText(context, "Password Copied!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy Password",
                                            tint = NeonGold,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================
// TAB SCREEN: STORE / BALANCES OPERATIONS / VOUCHERS
// ============================================
@Composable
fun StoreScreen(viewModel: EsportsViewModel) {
    val context = LocalContext.current
    val userState by viewModel.currentUser.collectAsStateWithLifecycle()
    val user = userState ?: return

    val epTitle by viewModel.epTitle.collectAsStateWithLifecycle()
    val epNum by viewModel.epNumber.collectAsStateWithLifecycle()
    val jcTitle by viewModel.jcTitle.collectAsStateWithLifecycle()
    val jcNum by viewModel.jcNumber.collectAsStateWithLifecycle()
    val minWithdraw by viewModel.minWithdraw.collectAsStateWithLifecycle()

    var showDepositDialog by remember { mutableStateOf(false) }
    var depositAmountText by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf("Easypaisa") }
    var senderName by remember { mutableStateOf("") }
    var senderPhone by remember { mutableStateOf("") }
    var screenshotUrl by remember { mutableStateOf("") }
    var uploadingScreenshot by remember { mutableStateOf(false) }

    val screenshotPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            uploadingScreenshot = true
            val tempFile = java.io.File(context.cacheDir, "deposit_proof.jpg")
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                tempFile.outputStream().use { out ->
                    inputStream.copyTo(out)
                }
            }
            viewModel.uploadImageToImgBB(tempFile, onSuccess = { url ->
                screenshotUrl = url
                uploadingScreenshot = false
                Toast.makeText(context, "Screenshot uploaded!", Toast.LENGTH_SHORT).show()
            }, onError = {
                uploadingScreenshot = false
                Toast.makeText(context, "Failed to upload screenshot.", Toast.LENGTH_SHORT).show()
            })
        }
    }

    var showWithdrawDialog by remember { mutableStateOf(false) }
    var withdrawAmountText by remember { mutableStateOf("") }
    var withdrawDetailsText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        item {
            Text(
                text = "Wallet Operations",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Balances summary card
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Pocket Money Balance", color = GrayText, fontSize = 11.sp)
                    Text(
                        text = "Rs.${user.mainWallet}",
                        color = MintGreen,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "Bonus Wallet", color = GrayText, fontSize = 11.sp)
                            Text(
                                text = "Rs.${user.bonusWallet}",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "Winnings (Withdrawable)", color = GrayText, fontSize = 11.sp)
                            Text(
                                text = "Rs.${user.winningWallet}",
                                color = NeonGold,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(
                            onClick = { showDepositDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Deposit")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Deposit Cash")
                        }

                        Button(
                            onClick = { showWithdrawDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = "Withdraw", tint = CharcoalBg)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Withdraw", color = CharcoalBg)
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Buy Coins (Voucher Packages)",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
            )
        }

        // Coins Voucher Packages from specification
        val packages = listOf(
            VoucherPackage("Champion Special", "Huge pro stack of 250 coins with double multipliers", 100.0, 250.0, 60.0),
            VoucherPackage("Coins Pack L", "Clash bundle of 120 coins", 50.0, 120.0, 25.0),
            VoucherPackage("Coins Pack M", "Value pack of 55 coins + bonus credits", 25.0, 55.0, 8.0),
            VoucherPackage("Coins Pack S", "Credit 20 coins for instant play setup", 10.0, 20.0, 2.0)
        )

        items(packages) { pack ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pack.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = pack.description,
                            color = GrayText,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "★ Yields ${pack.coinsYield.toInt()} Coins (+${pack.bonusYield.toInt()} Bonus)",
                            color = NeonGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.buyCoins(pack.title, pack.price, pack.coinsYield, pack.bonusYield)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Rs.${pack.price}",
                            color = CharcoalBg,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    // Deposit dialogue modal
    if (showDepositDialog) {
        Dialog(onDismissRequest = { showDepositDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Deposit Cash Credits",
                        color = NeonGold,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Button(
                            onClick = { selectedMethod = "Easypaisa" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (selectedMethod == "Easypaisa") MintGreen else Color.DarkGray)
                        ) { Text("Easypaisa") }
                        Button(
                            onClick = { selectedMethod = "JazzCash" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (selectedMethod == "JazzCash") MintGreen else Color.DarkGray)
                        ) { Text("JazzCash") }
                    }
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Card(colors = CardDefaults.cardColors(containerColor = CharcoalBg), modifier = Modifier.fillMaxWidth().padding(bottom=10.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Send payment to:", color = GrayText, fontSize = 12.sp)
                            Text(if (selectedMethod == "Easypaisa") epTitle else jcTitle, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(if (selectedMethod == "Easypaisa") epNum else jcNum, color = NeonGold, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedTextField(value = depositAmountText, onValueChange = { depositAmountText = it }, label = { Text("Amount (Rs.)") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, focusedBorderColor = NeonGold), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom=6.dp))
                    OutlinedTextField(value = senderName, onValueChange = { senderName = it }, label = { Text("Sender Name") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, focusedBorderColor = NeonGold), singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom=6.dp))
                    OutlinedTextField(value = senderPhone, onValueChange = { senderPhone = it }, label = { Text("Sender Phone/Account") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, focusedBorderColor = NeonGold), singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom=12.dp))

                    Button(
                        onClick = { screenshotPickerLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uploadingScreenshot) "UPLOADING..." else if (screenshotUrl.isNotEmpty()) "SCREENSHOT UPLOADED" else "UPLOAD SCREENSHOT", color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { showDepositDialog = false }) {
                            Text("CANCEL", color = GrayText)
                        }
                        Button(
                            onClick = {
                                val amt = depositAmountText.toDoubleOrNull() ?: 0.0
                                if (amt > 0.0 && senderName.isNotBlank() && senderPhone.isNotBlank() && screenshotUrl.isNotBlank()) {
                                    viewModel.requestDeposit(amt, screenshotUrl, selectedMethod, senderPhone, senderName)
                                    showDepositDialog = false
                                    depositAmountText = ""
                                    senderName = ""
                                    senderPhone = ""
                                    screenshotUrl = ""
                                } else {
                                    Toast.makeText(context, "Please fill all fields and upload screenshot", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !uploadingScreenshot && screenshotUrl.isNotBlank() && depositAmountText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
                        ) {
                            Text("SUBMIT REQUEST")
                        }
                    }
                }
            }
        }
    }

    // Withdraw dialogue modal
    if (showWithdrawDialog) {
        Dialog(onDismissRequest = { showWithdrawDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Withdraw Winnings",
                        color = NeonGold,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = withdrawAmountText,
                        onValueChange = { withdrawAmountText = it },
                        label = { Text("Amount (Rs.)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, focusedBorderColor = NeonGold
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = withdrawDetailsText,
                        onValueChange = { withdrawDetailsText = it },
                        label = { Text("UPI Address / Banking Details") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, focusedBorderColor = NeonGold
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { showWithdrawDialog = false }) {
                            Text("CANCEL", color = GrayText)
                        }
                        Button(
                            onClick = {
                                val amt = withdrawAmountText.toDoubleOrNull() ?: 0.0
                                if (amt > 0) {
                                    viewModel.requestWithdraw(amt, withdrawDetailsText) { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                    showWithdrawDialog = false
                                    withdrawAmountText = ""
                                    withdrawDetailsText = ""
                                } else {
                                    Toast.makeText(context, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
                        ) {
                            Text("SUBMIT WITHDRAW")
                        }
                    }
                }
            }
        }
    }
}

data class VoucherPackage(
    val title: String,
    val description: String,
    val price: Double,
    val coinsYield: Double,
    val bonusYield: Double
)

// ============================================
// TAB SCREEN: DYNAMIC TASKS REWARDS / 7-DAY LOGIN CLAIMS
// ============================================
@Composable
fun RewardsScreen(viewModel: EsportsViewModel) {
    val context = LocalContext.current
    val userState by viewModel.currentUser.collectAsStateWithLifecycle()
    val user = userState ?: return
    
    val dailyTasks by viewModel.dailyTasks.collectAsStateWithLifecycle()
    val taskProgress by viewModel.taskProgress.collectAsStateWithLifecycle()
    val claimedDays by viewModel.claimedDays.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
        ) {
        item {
            Text(
                text = "Daily Rewards Arena",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            Text(
                text = "Claim consecutive daily bonuses. Resets if streak lapses.",
                color = GrayText,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Beautiful 7-Day rewards horizontal list
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                val rewardsList = listOf(5.0, 5.0, 5.0, 10.0, 5.0, 5.0, 15.0)
                val now = System.currentTimeMillis()
                var currentDay = if (now - (user?.lastDailyRewardTime ?: 0L) > (2 * 24 * 60 * 60 * 1000L)) 1 else (user?.dailyRewardDay ?: 1)
                
                for (day in 1..7) {
                    val claimed = (day < currentDay) || (day == currentDay && now - (user?.lastDailyRewardTime ?: 0L) < (24 * 60 * 60 * 1000L))
                    val isToday = day == currentDay
                    val enabled = isToday && !claimed
                    
                    Card(
                        modifier = Modifier
                            .width(84.dp)
                            .padding(end = 8.dp)
                            .clickable(enabled = enabled) {
                                viewModel.claimDailyReward { error ->
                                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (claimed) MintGreen.copy(alpha = 0.2f) else if (isToday) NeonGold.copy(alpha=0.1f) else CharcoalCard
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (claimed) MintGreenBorder else if(isToday) NeonGold else Color.DarkGray
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Day $day",
                                color = if (claimed) MintGreen else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            if (claimed) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Claimed",
                                    tint = MintGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "CLAIMED",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Coins Reward",
                                    tint = NeonGold,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "${rewardsList[day - 1].toInt()} Coins",
                                    color = NeonGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = NeonGold.copy(alpha=0.15f)),
                border = BorderStroke(1.dp, NeonGold),
                modifier = Modifier.fillMaxWidth().clickable {
                    val activity = context.findActivity()
                    if (activity != null) {
                        viewModel.showUnityRewardedAd(activity, "rewards_wallet_watch") { success ->
                            if (success) {
                                Toast.makeText(context, "You earned 10 Coins!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, "Failed to locate active rendering window context.", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Watch Ads to Earn Coins", color = NeonGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Watch short video clips to gather coins for Diamond redemption.", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top=4.dp))
                    }
                    Icon(
                        imageVector = Icons.Default.PlayCircleOutline,
                        contentDescription = "Watch AD",
                        tint = NeonGold,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            val diamondPacks = listOf(
                Pair("110 Diamonds", 100),
                Pair("231 Diamonds", 200),
                Pair("583 Diamonds", 500),
                Pair("1188 Diamonds", 1000)
            )

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    text = "Free Fire Diamond Top-Up",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
                Text(
                    text = "Redeem your collected coins into Free Fire Diamonds direct to your Game UID.",
                    color = GrayText,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                // Using nested rows for grid to avoid scroll constraint conflicts
                val chunkedPacks = diamondPacks.chunked(2)
                for (rowPacks in chunkedPacks) {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (pack in rowPacks) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).clickable {
                                    if (user.coins >= pack.second) {
                                        Toast.makeText(context, "Redemption Request Submitted!", Toast.LENGTH_SHORT).show()
                                        viewModel.submitDiamondRedemption(pack.first, pack.second)
                                    } else {
                                        Toast.makeText(context, "Not enough coins!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Diamond,
                                        contentDescription = "Diamonds",
                                        tint = NeonGold,
                                        modifier = Modifier.size(40.dp).padding(bottom = 8.dp)
                                    )
                                    Text(text = pack.first, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = "${pack.second} Coins", color = NeonOrange, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                        if (rowPacks.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    } // end LazyColumn
    } // end Column wrapper
}

// ============================================
// TAB SCREEN: PROFILE HUB / REDEEM CODES
// ============================================
@Composable
fun ProfileScreen(viewModel: EsportsViewModel, onLogout: () -> Unit) {
    val context = LocalContext.current
    val userState by viewModel.currentUser.collectAsStateWithLifecycle()
    val user = userState ?: return

    var editUsername by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf(user.name) }
    var gameUidInput by remember { mutableStateOf(user.gameUid) }

    var redeemCode by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(
                        brush = Brush.radialGradient(listOf(NeonOrange, NeonGold)),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.name.take(1).uppercase(),
                    color = CharcoalBg,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = user.name,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = user.email,
                color = GrayText,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Player statistics row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharcoalCard, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatColumn(label = "Matches", value = "${user.matchesPlayed}")
                StatColumn(label = "Wins", value = "${user.matchesWon}")
                StatColumn(
                    label = "Earnings",
                    value = "Rs.${String.format("%.1f", user.mainWallet + user.winningWallet)}"
                )
                StatColumn(label = "Referrals", value = "0")
            }
        }

        item {
            Text(
                text = "Personal Customizations",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 12.dp)
            )
        }

        // Custom action tiles
        item {
            ProfileTile(
                title = "Edit Profile Details",
                subtitle = "Change account display handles with custom avatars",
                icon = Icons.Default.Edit,
                onClick = { editUsername = true }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ProfileTile(
                title = "My Match History",
                subtitle = "View details about your finished lobby scores",
                icon = Icons.Default.History,
                onClick = {
                    Toast.makeText(context, "No match history logs available.", Toast.LENGTH_SHORT).show()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ProfileTile(
                title = "Refer & Claim Rewards",
                subtitle = "Check unique code options and redeem codes",
                icon = Icons.Default.Share,
                onClick = {
                    Toast.makeText(context, "Your referral code is: ${user.name.uppercase()}_77", Toast.LENGTH_LONG).show()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ProfileTile(
                title = "Contact Anu Battle Support",
                subtitle = "Lobby disputes and instant deposits helper details",
                icon = Icons.Default.Phone,
                onClick = {
                    Toast.makeText(context, "Support Hotline: +91 9988776655. Email: assistance@anubattle.com", Toast.LENGTH_LONG).show()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ProfileTile(
                title = "Logout from Arena",
                subtitle = "Clear auto registration states",
                icon = Icons.Default.Logout,
                onClick = onLogout,
                iconColor = Color.Red
            )
            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // Edit username dialog modal
    if (editUsername) {
        Dialog(onDismissRequest = { editUsername = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = CharcoalCard), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Edit Profile Details", color = NeonGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        label = { Text("Display Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, focusedBorderColor = NeonGold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val now = System.currentTimeMillis()
                    val cooldownMillis = 30L * 24L * 60L * 60L * 1000L // 30 days
                    val canChangeUid = (now - user.lastGameUidChangeTime) > cooldownMillis
                    OutlinedTextField(
                        value = gameUidInput,
                        onValueChange = { gameUidInput = it },
                        label = { Text("Game UID") },
                        enabled = canChangeUid,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, focusedBorderColor = NeonGold
                        ),
                        placeholder = { Text("Enter Free Fire UID") }
                    )
                    if (!canChangeUid) {
                        Text(
                            text = "Game UID can only be changed once every 30 days.",
                            color = Color.Red,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { editUsername = false }) {
                            Text("CANCEL", color = GrayText)
                        }
                        Button(
                            onClick = {
                                if (renameInput.isNotBlank()) {
                                    val dbRef = FirebaseDatabase.getInstance().getReference("users").child(user.emailKey)
                                    dbRef.child("name").setValue(renameInput.trim())
                                    
                                    if (canChangeUid && gameUidInput.trim() != user.gameUid) {
                                        dbRef.child("gameUid").setValue(gameUidInput.trim())
                                        dbRef.child("lastGameUidChangeTime").setValue(System.currentTimeMillis())
                                    }
                                    
                                    editUsername = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
                        ) {
                            Text("SAVE CHANGES")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        Text(text = label, color = GrayText, fontSize = 11.sp)
    }
}

@Composable
fun ProfileTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    iconColor: Color = NeonGold
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalCard)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(text = subtitle, color = GrayText, fontSize = 10.sp)
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "Forward",
                tint = GrayText,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ============================================
// PANEL SCREEN: HIGH-FIDELITY RESPONSIVE BACKOFFICE ADMIN DASHBOARD
// ============================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(viewModel: EsportsViewModel, onBack: () -> Unit) {
    var adminTab by remember { mutableStateOf("Users") }
    val adminTabs = listOf("Users", "Add Tourney", "Task CRUD", "Settings", "Deposit/Withdraw Queue", "Promos")

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(CharcoalCard, CircleShape)
            ) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Backoffice Support Panel",
                color = NeonGold,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Tabs Header Selection Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            adminTabs.forEach { tab ->
                val active = adminTab == tab
                Card(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clickable { adminTab = tab },
                    colors = CardDefaults.cardColors(
                        containerColor = if (active) NeonGold else CharcoalCard
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = tab,
                        color = if (active) CharcoalBg else Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Divider(color = NeonGold.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))

        // Dynamic Subscreen Selector Panels
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        ) {
            when (adminTab) {
                "Users" -> AdminUsersTab(viewModel)
                "Add Tourney" -> AdminTournamentsCreatorTab(viewModel)
                "Task CRUD" -> AdminTaskCRUDTab(viewModel)
                "Settings" -> AdminSettingsTab(viewModel)
                "Deposit/Withdraw Queue" -> AdminTransactionsQueueTab(viewModel)
                "Promos" -> AdminPromosTab(viewModel)
            }
        }
    }
}

@Composable
fun AdminUsersTab(viewModel: EsportsViewModel) {
    val users by viewModel.allUsers.collectAsStateWithLifecycle()
    var searchToken by remember { mutableStateOf("") }
    
    val filteredUsers = users.filter { u ->
        searchToken.isEmpty() || u.name.lowercase().contains(searchToken.lowercase()) || u.email.lowercase().contains(searchToken.lowercase())
    }

    var editingUserWallets by remember { mutableStateOf<UserEntity?>(null) }
    var adjustMainWallet by remember { mutableStateOf("") }
    var adjustBonusWallet by remember { mutableStateOf("") }
    var adjustWinningsWallet by remember { mutableStateOf("") }
    var adjustCoinsWallet by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchToken,
            onValueChange = { searchToken = it },
            label = { Text("Search users name / email details") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, focusedBorderColor = NeonGold
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filteredUsers) { u ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = u.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(text = u.email, color = GrayText, fontSize = 11.sp)
                            }
                            // Ban / Unban Toggle Button
                            Card(
                                onClick = { viewModel.adminBanControlUser(u.emailKey, !u.banned) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (u.banned) Color.Red else MintGreen
                                )
                            ) {
                                Text(
                                    text = if (u.banned) "BANNED" else "ACTIVE",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Pocket: Rs.${u.mainWallet}", color = Color.White, fontSize = 11.sp)
                            Text(text = "Bonus: Rs.${u.bonusWallet}", color = Color.White, fontSize = 11.sp)
                            Text(text = "Winnings: Rs.${u.winningWallet}", color = Color.White, fontSize = 11.sp)
                            Text(text = "Coins: ${u.coins.toInt()}", color = NeonGold, fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                adjustMainWallet = u.mainWallet.toString()
                                adjustBonusWallet = u.bonusWallet.toString()
                                adjustWinningsWallet = u.winningWallet.toString()
                                adjustCoinsWallet = u.coins.toString()
                                editingUserWallets = u
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Adjust Wallets / Coins", color = CharcoalBg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    editingUserWallets?.let { user ->
        Dialog(onDismissRequest = { editingUserWallets = null }) {
            Card(colors = CardDefaults.cardColors(containerColor = CharcoalCard), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Adjust Balances", color = NeonGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(text = user.email, color = GrayText, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(value = adjustMainWallet, onValueChange = { adjustMainWallet = it }, label = { Text("Main Wallet (Rs.)") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White))
                    OutlinedTextField(value = adjustBonusWallet, onValueChange = { adjustBonusWallet = it }, label = { Text("Bonus Wallet (Rs.)") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White))
                    OutlinedTextField(value = adjustWinningsWallet, onValueChange = { adjustWinningsWallet = it }, label = { Text("Winnings Wallet (Rs.)") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White))
                    OutlinedTextField(value = adjustCoinsWallet, onValueChange = { adjustCoinsWallet = it }, label = { Text("Coins Balance") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White))

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { editingUserWallets = null }) {
                            Text("CANCEL", color = GrayText)
                        }
                        Button(
                            onClick = {
                                val m = adjustMainWallet.toDoubleOrNull() ?: 0.0
                                val b = adjustBonusWallet.toDoubleOrNull() ?: 0.0
                                val w = adjustWinningsWallet.toDoubleOrNull() ?: 0.0
                                val c = adjustCoinsWallet.toDoubleOrNull() ?: 0.0
                                viewModel.adminAdjustUserWallets(user.emailKey, m, b, w, c)
                                editingUserWallets = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
                        ) {
                            Text("APPLY CREDITS")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminTournamentsCreatorTab(viewModel: EsportsViewModel) {
    val tournaments by viewModel.tournaments.collectAsStateWithLifecycle()
    
    var showDialog by remember { mutableStateOf(false) }
    var editingTournamentId by remember { mutableStateOf<String?>(null) }
    
    var title by remember { mutableStateOf("") }
    var isFree by remember { mutableStateOf(false) }
    var mapType by remember { mutableStateOf("Bermuda") }
    var entryFee by remember { mutableStateOf("") }
    var prizePool by remember { mutableStateOf("") }
    var totalSlots by remember { mutableStateOf("100") }
    var adsRequired by remember { mutableStateOf("3") }
    var roomId by remember { mutableStateOf("") }
    var roomPassword by remember { mutableStateOf("") }
    var bannerUrl by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var uploadingImage by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            uploadingImage = true
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val tempFile = java.io.File.createTempFile("promo_", ".jpg", context.cacheDir)
                tempFile.outputStream().use { out ->
                    inputStream?.copyTo(out)
                }
                viewModel.uploadImageToImgBB(tempFile, onSuccess = { url ->
                    bannerUrl = url
                    uploadingImage = false
                    Toast.makeText(context, "Image uploaded!", Toast.LENGTH_SHORT).show()
                }, onError = {
                    uploadingImage = false
                    Toast.makeText(context, "Failed to upload image.", Toast.LENGTH_SHORT).show()
                })
            } catch (e: Exception) {
                uploadingImage = false
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Button(
            onClick = {
                editingTournamentId = null
                title = ""
                isFree = false
                mapType = "Bermuda"
                entryFee = ""
                prizePool = ""
                totalSlots = "100"
                adsRequired = "3"
                roomId = ""
                roomPassword = ""
                bannerUrl = ""
                description = ""
                showDialog = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = NeonOrange),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Text("ADD NEW TOURNAMENT", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Text(text = "Existing Playrooms List", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(tournaments) { t ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(text = t.title, color = NeonGold, fontWeight = FontWeight.Bold)
                            Text(text = if(t.entryFee > 0) "Fee: Rs.${t.entryFee}" else "FREE", color = Color.LightGray, fontSize = 12.sp)
                        }
                        Text(text = "Type: Free Fire | Map: ${t.mapType}", color = Color.Gray, fontSize = 12.sp)
                        Text(text = "Status: ${t.status} | Joined: ${t.slotsFilled}/${t.totalSlots}", color = GrayText, fontSize = 12.sp)

                        Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    editingTournamentId = t.id
                                    title = t.title
                                    isFree = t.entryFee == 0.0
                                    mapType = t.mapType
                                    entryFee = t.entryFee.toString()
                                    prizePool = t.prizePool.toString()
                                    totalSlots = t.totalSlots.toString()
                                    adsRequired = t.adsRequired.toString()
                                    roomId = t.roomId
                                    roomPassword = t.roomPassword
                                    bannerUrl = t.bannerUrl
                                    description = t.description
                                    showDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                modifier = Modifier.weight(1f).height(35.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("EDIT", color = Color.White, fontSize = 11.sp)
                            }
                            Button(
                                onClick = { viewModel.adminDeleteTournament(t.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.7f)),
                                modifier = Modifier.weight(1f).height(35.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("DELETE", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalBg),
                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        text = if (editingTournamentId == null) "Create Playroom Lobby" else "Edit Tournament Match",
                        color = NeonGold,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Note: Banner image should be 1200x500 for best display.", color = Color.Gray, fontSize = 10.sp)
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uploadingImage) "Uploading..." else if (bannerUrl.isNotEmpty()) "Change Banner Image" else "Upload Banner Image", color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Match Title") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Rules & Description") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 5
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = isFree,
                            onCheckedChange = { isFree = it },
                            colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = NeonGold)
                        )
                        Text("Is this a Free Tournament?", color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = mapType,
                        onValueChange = { mapType = it },
                        label = { Text("Map Location (e.g. Bermuda)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!isFree) {
                        OutlinedTextField(
                            value = entryFee,
                            onValueChange = { entryFee = it },
                            label = { Text("Entry Fee (Rs.)") },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = prizePool,
                            onValueChange = { prizePool = it },
                            label = { Text("Prize Pool (Rs.)") },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = adsRequired,
                            onValueChange = { adsRequired = it },
                            label = { Text("Ads Required Watch Target") },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = totalSlots,
                        onValueChange = { totalSlots = it },
                        label = { Text("Total Slots") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = roomId,
                        onValueChange = { roomId = it },
                        label = { Text("Lobby Pass ID (Will unlock 10 mins before)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = roomPassword,
                        onValueChange = { roomPassword = it },
                        label = { Text("Lobby Pass Secret") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CANCEL", color = Color.White)
                        }
                        Button(
                            onClick = {
                                if (title.isBlank()) {
                                    Toast.makeText(context, "Title is required!", Toast.LENGTH_SHORT).show()
                                } else {
                                    val finalEntryFee = if (isFree) 0.0 else (entryFee.toDoubleOrNull() ?: 0.0)
                                    val finalPrizePool = if (isFree) 0.0 else (prizePool.toDoubleOrNull() ?: 0.0)
                                    val finalAdsRequired = if (isFree) (adsRequired.toIntOrNull() ?: 3) else 0

                                    val original = tournaments.find { it.id == editingTournamentId }
                                    val entity = TournamentEntity(
                                        id = editingTournamentId ?: "match_${System.currentTimeMillis().toString().takeLast(6)}",
                                        title = title,
                                        gameType = "Free Fire",
                                        mapType = mapType,
                                        entryFee = finalEntryFee,
                                        prizePool = finalPrizePool,
                                        slotsFilled = original?.slotsFilled ?: 0,
                                        totalSlots = totalSlots.toIntOrNull() ?: 100,
                                        adsRequired = finalAdsRequired,
                                        scheduleTimeMillis = original?.scheduleTimeMillis ?: (System.currentTimeMillis() + 1800000), // Note: Hardcoded time logic requires a datetime picker in real app
                                        status = original?.status ?: "OPEN",
                                        roomId = roomId,
                                        roomPassword = roomPassword,
                                        bannerUrl = bannerUrl,
                                        description = description
                                    )
                                    viewModel.adminCreateTournament(entity)
                                    Toast.makeText(context, "Tournament saved!", Toast.LENGTH_SHORT).show()
                                    showDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("SAVE", color = CharcoalBg, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminTaskCRUDTab(viewModel: EsportsViewModel) {
    val tasks by viewModel.dailyTasks.collectAsStateWithLifecycle()
    
    var editingTaskId by remember { mutableStateOf<String?>(null) }
    
    var title by remember { mutableStateOf("") }
    var taskType by remember { mutableStateOf("WATCH_AD") }
    var targetValue by remember { mutableStateOf("2") }
    var coinReward by remember { mutableStateOf("10") }

    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (editingTaskId == null) "Add Daily Challenge Template" else "Edit Daily Challenge Template",
                    color = NeonGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Challenge Title text") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                OutlinedTextField(
                    value = taskType,
                    onValueChange = { taskType = it },
                    label = { Text("Task Type (e.g. WATCH_AD, PLAY_MINS, REFER)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                OutlinedTextField(
                    value = targetValue,
                    onValueChange = { targetValue = it },
                    label = { Text("Target progress cap") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                OutlinedTextField(
                    value = coinReward,
                    onValueChange = { coinReward = it },
                    label = { Text("Coins reward yield") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(14.dp))

                if (editingTaskId == null) {
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                Toast.makeText(context, "Title was left empty!", Toast.LENGTH_SHORT).show()
                            } else {
                                val entity = DailyTaskEntity(
                                    id = "task_${System.currentTimeMillis().toString().takeLast(4)}",
                                    title = title,
                                    taskType = taskType,
                                    targetValue = targetValue.toIntOrNull() ?: 1,
                                    coinReward = coinReward.toDoubleOrNull() ?: 5.0
                                )
                                viewModel.adminCreateTaskTemplate(entity)
                                Toast.makeText(context, "Challenge added!", Toast.LENGTH_SHORT).show()
                                title = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "ADD CHALLENGE", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (title.isBlank()) {
                                    Toast.makeText(context, "Title is empty!", Toast.LENGTH_SHORT).show()
                                } else {
                                    val entity = DailyTaskEntity(
                                        id = editingTaskId!!,
                                        title = title,
                                        taskType = taskType,
                                        targetValue = targetValue.toIntOrNull() ?: 1,
                                        coinReward = coinReward.toDoubleOrNull() ?: 5.0
                                    )
                                    viewModel.adminCreateTaskTemplate(entity)
                                    Toast.makeText(context, "Challenge modified successfully!", Toast.LENGTH_SHORT).show()
                                    
                                    editingTaskId = null
                                    title = ""
                                    taskType = "WATCH_AD"
                                    targetValue = "2"
                                    coinReward = "10"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "SAVE CHANGES", color = CharcoalBg, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                editingTaskId = null
                                title = ""
                                taskType = "WATCH_AD"
                                targetValue = "2"
                                coinReward = "10"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "CANCEL", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(tasks) { task ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = task.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(text = "Type: ${task.taskType} | Target: ${task.targetValue} | Reward: ${task.coinReward.toInt()} Coins", color = GrayText, fontSize = 11.sp)
                        }

                        Row {
                            IconButton(
                                onClick = {
                                    editingTaskId = task.id
                                    title = task.title
                                    taskType = task.taskType
                                    targetValue = task.targetValue.toString()
                                    coinReward = task.coinReward.toInt().toString()
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = NeonGold)
                            }
                            IconButton(
                                onClick = { viewModel.adminDeleteTaskTemplate(task.id) }
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminSettingsTab(viewModel: EsportsViewModel) {
    val gameId by viewModel.unityGameId.collectAsStateWithLifecycle()
    val rewardedId by viewModel.unityRewardedId.collectAsStateWithLifecycle()
    val interstitialId by viewModel.unityInterstitialId.collectAsStateWithLifecycle()
    val epTitle by viewModel.epTitle.collectAsStateWithLifecycle()
    val epNum by viewModel.epNumber.collectAsStateWithLifecycle()
    val jcTitle by viewModel.jcTitle.collectAsStateWithLifecycle()
    val jcNum by viewModel.jcNumber.collectAsStateWithLifecycle()
    val minWithdraw by viewModel.minWithdraw.collectAsStateWithLifecycle()

    var inputGameId by remember { mutableStateOf(gameId) }
    var inputRewardedId by remember { mutableStateOf(rewardedId) }
    var inputInterstitialId by remember { mutableStateOf(interstitialId) }
    var inputEpTitle by remember { mutableStateOf(epTitle) }
    var inputEpNum by remember { mutableStateOf(epNum) }
    var inputJcTitle by remember { mutableStateOf(jcTitle) }
    var inputJcNum by remember { mutableStateOf(jcNum) }
    var inputMinWithdraw by remember { mutableStateOf(minWithdraw) }

    LaunchedEffect(gameId, rewardedId, interstitialId, epTitle, epNum, jcTitle, jcNum, minWithdraw) {
        inputGameId = gameId
        inputRewardedId = rewardedId
        inputInterstitialId = interstitialId
        inputEpTitle = epTitle
        inputEpNum = epNum
        inputJcTitle = jcTitle
        inputJcNum = jcNum
        inputMinWithdraw = minWithdraw
    }

    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(text = "Manage Unity Ads Placer Settings", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(value = inputGameId, onValueChange = { inputGameId = it }, label = { Text("Unity Game ID") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = inputRewardedId, onValueChange = { inputRewardedId = it }, label = { Text("Rewarded Placement ID") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = inputInterstitialId, onValueChange = { inputInterstitialId = it }, label = { Text("Interstitial Placement ID") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Payment Administration", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(value = inputEpTitle, onValueChange = { inputEpTitle = it }, label = { Text("Easypaisa Account Title") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = inputEpNum, onValueChange = { inputEpNum = it }, label = { Text("Easypaisa Account Number") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = inputJcTitle, onValueChange = { inputJcTitle = it }, label = { Text("JazzCash Account Title") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = inputJcNum, onValueChange = { inputJcNum = it }, label = { Text("JazzCash Account Number") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = inputMinWithdraw, onValueChange = { inputMinWithdraw = it }, label = { Text("Minimum Withdrawal Amount") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                viewModel.adminSetPlatformSettings(inputGameId, inputRewardedId, inputInterstitialId, inputEpNum, inputEpTitle, inputJcNum, inputJcTitle, inputMinWithdraw)
                Toast.makeText(context, "Settings Updated successfully!", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "UPDATE PLACEMENTS", color = CharcoalBg, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AdminTransactionsQueueTab(viewModel: EsportsViewModel) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val pendingList = transactions.filter { it.status == "PENDING" }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Pending Cash Approvals Queue",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (pendingList.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Approval queue is currently empty.", color = GrayText, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn {
                items(pendingList) { tx ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(text = "User emailKey: ${tx.emailKey}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(text = "Type: ${tx.type} | Amount: Rs.${String.format("%.1f", Math.abs(tx.amount))}", color = NeonGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                val sdf = SimpleDateFormat("HH:mm a", Locale.getDefault())
                                Text(text = sdf.format(Date(tx.timestamp)), color = GrayText, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "Details: ${tx.details}", color = GrayText, fontSize = 11.sp)
                            
                            if (tx.screenshotUrl.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Payment Proof Screenshot (Deposit):", color = NeonGold, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                coil.compose.AsyncImage(
                                    model = tx.screenshotUrl,
                                    contentDescription = "Deposit Screenshot",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = { viewModel.adminRejectTransaction(tx) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(text = "REJECT", color = Color.White)
                                }

                                Button(
                                    onClick = { viewModel.adminApproveTransaction(tx) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(text = "APPROVE", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminPromosTab(viewModel: EsportsViewModel) {
    val promos by viewModel.promoSliders.collectAsStateWithLifecycle(initialValue = emptyList())
    var promoTitle by remember { mutableStateOf("") }
    var actionUrl by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var uploadingImage by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            uploadingImage = true
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val tempFile = java.io.File.createTempFile("promo_", ".jpg", context.cacheDir)
                tempFile.outputStream().use { out ->
                    inputStream?.copyTo(out)
                }
                viewModel.uploadImageToImgBB(tempFile, onSuccess = { url ->
                    imageUrl = url
                    uploadingImage = false
                    Toast.makeText(context, "Image uploaded!", Toast.LENGTH_SHORT).show()
                }, onError = {
                    uploadingImage = false
                    Toast.makeText(context, "Failed to upload image.", Toast.LENGTH_SHORT).show()
                })
            } catch (e: Exception) {
                uploadingImage = false
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Add Promo Image", color = NeonGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = promoTitle,
            onValueChange = { promoTitle = it },
            label = { Text("Promo Title") },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = actionUrl,
            onValueChange = { actionUrl = it },
            label = { Text("Action URL (Optional link)") },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { imagePickerLauncher.launch("image/*") },
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (uploadingImage) "Uploading..." else if (imageUrl.isNotEmpty()) "Change Image" else "Upload Promo Image", color = Color.White)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Selected image URL: $imageUrl", color = Color.Gray, fontSize = 10.sp)

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (imageUrl.isBlank()) {
                    Toast.makeText(context, "Please upload an image first!", Toast.LENGTH_SHORT).show()
                } else {
                    val id = "promo_${System.currentTimeMillis()}"
                    val entity = com.example.data.PromoSliderEntity(
                        id = id,
                        imageUrl = imageUrl,
                        title = promoTitle.ifBlank { "Promo" },
                        actionUrl = actionUrl
                    )
                    viewModel.savePromoSlider(entity)
                    Toast.makeText(context, "Promo added successfully!", Toast.LENGTH_SHORT).show()
                    promoTitle = ""
                    actionUrl = ""
                    imageUrl = ""
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("SAVE PROMO", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Active Promos", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(promos) { p ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(p.title, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { viewModel.deletePromoSlider(p.id) }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Promo", tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}