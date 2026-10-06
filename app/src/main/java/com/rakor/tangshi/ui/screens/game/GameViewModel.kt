package com.rakor.tangshi.ui.screens.game

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rakor.tangshi.data.model.Poem
import com.rakor.tangshi.data.repo.PoemRepository
import com.rakor.tangshi.data.repo.ProgressRepository
import com.rakor.tangshi.data.repo.ProgressSummary
import com.rakor.tangshi.di.AppGraph
import com.rakor.tangshi.game.QuizGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

/** 一轮题目（一轮内模式同构：A 全为填空、B 全为听音） */
sealed interface Question {
    data class Fill(val q: QuizGenerator.FillBlank) : Question
    data class Listen(val q: QuizGenerator.Listen) : Question
}

/** 一轮的状态阶段：开局生成中 / 题池不足 / 答题中 / 已完成（等结算导航） */
enum class GamePhase { IDLE, NEED_MORE, PLAYING, DONE }

/**
 * 一轮答题状态（计划中的 GameState 字段 + phase/picked/finalStreak 三个实现性补充：
 * phase 区分"还没生成"与"池不足"，picked 记录本轮作答选项供答对/答错反馈上色，
 * finalStreak 在置 DONE 时新鲜读取（markTodayStudyLog 落账后）——结算页鼓励语用它，
 * 避免 stats 流快照停留在落账前（当日首轮会显示昨天的连续天数）。
 */
data class GameState(
    val phase: GamePhase = GamePhase.IDLE,
    val mode: String = GameViewModel.MODE_FILL,
    val questions: List<Question> = emptyList(),
    val index: Int = 0,
    val correct: Int = 0,
    val streakInRound: Int = 0,
    val answered: Boolean = false,
    val answerWasCorrect: Boolean = false,
    val picked: String? = null,
    val finalStreak: Int = 0
)

/**
 * 背诗小游戏数据（GameSetupScreen 取计数/题池口径，GameQuestionScreen 驱动一轮状态机；
 * 两屏各持独立实例——VM 属于各自导航条目，互不共享状态）。
 *
 * 出题：start() 按 scope 取题池（≥ MIN_POOL 才开局），QuizGenerator 以非固定种子生成
 * ROUND_LEN 题；answer*() 判定后进入 800ms 反馈期（答对变绿/答错标红），随后推进下一题
 * 或置 DONE；完成时按规格 9 调 markTodayStudyLog()（只记当日 study_log，不动诗进度）。
 */
class GameViewModel(private val poems: PoemRepository, private val progress: ProgressRepository) : ViewModel() {

    val all: List<Poem> = poems.loadAll()

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    /** 题池口径：today=今日所学 / learned=已学全部（有进度行）/ all=全部（保持诗库排序） */
    fun poolOf(scope: String, summaries: List<ProgressSummary>, todayIds: Set<String>): List<Poem> =
        when (scope) {
            SCOPE_TODAY -> all.filter { it.id in todayIds }
            SCOPE_LEARNED -> all.filter { p -> summaries.any { it.poemId == p.id } }
            else -> all
        }

    /** 题池是否够开局（setup 屏拦截用；重取最新进度而非依赖已收集快照） */
    suspend fun hasEnough(scope: String): Boolean =
        poolOf(scope, progress.observeAllSummaries().first(), progress.todayPoemIds()).size >= MIN_POOL

    /** 开局：池不足置 NEED_MORE；成功则一次生成整轮题目 */
    suspend fun start(mode: String, scope: String) {
        if (_state.value.phase != GamePhase.IDLE) return
        val pool = poolOf(scope, progress.observeAllSummaries().first(), progress.todayPoemIds())
        if (pool.size < MIN_POOL) {
            _state.value = GameState(phase = GamePhase.NEED_MORE)
            return
        }
        val rng = Random(System.nanoTime())   // 非固定种子：每轮题目不同（生成器的正确性由单元测试按固定种子保障）
        val questions = List(ROUND_LEN) {
            if (mode == MODE_FILL) Question.Fill(QuizGenerator.fillBlank(pool, rng))
            else Question.Listen(QuizGenerator.listen(pool, rng))
        }
        _state.value = GameState(phase = GamePhase.PLAYING, mode = mode, questions = questions)
    }

    /** 填空作答（answered/非答题期忽略，防止反馈期内重复判定） */
    fun answerFill(option: Char) {
        val s = _state.value
        if (s.phase != GamePhase.PLAYING || s.answered) return
        val q = s.questions.getOrNull(s.index) as? Question.Fill ?: return
        settle(q.q.answer == option, option.toString())
    }

    /** 听音作答（按诗 id 判定） */
    fun answerListen(poemId: String) {
        val s = _state.value
        if (s.phase != GamePhase.PLAYING || s.answered) return
        val q = s.questions.getOrNull(s.index) as? Question.Listen ?: return
        settle(q.q.poem.id == poemId, poemId)
    }

    /** 判定 + 记账，FEEDBACK_MS 后自动推进（viewModelScope 内 delay，退出本屏即取消） */
    private fun settle(correct: Boolean, picked: String) {
        val s = _state.value
        _state.value = s.copy(
            answered = true,
            answerWasCorrect = correct,
            picked = picked,
            correct = s.correct + if (correct) 1 else 0,
            streakInRound = if (correct) s.streakInRound + 1 else 0
        )
        viewModelScope.launch {
            delay(FEEDBACK_MS)
            val cur = _state.value
            val next = cur.index + 1
            if (next >= cur.questions.size) {
                // 规格 9：一轮完成计入当日 study_log；streak 落账后新鲜读取随 DONE 带给结算页
                // （observeStats 流快照可能停留在落账前，当日首轮会把"连续 1 天"显示成 0）
                progress.markTodayStudyLog()
                val streak = progress.observeStats().first().streakDays
                _state.value = cur.copy(phase = GamePhase.DONE, finalStreak = streak)
            } else {
                _state.value = cur.copy(index = next, answered = false, answerWasCorrect = false, picked = null)
            }
        }
    }

    companion object {
        const val MODE_FILL = "A"       // 填空补诗
        const val MODE_LISTEN = "B"     // 听音辨诗
        const val SCOPE_TODAY = "today"
        const val SCOPE_LEARNED = "learned"
        const val SCOPE_ALL = "all"
        const val ROUND_LEN = 10        // 10 题一轮
        const val MIN_POOL = 5          // 题池下限（规格 9：不足提示"先去学几首诗再来吧！"）
        const val FEEDBACK_MS = 800L    // 答对/答错反馈时长
    }
}

/** 手写 factory（不引 DI 框架）；顶层单例，避免每次重组重建 factory（照抄 HomeScreen 模式） */
private val gameViewModelFactory = viewModelFactory {
    initializer { GameViewModel(AppGraph.instance.poems, AppGraph.instance.progress) }
}

@Composable
fun gameViewModel(): GameViewModel = viewModel(factory = gameViewModelFactory)
