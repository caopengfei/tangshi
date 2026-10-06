package com.rakor.tangshi

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rakor.tangshi.ui.components.CreamBackground
import com.rakor.tangshi.ui.screens.home.HomeScreen
import com.rakor.tangshi.ui.theme.T

/** 路由表（规格第 6 节）：poem/game 带参数，Routes.poem(id)/game(mode) 生成实际地址 */
object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val GAME_SETUP = "gameSetup"
    const val SETTINGS = "settings"
    const val POEM = "poem/{id}"
    const val GAME = "game/{mode}"
    const val GAME_RESULT = "gameResult"

    fun poem(id: String) = "poem/$id"
    fun game(mode: String) = "game/$mode"
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
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
        composable(Routes.POEM) { Placeholder("详情学习页") }       // Task 7
        composable(Routes.SEARCH) { Placeholder("搜索唐诗") }       // Task 9
        composable(Routes.LIBRARY) { Placeholder("全部唐诗") }      // Task 8
        composable(Routes.GAME_SETUP) { Placeholder("背诗小游戏") }  // Task 10
        composable(Routes.GAME) { Placeholder("游戏题面") }         // Task 10
        composable(Routes.GAME_RESULT) { Placeholder("游戏结算") }   // Task 10
        composable(Routes.SETTINGS) { Placeholder("家长设置") }     // Task 11
    }
}

/** 未实现屏幕的占位（Task 7-11 逐个替换） */
@Composable
private fun Placeholder(title: String) {
    CreamBackground {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(title, color = T.TextTitle)
        }
    }
}
