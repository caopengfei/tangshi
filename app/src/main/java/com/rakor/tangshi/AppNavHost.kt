package com.rakor.tangshi

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rakor.tangshi.ui.screens.detail.DetailScreen
import com.rakor.tangshi.ui.screens.game.GameQuestionScreen
import com.rakor.tangshi.ui.screens.game.GameResultScreen
import com.rakor.tangshi.ui.screens.game.GameSetupScreen
import com.rakor.tangshi.ui.screens.game.GameViewModel
import com.rakor.tangshi.ui.screens.home.HomeScreen
import com.rakor.tangshi.ui.screens.library.LibraryScreen
import com.rakor.tangshi.ui.screens.search.SearchScreen
import com.rakor.tangshi.ui.screens.settings.SettingsScreen

/** 路由表（规格第 6 节）：poem/game/gameResult 带参数，Routes.poem(id)/game(mode,scope)/
 *  gameResult(correct,streak,mode,scope) 生成实际地址（query 参数在导航层显式声明默认值） */
object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val GAME_SETUP = "gameSetup"
    const val SETTINGS = "settings"
    const val POEM = "poem/{id}"
    const val GAME = "game/{mode}?scope={scope}"
    const val GAME_RESULT = "gameResult?correct={correct}&streak={streak}&mode={mode}&scope={scope}"

    fun poem(id: String) = "poem/$id"
    fun game(mode: String, scope: String) = "game/$mode?scope=$scope"
    fun gameResult(correct: Int, streak: Int, mode: String, scope: String) =
        "gameResult?correct=$correct&streak=$streak&mode=$mode&scope=$scope"
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    // 菜单键 → 家长设置（规格第 6 节）：各屏不消费 MENU，冒泡到根部统一处理；
    // 已在设置页时忽略（launchSingleTop 防重复入栈）。对话框是独立窗口收不到，天然豁免。
    Box(Modifier.fillMaxSize().onKeyEvent { e ->
        if (e.key == Key.Menu &&
            e.type == KeyEventType.KeyUp &&
            nav.currentDestination?.route != Routes.SETTINGS
        ) {
            nav.navigate(Routes.SETTINGS) { launchSingleTop = true }
            true
        } else false
    }) {
        NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onPoem = { nav.navigate(Routes.poem(it)) },
                onSearch = { nav.navigate(Routes.SEARCH) },
                onLibrary = { nav.navigate(Routes.LIBRARY) },
                onGames = { nav.navigate(Routes.GAME_SETUP) },
                onSettings = { nav.navigate(Routes.SETTINGS) }
            )
        }
        composable(
            Routes.POEM,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            DetailScreen(
                poemId = entry.arguments?.getString("id") ?: "",
                onBack = { nav.popBackStack() }
            )
        }
        composable(Routes.SEARCH) { SearchScreen(onPoem = { nav.navigate(Routes.poem(it)) }) }
        composable(Routes.LIBRARY) { LibraryScreen(onPoem = { nav.navigate(Routes.poem(it)) }) }
        composable(Routes.GAME_SETUP) {
            GameSetupScreen(
                onBack = { nav.popBackStack() },
                onStart = { mode, scope -> nav.navigate(Routes.game(mode, scope)) }
            )
        }
        composable(
            Routes.GAME,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("scope") { type = NavType.StringType; defaultValue = GameViewModel.SCOPE_ALL }
            )
        ) { entry ->
            GameQuestionScreen(
                mode = entry.arguments?.getString("mode") ?: GameViewModel.MODE_FILL,
                scope = entry.arguments?.getString("scope") ?: GameViewModel.SCOPE_ALL,
                onBack = { nav.popBackStack() },   // 顶栏 ← 返回 / 后退键 = 放弃本轮回玩法选择页
                onFinished = { correct, streak ->
                    val mode = entry.arguments?.getString("mode") ?: GameViewModel.MODE_FILL
                    val scope = entry.arguments?.getString("scope") ?: GameViewModel.SCOPE_ALL
                    nav.navigate(Routes.gameResult(correct, streak, mode, scope))
                }
            )
        }
        composable(
            Routes.GAME_RESULT,
            arguments = listOf(
                navArgument("correct") { type = NavType.IntType; defaultValue = 0 },
                navArgument("streak") { type = NavType.IntType; defaultValue = 0 },
                navArgument("mode") { type = NavType.StringType; defaultValue = GameViewModel.MODE_FILL },
                navArgument("scope") { type = NavType.StringType; defaultValue = GameViewModel.SCOPE_ALL }
            )
        ) { entry ->
            val mode = entry.arguments?.getString("mode") ?: GameViewModel.MODE_FILL
            val scope = entry.arguments?.getString("scope") ?: GameViewModel.SCOPE_ALL
            GameResultScreen(
                correct = entry.arguments?.getInt("correct") ?: 0,
                streak = entry.arguments?.getInt("streak") ?: 0,
                onAgain = {
                    // 再来一轮：重开同玩法+范围；popUpTo 玩法选择页，弹掉旧题面避免栈膨胀
                    nav.navigate(Routes.game(mode, scope)) { popUpTo(Routes.GAME_SETUP) }
                },
                onHome = { nav.popBackStack(Routes.HOME, false) }
            )
        }
        composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}
